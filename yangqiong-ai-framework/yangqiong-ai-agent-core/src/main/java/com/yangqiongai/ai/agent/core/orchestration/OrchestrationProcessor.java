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
package com.yangqiongai.ai.agent.core.orchestration;

import com.yangqiongai.ai.agent.core.processor.AbstractAgentProcessor;
import com.yangqiongai.ai.agent.core.AgentResultConverter;
import com.yangqiongai.ai.agent.core.bootstrap.AgentBootstrap;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig;
import com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.core.session.ConversationBridge;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.*;

/**
 * 多代理编排任务处理器（通用入口）
 * <p>
 * 作为多代理编排的通用入口，通过请求体参数指定编排模式和子代理配置。
 * 适用于不确定该用哪个专业处理器的场景。
 * </p>
 *
 * <p>对于专业场景，建议直接继承 {@link AbstractAgentProcessor} 并使用
 * {@link OrchestrationSupport} 提供的编排能力，而非通过此通用入口。</p>
 *
 * <p>请求体参数示例：</p>
 * <pre>
 * {
 *   "input": "分析这段文本",
 *   "agentCode": "orchestration",
 *   "body": {
 *     "orchestrationMode": "sequential",
 *     "subagents": [
 *       { "name": "extractor", "description": "信息提取专家" },
 *       { "name": "reviewer", "description": "内容审核专家" }
 *     ]
 *   }
 * }
 * </pre>
 *
 * <p>动态编排参数示例：</p>
 * <pre>
 * {
 *   "input": "分析这段文本",
 *   "agentCode": "orchestration",
 *   "body": {
 *     "autoGenerate": true,
 *     "maxSubagents": 5
 *   }
 * }
 * </pre>
 *
 * @author yangqiong
 * @see OrchestrationSupport
 * @see OrchestrationDecisionService
 * @see SubagentSpecGeneratorService
 * @see AbstractAgentProcessor#orchestrateSequential(String, List, AgentRequest)
 * @see AbstractAgentProcessor#orchestrateParallel(String, List, AgentRequest)
 * @see AbstractAgentProcessor#orchestrateDelegate(AgentContext, List)
 */
@Slf4j
public class OrchestrationProcessor extends AbstractAgentProcessor {

    private static final String AGENT_CODE = "orchestration";

    private static final String ATTR_ORCHESTRATION_MODE = "_orchestrationMode";
    private static final String ATTR_SUBAGENT_DECLARATIONS = "_subagentDeclarations";

    @Autowired
    private OrchestrationDecisionService orchestrationDecisionService;

    @Autowired
    private SubagentSpecGeneratorService subagentSpecGeneratorService;

    @Override
    public String getAgentCode() {
        return AGENT_CODE;
    }

    @Override
    protected String getAgentName() {
        return "OrchestrationAgent";
    }

    @Override
    protected String getDefaultSystemPrompt() {
        return """
                你是一个多代理编排协调器。你的职责是根据用户的需求，协调多个专业子代理协作完成任务。

                工作流程：
                1. 分析用户需求，判断需要哪些专业能力
                2. 将任务合理分配给可用的子代理
                3. 汇总各子代理的结果，形成最终回答

                请确保最终回答完整、连贯、有价值。
                默认使用中文回答，除非用户明确要求其他语言。
                """;
    }

    @Override
    protected int getMaxIterations() {
        return 15;
    }

    @Override
    protected List<AgentMessage> buildInputMessages(AgentRequest request) {
        List<InputBlock> inputBlocks = request == null ? List.of() : request.getInput();
        List<AgentContentBlock> userInput;
        if (inputBlocks == null || inputBlocks.isEmpty()) {
            userInput = List.of(AgentTextBlock.builder().text("").build());
        } else {
            userInput = ContentBlockConverter.fromInputBlocks(inputBlocks);
        }
        return List.of(AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(userInput)
                .build());
    }

    @Override
    protected HarnessAgentRuntimeBuilder buildAgentBuilder(AgentRequest request, String systemPrompt) {
        HarnessAgentRuntimeBuilder builder = agentRuntimeFactory.createBuilder();
        builder.name(getAgentName());
        builder.systemPrompt(systemPrompt);
        builder.memoryConfig(AgentMemoryConfig.defaults());
        // 模型调用瞬时故障（连接重置/429/5xx）指数退避重试，流式仅首片到达前重试避免重复输出
        builder.modelRetryConfig(AgentModelRetryConfig.defaultEnabled());
        builder.maxIters(getMaxIterations());
        return builder;
    }

    @Override
    protected AgentBootstrap buildBootstrap(HarnessAgentRuntimeBuilder builder,
                                              List<AgentMessage> inputs,
                                              AgentResultConverter resultHandler,
                                              AgentRequest request) {
        AgentBootstrap plan = new AgentBootstrap()
                .agentBuilder(builder)
                .inputMessages(inputs)
                .resultConverter(resultHandler)
                .sessionConfig(Map.of(
                        ConversationBridge.ATTR_SESSION_ENABLED, true,
                        ConversationBridge.ATTR_SESSION_NAMESPACE, getSessionNamespace()
                ));

        // 动态解析编排方案并注册子代理
        if (request != null && request.getBody() != null) {
            resolveOrchestrationPlan(request);
        }

        return plan;
    }

    /**
     * 动态解析编排方案
     * <p>
     * 当 autoGenerate=true 且未提供子代理声明时，通过 LLM 自动生成；
     * 当请求未指定编排模式时，通过 LLM 决策最优模式。
     * </p>
     * @param request
     */
    private void resolveOrchestrationPlan(AgentRequest request) {
        Map<String, Object> body = request.getBody();
        boolean autoGenerate = Boolean.TRUE.equals(body.get(AgentRequest.BodyKeys.AUTO_GENERATE));
        String task = request.getInputAsText();

        // 解析已有声明（显式提供的优先）
        List<SubagentDeclaration> declarations = orchestrationSupport.resolveSubagentDeclarations(request);

        // 动态生成子代理
        if (autoGenerate && declarations.isEmpty()) {
            int maxSubagents = body.get(AgentRequest.BodyKeys.MAX_SUBAGENTS) instanceof Number n
                    ? n.intValue() : 5;
            String genModelCode = body.get(AgentRequest.BodyKeys.GENERATION_MODEL_CODE) instanceof String s
                    ? s : null;
            declarations = subagentSpecGeneratorService
                    .generateDeclarations(task, maxSubagents, Collections.emptyList(), genModelCode)
                    .block(Duration.ofSeconds(120));
            if (declarations == null) {
                declarations = Collections.emptyList();
            }
            log.info("动态生成子代理声明: count={}", declarations.size());
        }

        // 注册子代理（保留完整声明，含 systemPrompt/modelCode/temperature 等字段）
        for (SubagentDeclaration decl : declarations) {
            orchestrationSupport.registerSubagent(decl);
        }

        // 决策编排模式
        String mode = request.getOrchestrationMode();
        if (mode == null || mode.isBlank()) {
            String reqModelCode = body.get(AgentRequest.BodyKeys.GENERATION_MODEL_CODE) instanceof String s
                    ? s : null;
            mode = orchestrationDecisionService
                    .decideMode(task, reqModelCode)
                    .block(Duration.ofSeconds(60));
            if (mode == null || mode.isBlank()) {
                mode = "delegate";
            }
            log.info("动态决策编排模式: mode={}", mode);
        }

        // 存储到request body供process/stream使用
        body.put(ATTR_ORCHESTRATION_MODE, mode);
        body.put(ATTR_SUBAGENT_DECLARATIONS, declarations);
    }

    @Override
    public AgentResult process(AgentContext context) {
        String mode = context.getAttribute(ATTR_ORCHESTRATION_MODE);
        List<SubagentDeclaration> declarations = context.getAttribute(ATTR_SUBAGENT_DECLARATIONS);

        if (declarations == null || declarations.isEmpty()) {
            return super.process(context);
        }

        String query = StringUtils.getOrDefault(context.getRequest() == null ? null : context.getRequest().getInputAsText());

        return switch (StringUtils.getOrDefault(mode)) {
            case "sequential" -> orchestrateSequential(query, declarations, context.getRequest());
            case "parallel" -> orchestrateParallel(query, declarations, context.getRequest());
            case "delegate" -> {
                orchestrateDelegate(context, declarations);
                yield super.process(context);
            }
            default -> super.process(context);
        };
    }

    @Override
    public Flux<StreamEvent> stream(AgentContext context) {
        String mode = context.getAttribute(ATTR_ORCHESTRATION_MODE);
        List<SubagentDeclaration> declarations = context.getAttribute(ATTR_SUBAGENT_DECLARATIONS);

        if (declarations == null || declarations.isEmpty() || "delegate".equals(mode)) {
            return super.stream(context);
        }

        // 非delegate模式的流式，先同步执行编排，再包装为流
        return Flux.defer(() -> {
            try {
                AgentResult result = process(context);
                String text = result.isSuccess()
                        ? result.getOutputAsText()
                        : "编排执行失败: " + result.getErrorMessage();
                return Flux.just(StreamEvent.textDelta(text));
            } catch (Exception e) {
                return Flux.error(e);
            }
        });
    }
}
