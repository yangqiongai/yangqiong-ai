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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.core.HarnessAgentRuntimeBuilder;
import com.yangqiongai.agent.harness.config.*;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptControl;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptSource;
import com.yangqiongai.agent.harness.core.message.*;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.TokenMetrics;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;
import com.yangqiongai.agent.harness.core.tool.AgentToolSpec;
import com.yangqiongai.ai.common.scope.ScopeContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/**
 * 类型转换器
 * @author yangqiong
 */
public final class RuntimeTypeConverter {

    private RuntimeTypeConverter() {
    }

    // ========== 消息类型转换 ==========

    /**
     * 转换Agent消息为Harness类型
     * @param message
     * @return
     */
    public static AgentMessage toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentMessage message) {
        if (message == null) {
            return null;
        }
        return AgentMessage.builder()
                .name(message.getName())
                .role(toHarness(message.getRole()))
                .content(toHarnessContentList(message.getContent()))
                .chatUsage(toHarness(message.getChatUsage()))
                .latency(message.getLatency())
                .build();
    }

    /**
     * 转换Agent消息为Runtime类型
     * @param message
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentMessage toRuntime(
            AgentMessage message) {
        if (message == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentMessage.builder()
                .name(message.getName())
                .role(toRuntime(message.getRole()))
                .content(toRuntimeContentList(message.getContent()))
                .chatUsage(toRuntime(message.getChatUsage()))
                .latency(message.getLatency())
                .build();
    }

    /**
     * 转换消息角色为Harness类型
     * @param role
     * @return
     */
    public static AgentMessageRole toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentMessageRole role) {
        if (role == null) {
            return null;
        }
        return AgentMessageRole.valueOf(role.name());
    }

    /**
     * 转换消息角色为Runtime类型
     * @param role
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentMessageRole toRuntime(
            AgentMessageRole role) {
        if (role == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentMessageRole.valueOf(role.name());
    }

    /**
     * 转换内容块为Harness类型
     * @param block
     * @return
     */
    public static AgentContentBlock toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentContentBlock block) {
        if (block == null) {
            return null;
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentTextBlock textBlock) {
            return toHarness(textBlock);
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentImageBlock imageBlock) {
            return toHarness(imageBlock);
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock thinkingBlock) {
            return toHarness(thinkingBlock);
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock toolUseBlock) {
            return toHarness(toolUseBlock);
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock toolResultBlock) {
            return toHarness(toolResultBlock);
        }
        throw new IllegalArgumentException("不支持的内容块类型: " + block.getClass().getName());
    }

    /**
     * 转换内容块为Runtime类型
     * @param block
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentContentBlock toRuntime(
            AgentContentBlock block) {
        if (block == null) {
            return null;
        }
        if (block instanceof AgentTextBlock textBlock) {
            return toRuntime(textBlock);
        }
        if (block instanceof AgentImageBlock imageBlock) {
            return toRuntime(imageBlock);
        }
        if (block instanceof AgentThinkingBlock thinkingBlock) {
            return toRuntime(thinkingBlock);
        }
        if (block instanceof AgentToolUseBlock toolUseBlock) {
            return toRuntime(toolUseBlock);
        }
        if (block instanceof AgentToolResultBlock toolResultBlock) {
            return toRuntime(toolResultBlock);
        }
        throw new IllegalArgumentException("不支持的内容块类型: " + block.getClass().getName());
    }

    /**
     * 转换文本块为Harness类型
     * @param block
     * @return
     */
    public static AgentTextBlock toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentTextBlock block) {
        if (block == null) {
            return null;
        }
        return AgentTextBlock.builder()
                .text(block.getText())
                .build();
    }

    /**
     * 转换文本块为Runtime类型
     * @param block
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentTextBlock toRuntime(
            AgentTextBlock block) {
        if (block == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentTextBlock.builder()
                .text(block.getText())
                .build();
    }

    /**
     * 转换图像块为Harness类型
     * @param block
     * @return
     */
    public static AgentImageBlock toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentImageBlock block) {
        if (block == null) {
            return null;
        }
        return AgentImageBlock.builder()
                .url(block.getUrl())
                .base64Data(block.getBase64Data())
                .mediaType(block.getMediaType())
                .build();
    }

    /**
     * 转换图像块为Runtime类型
     * @param block
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentImageBlock toRuntime(
            AgentImageBlock block) {
        if (block == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentImageBlock.builder()
                .url(block.getUrl())
                .base64Data(block.getBase64Data())
                .mediaType(block.getMediaType())
                .build();
    }

    /**
     * 转换思考块为Harness类型
     * @param block
     * @return
     */
    public static AgentThinkingBlock toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock block) {
        if (block == null) {
            return null;
        }
        return AgentThinkingBlock.builder()
                .thinking(block.getThinking())
                .build();
    }

    /**
     * 转换思考块为Runtime类型
     * @param block
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock toRuntime(
            AgentThinkingBlock block) {
        if (block == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock.builder()
                .thinking(block.getThinking())
                .build();
    }

    /**
     * 转换工具调用块为Harness类型
     * @param block
     * @return
     */
    public static AgentToolUseBlock toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock block) {
        if (block == null) {
            return null;
        }
        return new AgentToolUseBlock(
                block.getToolName(), block.getToolUseId(), block.getInput());
    }

    /**
     * 转换工具调用块为Runtime类型
     * @param block
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock toRuntime(
            AgentToolUseBlock block) {
        if (block == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock(
                block.getToolName(), block.getToolUseId(), block.getInput());
    }

    /**
     * 转换工具结果块为Harness类型
     * @param block
     * @return
     */
    public static AgentToolResultBlock toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock block) {
        if (block == null) {
            return null;
        }
        if (block.isError()) {
            return AgentToolResultBlock.error(
                    block.getToolUseId(), block.getTextContent());
        }
        return AgentToolResultBlock.of(
                block.getToolUseId(), toHarnessContentList(block.getContent()));
    }

    /**
     * 转换工具结果块为Runtime类型
     * @param block
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock toRuntime(
            AgentToolResultBlock block) {
        if (block == null) {
            return null;
        }
        if (block.isError()) {
            return com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock.error(
                    block.getToolUseId(), block.getTextContent());
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock.of(
                block.getToolUseId(), toRuntimeContentList(block.getContent()));
    }

    /**
     * 转换Token用量统计为Harness类型
     * @param usage
     * @return
     */
    public static AgentChatUsage toHarness(
            com.yangqiongai.ai.agent.runtime.message.AgentChatUsage usage) {
        if (usage == null) {
            return null;
        }
        return new AgentChatUsage(
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 转换Token用量统计为Runtime类型
     * @param usage
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.message.AgentChatUsage toRuntime(
            AgentChatUsage usage) {
        if (usage == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.message.AgentChatUsage(
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    // ========== 运行时上下文转换 ==========

    /**
     * 转换运行时上下文为Harness类型
     * @param context
     * @return
     */
    public static com.yangqiongai.agent.harness.engine.AgentRuntimeContext toHarness(
            com.yangqiongai.ai.agent.runtime.AgentRuntimeContext context) {
        if (context == null) {
            return null;
        }
        return com.yangqiongai.agent.harness.engine.AgentRuntimeContext.builder()
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .scopeId(resolveScopeId(context))
                .interruptControl(toHarness(context.getInterruptControl()))
                .attributes(context.getAttributes())
                .build();
    }

    /**
     * 解析引擎侧隔离域ID：优先取运行时上下文attributes中的scopeId，缺失时回退当前线程上下文
     * @param context
     * @return
     */
    private static String resolveScopeId(com.yangqiongai.ai.agent.runtime.AgentRuntimeContext context) {
        Object scopeId = context.get("scopeId");
        if (scopeId != null && !String.valueOf(scopeId).isBlank()) {
            return String.valueOf(scopeId);
        }
        return ScopeContext.getScopeId();
    }

    /**
     * 转换运行时上下文为Runtime类型
     * @param context
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.AgentRuntimeContext toRuntime(
            com.yangqiongai.agent.harness.engine.AgentRuntimeContext context) {
        if (context == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.AgentRuntimeContext.builder()
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .interruptControl(toRuntime(context.getInterruptControl()))
                .attributes(context.getAttributes())
                .build();
    }

    /**
     * 转换中断控制器为Harness类型
     * @param control
     * @return
     */
    public static AgentInterruptControl toHarness(
            com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl control) {
        if (control == null) {
            return null;
        }
        return new AgentInterruptControl() {
            @Override
            public void trigger(AgentInterruptSource source,
                                AgentMessage message) {
                control.trigger(toRuntime(source), toRuntime(message));
            }

            @Override
            public boolean isInterrupted() {
                return control.isInterrupted();
            }

            @Override
            public void reset() {
                control.reset();
            }
        };
    }

    /**
     * 转换中断控制器为Runtime类型
     * @param control
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl toRuntime(
            AgentInterruptControl control) {
        if (control == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl() {
            @Override
            public void trigger(com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource source,
                                com.yangqiongai.ai.agent.runtime.message.AgentMessage message) {
                control.trigger(toHarness(source), toHarness(message));
            }

            @Override
            public boolean isInterrupted() {
                return control.isInterrupted();
            }

            @Override
            public void reset() {
                control.reset();
            }
        };
    }

    /**
     * 转换中断来源为Harness类型
     * @param source
     * @return
     */
    public static AgentInterruptSource toHarness(
            com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource source) {
        if (source == null) {
            return null;
        }
        return AgentInterruptSource.valueOf(source.name());
    }

    /**
     * 转换中断来源为Runtime类型
     * @param source
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource toRuntime(
            AgentInterruptSource source) {
        if (source == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource.valueOf(source.name());
    }

    // ========== 事件类型转换 ==========

    /**
     * 转换Agent事件为Harness类型
     * @param event
     * @return
     */
    public static AgentEvent toHarness(
            com.yangqiongai.ai.agent.runtime.event.AgentEvent event) {
        if (event == null) {
            return null;
        }
        return AgentEvent.of(
                toHarness(event.getType()),
                toHarnessPayload(event.getPayload()),
                event.getParentAgentPath());
    }

    /**
     * 转换Agent事件为Runtime类型
     * <p>
     * 引擎侧新增而运行时未同步的事件类型按CUSTOM透传（rawTypeName保留原始类型名），避免流式链路中断。
     * </p>
     * @param event
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.event.AgentEvent toRuntime(
            AgentEvent event) {
        if (event == null) {
            return null;
        }
        com.yangqiongai.ai.agent.runtime.event.AgentEventType runtimeType;
        try {
            runtimeType = toRuntime(event.getType());
        } catch (IllegalArgumentException ex) {
            // 单侧特有事件透传为CUSTOM，原始类型名随rawTypeName保留
            return com.yangqiongai.ai.agent.runtime.event.AgentEvent.custom(
                    event.getType().name(), toRuntimePayload(event.getPayload()));
        }
        return com.yangqiongai.ai.agent.runtime.event.AgentEvent.of(
                runtimeType,
                toRuntimePayload(event.getPayload()),
                event.getParentAgentPath());
    }

    /**
     * 转换事件类型为Harness类型
     * @param type
     * @return
     */
    public static AgentEventType toHarness(
            com.yangqiongai.ai.agent.runtime.event.AgentEventType type) {
        if (type == null) {
            return null;
        }
        return AgentEventType.valueOf(type.name());
    }

    /**
     * 转换事件类型为Runtime类型
     * @param type
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.event.AgentEventType toRuntime(
            AgentEventType type) {
        if (type == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.event.AgentEventType.valueOf(type.name());
    }

    /**
     * 转换审批确认结果为Harness类型
     * @param result
     * @return
     */
    public static ConfirmResult toHarness(
            com.yangqiongai.ai.agent.runtime.event.ConfirmResult result) {
        if (result == null) {
            return null;
        }
        return new ConfirmResult(
                result.getToolCallId(), result.getToolName(), result.isApproved(), result.getReason());
    }

    /**
     * 转换审批确认结果为Runtime类型
     * @param result
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.event.ConfirmResult toRuntime(
            ConfirmResult result) {
        if (result == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.event.ConfirmResult(
                result.getToolCallId(), result.getToolName(), result.isApproved(), result.getReason());
    }

    // ========== 模型类型转换 ==========

    /**
     * 转换生成选项为Harness类型
     * @param options
     * @return
     */
    public static AgentGenerateOptions toHarness(
            com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions options) {
        if (options == null) {
            return null;
        }
        return AgentGenerateOptions.builder()
                .temperature(options.getTemperature())
                .maxTokens(options.getMaxTokens())
                .topP(options.getTopP())
                .reasoningEffort(options.getReasoningEffort())
                .thinkingBudget(options.getThinkingBudget())
                .stream(options.isStream())
                .responseFormat(toHarness(options.getResponseFormat()))
                .toolChoice(toHarness(options.getToolChoice()))
                .build();
    }

    /**
     * 转换生成选项为Runtime类型
     * @param options
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions toRuntime(
            AgentGenerateOptions options) {
        if (options == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions.builder()
                .temperature(options.getTemperature())
                .maxTokens(options.getMaxTokens())
                .topP(options.getTopP())
                .reasoningEffort(options.getReasoningEffort())
                .thinkingBudget(options.getThinkingBudget())
                .stream(options.isStream())
                .responseFormat(toRuntime(options.getResponseFormat()))
                .toolChoice(toRuntime(options.getToolChoice()))
                .build();
    }

    /**
     * 转换对话响应为Harness类型
     * @param response
     * @return
     */
    public static AgentChatResponse toHarness(
            com.yangqiongai.ai.agent.runtime.model.AgentChatResponse response) {
        if (response == null) {
            return null;
        }
        return new AgentChatResponse(
                toHarnessContentList(response.getContent()),
                toHarness(response.getChatUsage()));
    }

    /**
     * 转换对话响应为Runtime类型
     * @param response
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.model.AgentChatResponse toRuntime(
            AgentChatResponse response) {
        if (response == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.model.AgentChatResponse(
                toRuntimeContentList(response.getContent()),
                toRuntime(response.getChatUsage()));
    }

    /**
     * 转换Token消耗指标为Harness类型
     * @param metrics
     * @return
     */
    public static TokenMetrics toHarness(
            com.yangqiongai.ai.agent.runtime.model.TokenMetrics metrics) {
        if (metrics == null) {
            return null;
        }
        return TokenMetrics.builder()
                .inputTokens(metrics.getInputTokens())
                .outputTokens(metrics.getOutputTokens())
                .totalTokens(metrics.getTotalTokens())
                .time(metrics.getTime())
                .build();
    }

    /**
     * 转换Token消耗指标为Runtime类型
     * @param metrics
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.model.TokenMetrics toRuntime(
            TokenMetrics metrics) {
        if (metrics == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.model.TokenMetrics.builder()
                .inputTokens(metrics.getInputTokens())
                .outputTokens(metrics.getOutputTokens())
                .totalTokens(metrics.getTotalTokens())
                .time(metrics.getTime())
                .build();
    }

    // ========== 配置类型转换 ==========

    /**
     * 转换消息压缩配置为Harness类型
     * @param config
     * @return
     */
    public static AgentCompactionConfig toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentCompactionConfig config) {
        if (config == null) {
            return null;
        }
        return AgentCompactionConfig.builder()
                .triggerMessages(config.getTriggerMessages())
                .keepMessages(config.getKeepMessages())
                .build();
    }

    /**
     * 转换工具结果驱逐配置为Harness类型
     * @param config
     * @return
     */
    public static AgentToolResultEvictionConfig toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentToolResultEvictionConfig config) {
        if (config == null) {
            return null;
        }
        return AgentToolResultEvictionConfig.builder()
                .maxResultChars(config.getMaxResultChars())
                .previewChars(config.getPreviewChars())
                .build();
    }

    /**
     * 转换记忆配置为Harness类型
     * @param config
     * @return
     */
    public static AgentMemoryConfig toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig config) {
        if (config == null) {
            return null;
        }
        return AgentMemoryConfig.builder()
                .compactionConfig(toHarness(config.getCompactionConfig()))
                .toolResultEvictionConfig(toHarness(config.getToolResultEvictionConfig()))
                .build();
    }

    /**
     * 转换权限上下文状态为Harness类型
     * @param state
     * @return
     */
    public static AgentPermissionContextState toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentPermissionContextState state) {
        if (state == null) {
            return null;
        }
        List<AgentPermissionRule> rules = new ArrayList<>();
        for (com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule rule : state.getRules()) {
            rules.add(toHarness(rule));
        }
        return AgentPermissionContextState.builder()
                .mode(toHarness(state.getMode()))
                .rules(rules)
                .build();
    }

    /**
     * 转换权限模式为Harness类型
     * @param mode
     * @return
     */
    public static AgentPermissionMode toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentPermissionMode mode) {
        if (mode == null) {
            return null;
        }
        return AgentPermissionMode.valueOf(mode.name());
    }

    /**
     * 转换权限规则为Harness类型
     * @param rule
     * @return
     */
    public static AgentPermissionRule toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule rule) {
        if (rule == null) {
            return null;
        }
        return AgentPermissionRule.builder()
                .toolName(rule.getToolName())
                .mode(toHarness(rule.getMode()))
                .build();
    }

    /**
     * 转换响应格式为Harness类型
     * @param format
     * @return
     */
    public static AgentResponseFormat toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat format) {
        if (format == null) {
            return null;
        }
        return new AgentResponseFormat(
                format.getType(), toHarness(format.getJsonSchema()));
    }

    /**
     * 转换响应格式为Runtime类型
     * @param format
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat toRuntime(
            AgentResponseFormat format) {
        if (format == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat(
                format.getType(), toRuntime(format.getJsonSchema()));
    }

    /**
     * 转换JSON Schema为Harness类型
     * @param schema
     * @return
     */
    public static AgentJsonSchema toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentJsonSchema schema) {
        if (schema == null) {
            return null;
        }
        return AgentJsonSchema.builder()
                .name(schema.getName())
                .schema(schema.getSchema())
                .strict(schema.isStrict())
                .build();
    }

    /**
     * 转换JSON Schema为Runtime类型
     * @param schema
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.AgentJsonSchema toRuntime(
            AgentJsonSchema schema) {
        if (schema == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentJsonSchema.builder()
                .name(schema.getName())
                .schema(schema.getSchema())
                .strict(schema.isStrict())
                .build();
    }

    /**
     * 转换工具选择策略为Harness类型
     * @param choice
     * @return
     */
    public static AgentToolChoice toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentToolChoice choice) {
        if (choice == null) {
            return null;
        }
        return AgentToolChoice.valueOf(choice.name());
    }

    /**
     * 转换工具选择策略为Runtime类型
     * @param choice
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.AgentToolChoice toRuntime(
            AgentToolChoice choice) {
        if (choice == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentToolChoice.valueOf(choice.name());
    }

    // ========== 编排类型转换 ==========

    /**
     * 转换子代理声明为Harness类型
     * @param declaration
     * @return
     */
    public static SubagentDeclaration toHarness(
            com.yangqiongai.ai.agent.runtime.orchestration.SubagentDeclaration declaration) {
        if (declaration == null) {
            return null;
        }
        return SubagentDeclaration.builder()
                .name(declaration.getName())
                .description(declaration.getDescription())
                .agentCode(declaration.getAgentCode())
                .modelCode(declaration.getModelCode())
                .systemPrompt(declaration.getSystemPrompt())
                .tools(declaration.getTools() != null ? new ArrayList<>(declaration.getTools()) : null)
                .temperature(declaration.getTemperature())
                .maxIterations(declaration.getMaxIterations())
                .inheritTools(declaration.getInheritTools())
                .allowedTools(declaration.getAllowedTools() != null ? new HashSet<>(declaration.getAllowedTools()) : null)
                .deniedTools(declaration.getDeniedTools() != null ? new HashSet<>(declaration.getDeniedTools()) : null)
                .inheritSkills(declaration.getInheritSkills())
                .inheritMcp(declaration.getInheritMcp())
                .inheritMiddlewares(declaration.getInheritMiddlewares())
                .build();
    }

    // ========== Harness枚举转换 ==========

    /**
     * 转换工具失败策略为Harness类型
     * @param strategy
     * @return
     */
    public static HarnessAgentRuntimeBuilder.ToolFailureStrategy toHarness(
            com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolFailureStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return HarnessAgentRuntimeBuilder.ToolFailureStrategy.valueOf(strategy.name());
    }

    /**
     * 转换工具失败策略为Runtime类型
     * @param strategy
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolFailureStrategy toRuntime(
            HarnessAgentRuntimeBuilder.ToolFailureStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolFailureStrategy.valueOf(strategy.name());
    }

    /**
     * 转换历史截断策略为Harness类型
     * @param strategy
     * @return
     */
    public static HarnessAgentRuntimeBuilder.TruncationStrategy toHarness(
            com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.TruncationStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return HarnessAgentRuntimeBuilder.TruncationStrategy.valueOf(strategy.name());
    }

    /**
     * 转换历史截断策略为Runtime类型
     * @param strategy
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.TruncationStrategy toRuntime(
            HarnessAgentRuntimeBuilder.TruncationStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.TruncationStrategy.valueOf(strategy.name());
    }

    /**
     * 转换总超时模式为Harness类型
     * @param mode
     * @return
     */
    public static HarnessAgentRuntimeBuilder.TimeoutMode toHarness(
            com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.TimeoutMode mode) {
        if (mode == null) {
            return null;
        }
        return HarnessAgentRuntimeBuilder.TimeoutMode.valueOf(mode.name());
    }

    /**
     * 转换总超时模式为Runtime类型
     * @param mode
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.TimeoutMode toRuntime(
            HarnessAgentRuntimeBuilder.TimeoutMode mode) {
        if (mode == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.TimeoutMode.valueOf(mode.name());
    }

    /**
     * 转换工具下发模式为Harness类型
     * @param mode
     * @return
     */
    public static ToolLoadingMode toHarness(
            com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode mode) {
        if (mode == null) {
            return null;
        }
        return ToolLoadingMode.valueOf(mode.name());
    }

    /**
     * 转换工具下发模式为Runtime类型
     * @param mode
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode toRuntime(
            ToolLoadingMode mode) {
        if (mode == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode.valueOf(mode.name());
    }

    /**
     * 转换审批模式为Harness类型
     * @param mode
     * @return
     */
    public static AgentApprovalMode toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode mode) {
        if (mode == null) {
            return null;
        }
        return AgentApprovalMode.valueOf(mode.name());
    }

    /**
     * 转换审批模式为Runtime类型
     * @param mode
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode toRuntime(
            AgentApprovalMode mode) {
        if (mode == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode.valueOf(mode.name());
    }

    /**
     * 转换权限决策为Harness类型
     * @param decision
     * @return
     */
    public static AgentPermissionDecision toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentPermissionDecision decision) {
        if (decision == null) {
            return null;
        }
        return AgentPermissionDecision.valueOf(decision.name());
    }

    /**
     * 转换权限决策为Runtime类型
     * @param decision
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.AgentPermissionDecision toRuntime(
            AgentPermissionDecision decision) {
        if (decision == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentPermissionDecision.valueOf(decision.name());
    }

    /**
     * 转换编排策略为Harness类型
     * @param strategy
     * @return
     */
    public static com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategy toHarness(
            com.yangqiongai.ai.agent.runtime.config.OrchestrationStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategy.valueOf(strategy.name());
    }

    /**
     * 转换编排策略为Runtime类型
     * @param strategy
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.OrchestrationStrategy toRuntime(
            com.yangqiongai.agent.harness.subagent.orchestration.OrchestrationStrategy strategy) {
        if (strategy == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.OrchestrationStrategy.valueOf(strategy.name());
    }

    /**
     * 转换模型重试配置为Harness类型
     * @param config
     * @return
     */
    public static com.yangqiongai.agent.harness.model.ModelRetryConfig toHarness(
            com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig config) {
        if (config == null) {
            return null;
        }
        return com.yangqiongai.agent.harness.model.ModelRetryConfig.builder()
                .enabled(config.isEnabled())
                .maxRetries(config.getMaxRetries())
                .initialBackoff(config.getInitialBackoff())
                .backoffMultiplier(config.getBackoffMultiplier())
                .maxBackoff(config.getMaxBackoff())
                .build();
    }

    /**
     * 转换模型重试配置为Runtime类型
     * @param config
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig toRuntime(
            com.yangqiongai.agent.harness.model.ModelRetryConfig config) {
        if (config == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig.builder()
                .enabled(config.isEnabled())
                .maxRetries(config.getMaxRetries())
                .initialBackoff(config.getInitialBackoff())
                .backoffMultiplier(config.getBackoffMultiplier())
                .maxBackoff(config.getMaxBackoff())
                .build();
    }

    /**
     * 转换模型定价为Harness类型
     * @param pricing
     * @return
     */
    public static com.yangqiongai.agent.harness.model.ModelPricing toHarness(
            com.yangqiongai.ai.agent.runtime.budget.ModelPricing pricing) {
        if (pricing == null) {
            return null;
        }
        return new com.yangqiongai.agent.harness.model.ModelPricing(
                pricing.modelCode(), pricing.promptUsdPer1k(), pricing.completionUsdPer1k());
    }

    /**
     * 转换模型定价列表为Harness类型
     * @param pricings
     * @return
     */
    public static List<com.yangqiongai.agent.harness.model.ModelPricing> toHarnessPricings(
            com.yangqiongai.ai.agent.runtime.budget.ModelPricing... pricings) {
        if (pricings == null) {
            return null;
        }
        List<com.yangqiongai.agent.harness.model.ModelPricing> result = new ArrayList<>(pricings.length);
        for (com.yangqiongai.ai.agent.runtime.budget.ModelPricing pricing : pricings) {
            if (pricing != null) {
                result.add(toHarness(pricing));
            }
        }
        return result;
    }

    // ========== 工具类型转换 ==========

    /**
     * 转换工具调用参数为Harness类型
     * @param param
     * @return
     */
    public static AgentToolCallParam toHarness(
            com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam param) {
        if (param == null) {
            return null;
        }
        return new AgentToolCallParam(param.getInput());
    }

    /**
     * 转换工具调用参数为Runtime类型
     * @param param
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam toRuntime(
            AgentToolCallParam param) {
        if (param == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam(param.getInput());
    }

    /**
     * 转换工具规格为Harness类型
     * @param spec
     * @return
     */
    public static AgentToolSpec toHarness(
            com.yangqiongai.ai.agent.runtime.tool.AgentToolSpec spec) {
        if (spec == null) {
            return null;
        }
        return AgentToolSpec.builder()
                .name(spec.getName())
                .description(spec.getDescription())
                .parameters(spec.getParameters())
                .build();
    }

    /**
     * 转换工具规格为Runtime类型
     * @param spec
     * @return
     */
    public static com.yangqiongai.ai.agent.runtime.tool.AgentToolSpec toRuntime(
            AgentToolSpec spec) {
        if (spec == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.tool.AgentToolSpec.builder()
                .name(spec.getName())
                .description(spec.getDescription())
                .parameters(spec.getParameters())
                .build();
    }

    // ========== 私有辅助方法 ==========

    /**
     * 转换内容块列表为Harness类型
     * @param blocks
     * @return
     */
    private static List<AgentContentBlock> toHarnessContentList(
            List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> blocks) {
        if (blocks == null) {
            return null;
        }
        List<AgentContentBlock> result =
                new ArrayList<>(blocks.size());
        for (com.yangqiongai.ai.agent.runtime.message.AgentContentBlock block : blocks) {
            result.add(toHarness(block));
        }
        return result;
    }

    /**
     * 转换内容块列表为Runtime类型
     * @param blocks
     * @return
     */
    private static List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> toRuntimeContentList(
            List<AgentContentBlock> blocks) {
        if (blocks == null) {
            return null;
        }
        List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> result =
                new ArrayList<>(blocks.size());
        for (AgentContentBlock block : blocks) {
            result.add(toRuntime(block));
        }
        return result;
    }

    /**
     * 转换事件载荷为Harness类型
     * @param payload
     * @return
     */
    private static Object toHarnessPayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof com.yangqiongai.ai.agent.runtime.event.ConfirmResult confirmResult) {
            return toHarness(confirmResult);
        }
        if (payload instanceof com.yangqiongai.ai.agent.runtime.message.AgentMessage message) {
            return toHarness(message);
        }
        return payload;
    }

    /**
     * 转换事件载荷为Runtime类型
     * @param payload
     * @return
     */
    private static Object toRuntimePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof ConfirmResult confirmResult) {
            return toRuntime(confirmResult);
        }
        if (payload instanceof AgentMessage message) {
            return toRuntime(message);
        }
        return payload;
    }
}
