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
package com.yangqiongai.ai.agent.core.middleware;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.security.GuardrailsManager;
import com.yangqiongai.ai.security.guardrails.GuardrailAction;
import com.yangqiongai.ai.security.guardrails.GuardrailContext;
import com.yangqiongai.ai.security.guardrails.GuardrailResult;
import com.yangqiongai.ai.security.guardrails.HookPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 安全护栏中间件
 * <p>
 * 将security模块护栏链接入运行时中间件体系，统一覆盖INPUT/SYSTEM_PROMPT/TOOL_CALL/OUTPUT
 * 四个挂载点，替代执行器内手动检查，避免双轨拦截。
 * 阻断语义与引擎原生护栏对齐：输入侧返回错误信号，输出侧替换为拦截提示。
 * </p>
 * @author yangqiong
 */
public class SecurityGuardrailMiddleware implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(SecurityGuardrailMiddleware.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 输出被拦截时的提示文案
     */
    private static final String OUTPUT_BLOCKED_TEXT = "抱歉，生成内容未通过安全检查，已被拦截。";

    /**
     * 护栏服务门面
     */
    private final GuardrailsManager guardrailsManager;

    /**
     * 请求级护栏上下文（agentCode/scopeId等请求元数据）
     */
    private final GuardrailContext baseContext;

    /**
     * 构造安全护栏中间件
     * @param guardrailsManager
     * @param baseContext
     */
    public SecurityGuardrailMiddleware(GuardrailsManager guardrailsManager, GuardrailContext baseContext) {
        this.guardrailsManager = guardrailsManager;
        this.baseContext = baseContext == null ? GuardrailContext.empty() : baseContext;
    }

    /**
     * Agent整体调用拦截，对当前轮用户输入执行INPUT挂载点检查
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Flux<AgentEvent> onAgent(AgentRuntimeContext context, List<AgentMessage> input,
                                    Function<List<AgentMessage>, Flux<AgentEvent>> next) {
        if (input == null || input.isEmpty()) {
            return next.apply(input);
        }
        // 仅检查末尾连续USER消息（当前轮输入），历史轮次在当轮已检查过，避免重复审计
        int start = input.size();
        while (start > 0 && isUserMessage(input.get(start - 1))) {
            start--;
        }
        GuardrailContext guardrailContext = buildContext(context);
        List<AgentMessage> checked = new ArrayList<>(input);
        for (int i = start; i < input.size(); i++) {
            String text = input.get(i).getTextContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            GuardrailResult result = guardrailsManager.check(HookPoint.INPUT, text, guardrailContext);
            if (!result.isPassed()) {
                log.warn("输入被安全护栏拦截: reason={}", result.getReason());
                return Flux.error(new AiException(AiErrorCode.AGENT_INPUT_BLOCKED,
                        "输入内容未通过安全检查: " + result.getReason()));
            }
            if (result.getAction() == GuardrailAction.MASK && result.getMaskedContent() != null) {
                checked.set(i, replaceText(input.get(i), result.getMaskedContent()));
            }
        }
        return next.apply(checked);
    }

    /**
     * 系统提示词处理钩子，组装完成后执行SYSTEM_PROMPT挂载点检查
     * @param systemPrompt
     * @param context
     * @return
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return systemPrompt;
        }
        GuardrailResult result = guardrailsManager.check(HookPoint.SYSTEM_PROMPT, systemPrompt, buildContext(context));
        if (!result.isPassed()) {
            log.warn("系统提示词被安全护栏拦截: reason={}", result.getReason());
            throw new AiException(AiErrorCode.AGENT_INPUT_BLOCKED,
                    "系统提示词未通过安全检查: " + result.getReason());
        }
        if (result.getAction() == GuardrailAction.MASK && result.getMaskedContent() != null) {
            return result.getMaskedContent();
        }
        return systemPrompt;
    }

    /**
     * 工具调用前拦截钩子，对参数JSON执行TOOL_CALL挂载点检查
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    @Override
    public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String json;
        try {
            json = JSON_MAPPER.writeValueAsString(input);
        } catch (Exception e) {
            log.warn("工具调用参数序列化失败，跳过护栏检查: tool={}", toolName);
            return input;
        }
        GuardrailResult result = guardrailsManager.check(HookPoint.TOOL_CALL, json, buildContext(context));
        if (!result.isPassed()) {
            log.warn("工具调用被安全护栏拦截: tool={}, reason={}", toolName, result.getReason());
            throw new AiException(AiErrorCode.AGENT_INPUT_BLOCKED,
                    "工具[" + toolName + "]调用参数未通过安全检查: " + result.getReason());
        }
        // MASK动作用于文本脱敏，JSON参数无法可靠映射回参数Map，此处仅放行并记录
        if (result.getAction() == GuardrailAction.MASK) {
            log.debug("工具调用参数命中脱敏规则，参数结构不支持脱敏回写，已放行: tool={}", toolName);
        }
        return input;
    }

    /**
     * 原始LLM调用拦截，对模型输出执行OUTPUT挂载点检查
     * @param context
     * @param input
     * @param next
     * @return
     */
    @Override
    public Mono<AgentMessage> onModelCall(AgentRuntimeContext context, List<AgentMessage> input,
                                          Function<List<AgentMessage>, Mono<AgentMessage>> next) {
        return next.apply(input).map(this::checkOutput);
    }

    /**
     * 检查模型输出文本，阻断时替换为拦截提示，脱敏时替换为脱敏文本
     * @param message
     * @return
     */
    private AgentMessage checkOutput(AgentMessage message) {
        if (message == null) {
            return null;
        }
        String text = message.getTextContent();
        if (text == null || text.isBlank()) {
            return message;
        }
        GuardrailResult result = guardrailsManager.check(HookPoint.OUTPUT, text, baseContext);
        if (!result.isPassed()) {
            log.warn("输出被安全护栏拦截: reason={}", result.getReason());
            return replaceText(message, OUTPUT_BLOCKED_TEXT);
        }
        if (result.getAction() == GuardrailAction.MASK && result.getMaskedContent() != null) {
            return replaceText(message, result.getMaskedContent());
        }
        return message;
    }

    /**
     * 合并请求级元数据与运行时上下文会话信息
     * @param context
     * @return
     */
    private GuardrailContext buildContext(AgentRuntimeContext context) {
        if (context == null) {
            return baseContext;
        }
        return new GuardrailContext(baseContext.agentId(), baseContext.agentCode(),
                context.getSessionId() != null ? context.getSessionId() : baseContext.sessionId(),
                context.getUserId() != null ? context.getUserId() : baseContext.userId(),
                baseContext.scopeId(), baseContext.metadata());
    }

    /**
     * 判断是否为用户消息
     * @param message
     * @return
     */
    private boolean isUserMessage(AgentMessage message) {
        return message != null && message.getRole() == AgentMessageRole.USER;
    }

    /**
     * 以新文本重建消息，保留角色、名称与用量信息
     * @param message
     * @param text
     * @return
     */
    private AgentMessage replaceText(AgentMessage message, String text) {
        return AgentMessage.builder()
                .role(message.getRole())
                .name(message.getName())
                .content(List.of(AgentTextBlock.builder().text(text).build()))
                .chatUsage(message.getChatUsage())
                .latency(message.getLatency())
                .build();
    }
}
