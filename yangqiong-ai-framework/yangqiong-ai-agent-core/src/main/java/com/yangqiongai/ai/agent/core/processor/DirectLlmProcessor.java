/*
 * Copyright (C) 2026 yangqiong
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, version 3 of the License
 * only ("AGPL-3.0-only") and not any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.yangqiongai.ai.agent.core.processor;

import com.yangqiongai.ai.agent.core.AgentResultConverter;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.executor.DirectLlmExecutor;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.session.ConversationBridge;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 直接大模型调用
 * @author yangqiong
 */
@Component
public class DirectLlmProcessor extends AbstractAgentProcessor {

    @Autowired
    private DirectLlmExecutor directLlmExecutor;

    @Override
    public String getAgentCode() {
        return DIRECT_LLM;
    }

    @Override
    protected String getAgentName() {
        return "directLlm";
    }

    @Override
    protected int getMaxIterations() {
        return 1;
    }

    @Override
    protected boolean disableLongTermMemoryTools() {
        return true;
    }

    /**
     * 构建最小上下文，不创建HarnessAgent，不装配工具/技能
     * @param request
     * @return
     */
    @Override
    public AgentContext createAgentContext(AgentRequest request) {
        String runId = generateRunId();
        String systemPrompt = resolveSystemPrompt(request);
        List<AgentMessage> inputs = buildInputMessages(request);
        enrichInputsWithKnowledge(inputs, request);
        AgentResultConverter resultHandler = createResultHandler(request, runId);

        AgentContext context = new AgentContext(request);
        context.setRunId(runId);
        context.setAttribute(AgentContext.CTX_INPUTS, inputs);
        context.setAttribute(AgentContext.CTX_RESULT_CONVERTER, resultHandler);
        context.setAttribute(AgentContext.CTX_SYSTEM_PROMPT, systemPrompt);

        // 会话配置（供 ConversationBridge 使用）
        Map<String, Object> sessionConfig = new HashMap<>();
        sessionConfig.put(ConversationBridge.ATTR_SESSION_ENABLED, true);
        sessionConfig.put(ConversationBridge.ATTR_SESSION_NAMESPACE, getSessionNamespace());
        Map<String, Object> requestSessionConfig = request == null ? null : request.getSessionConfig();
        if (requestSessionConfig != null) {
            sessionConfig.putAll(requestSessionConfig);
        }
        sessionConfig.forEach(context::setAttribute);

        // 规划模式标记（与现有路径保持一致）
        if (request != null && request.isPlanModeEnabled()) {
            context.setAttribute(AgentContext.CTX_PLAN_MODE_ENABLED, true);
        }

        return context;
    }

    @Override
    public AgentResult process(AgentContext context) {
        return directLlmExecutor.execute(context);
    }

    @Override
    public Flux<StreamEvent> stream(AgentContext context) {
        return directLlmExecutor.streamExecute(context);
    }

    /**
     * 将InputBlock转换为Msg
     * @param request
     * @return
     */
    @Override
    protected List<AgentMessage> buildInputMessages(AgentRequest request) {
        List<InputBlock> inputBlocks = request == null ? List.of() : request.getInput();
        List<AgentContentBlock> userInput;
        if (inputBlocks == null || inputBlocks.isEmpty()) {
            userInput = List.of(AgentTextBlock.builder().text("").build());
        } else {
            userInput = ContentBlockConverter.fromInputBlocks(inputBlocks);
        }
        List<AgentMessage> inputs = new ArrayList<>();
        inputs.add(AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(userInput)
                .build());
        return inputs;
    }
}
