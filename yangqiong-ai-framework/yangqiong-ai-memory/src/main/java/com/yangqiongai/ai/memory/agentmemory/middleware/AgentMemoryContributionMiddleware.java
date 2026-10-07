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
package com.yangqiongai.ai.memory.agentmemory.middleware;

import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.AgentResultEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunOutcome;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunQuery;
import com.yangqiongai.ai.memory.spi.MemoryContributor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Agent运行记忆中间件桥接
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.memory.agent", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AgentMemoryContributionMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryContributionMiddleware.class);

    private static final String MEMORY_BLOCK_PREFIX = "\n\n";

    private static final String DEFAULT_AGENT_CODE = "default";

    /**
     * 尾部注入：组织上下文与运行记忆置于系统提示词末尾，贴近用户消息获得更强注意力
     * @return
     */
    @Override
    public boolean isTailInjection() {
        return true;
    }

    @Autowired(required = false)
    private List<MemoryContributor> contributors;

    @Value("${ai.memory.agent.middleware.inject-token-budget:512}")
    private int injectTokenBudget;

    /**
     * 系统提示词注入运行记忆（无头运行注入入口，T19触发器同链路）
     * @param systemPrompt
     * @param context
     * @return
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        if (contributors == null || contributors.isEmpty()) {
            return systemPrompt;
        }
        try {
            MemoryRunQuery query = buildQuery(context, null);
            StringBuilder block = new StringBuilder();
            for (MemoryContributor contributor : contributors) {
                try {
                    var injection = contributor.beforeRun(query);
                    if (injection != null && injection.getText() != null && !injection.getText().isBlank()) {
                        block.append(injection.getText()).append("\n");
                    }
                } catch (Exception e) {
                    // 单个贡献者失败不影响其余注入
                    log.warn("MemoryContributor注入失败, contributor={}", contributor.getClass().getSimpleName(), e);
                }
            }
            if (block.isEmpty()) {
                return systemPrompt;
            }
            return systemPrompt + MEMORY_BLOCK_PREFIX + block.toString().strip();
        } catch (Exception e) {
            // 记忆注入失败不影响Agent主流程
            log.warn("Agent记忆注入降级", e);
            return systemPrompt;
        }
    }

    /**
     * 整体调用拦截：捕获最终结果，运行结束后产出记忆候选
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                    Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (contributors == null || contributors.isEmpty()) {
            return next.apply(input);
        }
        String inputText = extractInputText(input);
        AtomicReference<String> capturedResult = new AtomicReference<>();
        return next.apply(input)
                .doOnNext(event -> captureResult(capturedResult, event))
                .doFinally(signal -> produceMemories(context, inputText, capturedResult.get()));
    }

    /**
     * 捕获AGENT_RESULT事件的最终输出
     * @param capturedResult
     * @param event
     */
    private void captureResult(AtomicReference<String> capturedResult, AgentEvent event) {
        if (event.getType() == AgentEventType.AGENT_RESULT && event instanceof AgentResultEvent resultEvent) {
            AgentMessage result = resultEvent.getResult();
            if (result != null) {
                capturedResult.set(result.getTextContent());
            }
        }
    }

    /**
     * 运行结束后产出记忆候选
     * @param context
     * @param inputText
     * @param outputText
     */
    private void produceMemories(AgentRuntimeContext context, String inputText, String outputText) {
        try {
            if (outputText == null || outputText.isBlank()) {
                return;
            }
            MemoryRunOutcome outcome = new MemoryRunOutcome();
            outcome.setAgentCode(resolveAgentCode(context));
            outcome.setUserAnchor(resolveUserAnchor(context));
            outcome.setSessionId(context.getSessionId());
            Object scopeId = context.get("scopeId");
            outcome.setScopeId(scopeId != null ? String.valueOf(scopeId) : null);
            outcome.setInputText(inputText);
            outcome.setOutputText(outputText);
            for (MemoryContributor contributor : contributors) {
                try {
                    contributor.afterRun(outcome);
                } catch (Exception e) {
                    log.warn("MemoryContributor产出失败, contributor={}", contributor.getClass().getSimpleName(), e);
                }
            }
        } catch (Exception e) {
            log.warn("Agent记忆产出降级", e);
        }
    }

    /**
     * 构建检索入参
     * @param context
     * @param query
     * @return
     */
    private MemoryRunQuery buildQuery(AgentRuntimeContext context, String query) {
        MemoryRunQuery runQuery = new MemoryRunQuery();
        runQuery.setAgentCode(resolveAgentCode(context));
        runQuery.setUserAnchor(resolveUserAnchor(context));
        runQuery.setSessionId(context.getSessionId());
        runQuery.setQuery(query);
        return runQuery;
    }

    /**
     * 解析Agent编码（属性优先，缺省default）
     * @param context
     * @return
     */
    private String resolveAgentCode(AgentRuntimeContext context) {
        Object agentCode = context.getAttributes().get("agentCode");
        if (agentCode != null && !agentCode.toString().isBlank()) {
            return agentCode.toString();
        }
        return DEFAULT_AGENT_CODE;
    }

    /**
     * 解析用户锚点（userId缺失时回退anonymous）
     * @param context
     * @return
     */
    private String resolveUserAnchor(AgentRuntimeContext context) {
        String userId = context.getUserId();
        return userId == null || userId.isBlank() ? "anonymous" : userId;
    }

    /**
     * 提取输入文本（最后一条USER消息）
     * @param input
     * @return
     */
    private String extractInputText(List<AgentMessage> input) {
        if (input == null || input.isEmpty()) {
            return null;
        }
        for (int i = input.size() - 1; i >= 0; i--) {
            AgentMessage message = input.get(i);
            if (message.getRole() == com.yangqiongai.ai.agent.runtime.message.AgentMessageRole.USER) {
                String text = message.getTextContent();
                return text == null || text.isBlank() ? null : text;
            }
        }
        return null;
    }
}
