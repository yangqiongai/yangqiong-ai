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

import com.yangqiongai.agent.harness.engine.AgentRuntimeContext;
import com.yangqiongai.agent.harness.config.AgentCompactionConfig;
import com.yangqiongai.agent.harness.config.AgentJsonSchema;
import com.yangqiongai.agent.harness.config.AgentMemoryConfig;
import com.yangqiongai.agent.harness.config.AgentPermissionContextState;
import com.yangqiongai.agent.harness.config.AgentPermissionRule;
import com.yangqiongai.agent.harness.config.AgentResponseFormat;
import com.yangqiongai.agent.harness.config.AgentToolResultEvictionConfig;
import com.yangqiongai.agent.harness.core.event.AgentEvent;
import com.yangqiongai.agent.harness.core.event.AgentEventType;
import com.yangqiongai.agent.harness.core.event.AgentResultEvent;
import com.yangqiongai.agent.harness.core.event.AgentTextBlockDeltaEvent;
import com.yangqiongai.agent.harness.core.event.AgentThinkingBlockDeltaEvent;
import com.yangqiongai.agent.harness.core.event.AgentToolCallDeltaEvent;
import com.yangqiongai.agent.harness.core.event.ClarificationAnswer;
import com.yangqiongai.agent.harness.core.event.ConfirmResult;
import com.yangqiongai.agent.harness.core.event.ModelCallInfo;
import com.yangqiongai.agent.harness.core.event.ModelCallResult;
import com.yangqiongai.agent.harness.core.event.RequireUserClarificationEvent;
import com.yangqiongai.agent.harness.core.event.RequireUserConfirmEvent;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptControl;
import com.yangqiongai.agent.harness.core.interruption.AgentInterruptSource;
import com.yangqiongai.agent.harness.core.message.AgentChatUsage;
import com.yangqiongai.agent.harness.core.message.AgentContentBlock;
import com.yangqiongai.agent.harness.core.message.AgentImageBlock;
import com.yangqiongai.agent.harness.core.message.AgentMessage;
import com.yangqiongai.agent.harness.core.message.AgentMessageRole;
import com.yangqiongai.agent.harness.core.message.AgentTextBlock;
import com.yangqiongai.agent.harness.core.message.AgentThinkingBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.message.AgentToolUseBlock;
import com.yangqiongai.agent.harness.core.model.AgentChatResponse;
import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.skill.AgentSkill;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;

import reactor.core.publisher.Flux;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent模型适配器
 * @author yangqiong
 */
public class AgentModelAdapter implements AgentModel {

    /**
     * 框架模型委托
     */
    private final com.yangqiongai.ai.agent.runtime.model.AgentModel delegate;

    /**
     * 模型编码（用于调用级用量记录，未提供时回退类名）
     */
    private final String modelCode;

    public AgentModelAdapter(com.yangqiongai.ai.agent.runtime.model.AgentModel delegate) {
        this(delegate, null);
    }

    public AgentModelAdapter(com.yangqiongai.ai.agent.runtime.model.AgentModel delegate, String modelCode) {
        this.delegate = delegate;
        this.modelCode = modelCode;
    }

    /**
     * 返回真实模型编码，供调用级用量记录归属模型
     * @return
     */
    @Override
    public String modelName() {
        return modelCode != null && !modelCode.isBlank() ? modelCode : AgentModel.super.modelName();
    }

    /**
     * 同步生成模型响应
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> runtimeMessages = SpiConverters.toRuntimeMessages(messages);
        com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions runtimeOptions = SpiConverters.toRuntimeOptions(options);
        com.yangqiongai.ai.agent.runtime.model.AgentChatResponse response = delegate.generate(runtimeMessages, tools, runtimeOptions);
        return SpiConverters.toHarnessResponse(response);
    }

    /**
     * 流式生成模型响应
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    @Override
    public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options) {
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> runtimeMessages = SpiConverters.toRuntimeMessages(messages);
        com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions runtimeOptions = SpiConverters.toRuntimeOptions(options);
        return delegate.stream(runtimeMessages, tools, runtimeOptions)
                .map(SpiConverters::toHarnessResponse);
    }

    /**
     * 获取被包装的框架模型
     * @return
     */
    com.yangqiongai.ai.agent.runtime.model.AgentModel getDelegate() {
        return delegate;
    }
}

/**
 * SPI类型转换器
 * <p>
 * 在框架类型(com.yangqiongai.ai.agent.runtime.*)与独立类型(com.yangqiongai.ai.agent.harness.core.*)之间进行字段级转换。
 * 两套类型系统结构一致但包名不同，且均为不可变构建器/工厂模式（无Jackson注解、无私有空构造器），
 * 故采用显式字段拷贝完成转换。
 * </p>
 * @author yangqiong
 */
final class SpiConverters {

    private static final Logger log = LoggerFactory.getLogger(SpiConverters.class);

    private SpiConverters() {
    }

    /**
     * 枚举按名称转换
     * @param source
     * @param target
     * @return
     */
    static <T extends Enum<T>> T convertEnum(Enum<?> source, Class<T> target) {
        if (source == null) {
            return null;
        }
        try {
            return Enum.valueOf(target, source.name());
        } catch (IllegalArgumentException e) {
            // 目标枚举无同名值时降级返回null，由调用方决定透传或丢弃，避免主链路异常
            log.warn("枚举转换未找到同名值，降级返回null: source={}, target={}", source.name(), target.getSimpleName());
            return null;
        }
    }

    /**
     * 转换内容块列表为独立类型
     * @param blocks
     * @return
     */
    static List<AgentContentBlock> toHarnessBlocks(List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<AgentContentBlock> out = new ArrayList<>(blocks.size());
        for (com.yangqiongai.ai.agent.runtime.message.AgentContentBlock block : blocks) {
            out.add(toHarnessBlock(block));
        }
        return out;
    }

    /**
     * 转换内容块列表为框架类型
     * @param blocks
     * @return
     */
    static List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> toRuntimeBlocks(List<AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> out = new ArrayList<>(blocks.size());
        for (AgentContentBlock block : blocks) {
            out.add(toRuntimeBlock(block));
        }
        return out;
    }

    /**
     * 转换内容块为独立类型
     * @param block
     * @return
     */
    static AgentContentBlock toHarnessBlock(com.yangqiongai.ai.agent.runtime.message.AgentContentBlock block) {
        if (block == null) {
            return null;
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentTextBlock t) {
            return AgentTextBlock.builder().text(t.getText()).build();
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentImageBlock i) {
            return AgentImageBlock.builder()
                    .url(i.getUrl())
                    .base64Data(i.getBase64Data())
                    .mediaType(i.getMediaType())
                    .build();
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock t) {
            return AgentThinkingBlock.builder().thinking(t.getThinking()).build();
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock t) {
            return new AgentToolUseBlock(t.getToolName(), t.getToolUseId(), t.getInput());
        }
        if (block instanceof com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock t) {
            return toHarnessToolResult(t);
        }
        return null;
    }

    /**
     * 转换内容块为框架类型
     * @param block
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.message.AgentContentBlock toRuntimeBlock(AgentContentBlock block) {
        if (block == null) {
            return null;
        }
        if (block instanceof AgentTextBlock t) {
            return com.yangqiongai.ai.agent.runtime.message.AgentTextBlock.builder().text(t.getText()).build();
        }
        if (block instanceof AgentImageBlock i) {
            return com.yangqiongai.ai.agent.runtime.message.AgentImageBlock.builder()
                    .url(i.getUrl())
                    .base64Data(i.getBase64Data())
                    .mediaType(i.getMediaType())
                    .build();
        }
        if (block instanceof AgentThinkingBlock t) {
            return com.yangqiongai.ai.agent.runtime.message.AgentThinkingBlock.builder().thinking(t.getThinking()).build();
        }
        if (block instanceof AgentToolUseBlock t) {
            return new com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock(t.getToolName(), t.getToolUseId(), t.getInput());
        }
        if (block instanceof AgentToolResultBlock t) {
            return toRuntimeToolResult(t);
        }
        return null;
    }

    /**
     * 转换Token用量为独立类型
     * @param usage
     * @return
     */
    static AgentChatUsage toHarnessUsage(com.yangqiongai.ai.agent.runtime.message.AgentChatUsage usage) {
        if (usage == null) {
            return null;
        }
        return new AgentChatUsage(usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 转换Token用量为框架类型
     * @param usage
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.message.AgentChatUsage toRuntimeUsage(AgentChatUsage usage) {
        if (usage == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.message.AgentChatUsage(
                usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
    }

    /**
     * 转换消息为独立类型
     * @param message
     * @return
     */
    static AgentMessage toHarnessMessage(com.yangqiongai.ai.agent.runtime.message.AgentMessage message) {
        if (message == null) {
            return null;
        }
        return AgentMessage.builder()
                .name(message.getName())
                .role(convertEnum(message.getRole(), AgentMessageRole.class))
                .content(toHarnessBlocks(message.getContent()))
                .chatUsage(toHarnessUsage(message.getChatUsage()))
                .latency(message.getLatency())
                .build();
    }

    /**
     * 转换消息为框架类型
     * @param message
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.message.AgentMessage toRuntimeMessage(AgentMessage message) {
        if (message == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentMessage.builder()
                .name(message.getName())
                .role(convertEnum(message.getRole(), com.yangqiongai.ai.agent.runtime.message.AgentMessageRole.class))
                .content(toRuntimeBlocks(message.getContent()))
                .chatUsage(toRuntimeUsage(message.getChatUsage()))
                .latency(message.getLatency())
                .build();
    }

    /**
     * 转换消息列表为独立类型
     * @param messages
     * @return
     */
    static List<AgentMessage> toHarnessMessages(List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<AgentMessage> out = new ArrayList<>(messages.size());
        for (com.yangqiongai.ai.agent.runtime.message.AgentMessage message : messages) {
            out.add(toHarnessMessage(message));
        }
        return out;
    }

    /**
     * 转换消息列表为框架类型
     * @param messages
     * @return
     */
    static List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> toRuntimeMessages(List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> out = new ArrayList<>(messages.size());
        for (AgentMessage message : messages) {
            out.add(toRuntimeMessage(message));
        }
        return out;
    }

    /**
     * 转换对话响应为独立类型
     * @param response
     * @return
     */
    static AgentChatResponse toHarnessResponse(com.yangqiongai.ai.agent.runtime.model.AgentChatResponse response) {
        if (response == null) {
            return null;
        }
        return new AgentChatResponse(toHarnessBlocks(response.getContent()), toHarnessUsage(response.getChatUsage()));
    }

    /**
     * 转换工具结果块为独立类型
     * @param block
     * @return
     */
    static AgentToolResultBlock toHarnessToolResult(com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock block) {
        if (block == null) {
            return null;
        }
        if (block.isError()) {
            return AgentToolResultBlock.error(block.getToolUseId(), block.getTextContent());
        }
        return AgentToolResultBlock.of(block.getToolUseId(), toHarnessBlocks(block.getContent()));
    }

    /**
     * 转换工具结果块为框架类型
     * @param block
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock toRuntimeToolResult(AgentToolResultBlock block) {
        if (block == null) {
            return null;
        }
        if (block.isError()) {
            return com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock.error(block.getToolUseId(), block.getTextContent());
        }
        return com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock.of(block.getToolUseId(), toRuntimeBlocks(block.getContent()));
    }

    /**
     * 转换工具调用块为独立类型
     * @param block
     * @return
     */
    static AgentToolUseBlock toHarnessToolUse(com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock block) {
        if (block == null) {
            return null;
        }
        return new AgentToolUseBlock(block.getToolName(), block.getToolUseId(), block.getInput());
    }

    /**
     * 转换工具调用块为框架类型
     * @param block
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock toRuntimeToolUse(AgentToolUseBlock block) {
        if (block == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock(block.getToolName(), block.getToolUseId(), block.getInput());
    }

    /**
     * 转换工具调用块列表为独立类型
     * @param blocks
     * @return
     */
    static List<AgentToolUseBlock> toHarnessToolUses(List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<AgentToolUseBlock> out = new ArrayList<>(blocks.size());
        for (com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock block : blocks) {
            out.add(toHarnessToolUse(block));
        }
        return out;
    }

    /**
     * 转换工具调用块列表为框架类型
     * @param blocks
     * @return
     */
    static List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> toRuntimeToolUses(List<AgentToolUseBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return List.of();
        }
        List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> out = new ArrayList<>(blocks.size());
        for (AgentToolUseBlock block : blocks) {
            out.add(toRuntimeToolUse(block));
        }
        return out;
    }

    /**
     * 转换工具调用参数为框架类型
     * @param param
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam toRuntimeToolCallParam(AgentToolCallParam param) {
        if (param == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam(param.getInput());
    }

    /**
     * 转换生成选项为框架类型
     * @param options
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions toRuntimeOptions(AgentGenerateOptions options) {
        if (options == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions.builder()
                .temperature(options.getTemperature())
                .maxTokens(options.getMaxTokens())
                .topP(options.getTopP())
                .reasoningEffort(options.getReasoningEffort())
                .thinkingBudget(options.getThinkingBudget())
                .stream(options.isStream() ? Boolean.TRUE : null)
                .responseFormat(toRuntimeResponseFormat(options.getResponseFormat()))
                .toolChoice(convertEnum(options.getToolChoice(), com.yangqiongai.ai.agent.runtime.config.AgentToolChoice.class))
                .build();
    }

    /**
     * 转换运行时上下文为框架类型
     * @param context
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.AgentRuntimeContext toRuntimeContext(AgentRuntimeContext context) {
        if (context == null) {
            return null;
        }
        com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl interruptControl = context.getInterruptControl() == null
                ? null
                : new RuntimeInterruptControl(context.getInterruptControl());
        return com.yangqiongai.ai.agent.runtime.AgentRuntimeContext.builder()
                .sessionId(context.getSessionId())
                .userId(context.getUserId())
                .interruptControl(interruptControl)
                .attributes(context.getAttributes())
                .build();
    }

    /**
     * 转换事件为独立类型
     * @param event
     * @return
     */
    static AgentEvent toHarnessEvent(com.yangqiongai.ai.agent.runtime.event.AgentEvent event) {
        if (event == null) {
            return null;
        }
        if (event instanceof com.yangqiongai.ai.agent.runtime.event.AgentResultEvent r) {
            return new AgentResultEvent(toHarnessMessage(r.getResult()), toHarnessUsage(r.getUsage()));
        }
        if (event instanceof com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent d) {
            return new AgentTextBlockDeltaEvent(d.getDelta());
        }
        if (event instanceof com.yangqiongai.ai.agent.runtime.event.AgentThinkingBlockDeltaEvent d) {
            return new AgentThinkingBlockDeltaEvent(d.getDelta());
        }
        if (event instanceof com.yangqiongai.ai.agent.runtime.event.AgentToolCallDeltaEvent d) {
            return new AgentToolCallDeltaEvent(d.getToolName(), d.getToolUseId(), d.getInput());
        }
        if (event instanceof com.yangqiongai.ai.agent.runtime.event.RequireUserConfirmEvent r) {
            return new RequireUserConfirmEvent(toHarnessToolUses(r.getPendingToolCalls()));
        }
        if (event instanceof com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent c) {
            return new RequireUserClarificationEvent(c.getQuestion(), c.getToolCallId(), c.getOptions());
        }
        if (event.getType() == com.yangqiongai.ai.agent.runtime.event.AgentEventType.CUSTOM) {
            // 引擎无CUSTOM概念，框架侧单特事件不向引擎透传
            return null;
        }
        AgentEventType targetType = convertEnum(event.getType(), AgentEventType.class);
        if (targetType == null) {
            return null;
        }
        return AgentEvent.of(targetType, toHarnessPayload(event.getPayload()), event.getParentAgentPath());
    }

    /**
     * 转换事件为框架类型
     * @param event
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.event.AgentEvent toRuntimeEvent(AgentEvent event) {
        if (event == null) {
            return null;
        }
        if (event instanceof AgentResultEvent r) {
            return new com.yangqiongai.ai.agent.runtime.event.AgentResultEvent(
                    toRuntimeMessage(r.getResult()), toRuntimeUsage(r.getUsage()));
        }
        if (event instanceof AgentTextBlockDeltaEvent d) {
            return new com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent(d.getDelta());
        }
        if (event instanceof AgentThinkingBlockDeltaEvent d) {
            return new com.yangqiongai.ai.agent.runtime.event.AgentThinkingBlockDeltaEvent(d.getDelta());
        }
        if (event instanceof AgentToolCallDeltaEvent d) {
            return new com.yangqiongai.ai.agent.runtime.event.AgentToolCallDeltaEvent(d.getToolName(), d.getToolUseId(), d.getInput());
        }
        if (event instanceof RequireUserConfirmEvent r) {
            return new com.yangqiongai.ai.agent.runtime.event.RequireUserConfirmEvent(toRuntimeToolUses(r.getPendingToolCalls()));
        }
        if (event instanceof RequireUserClarificationEvent c) {
            return new com.yangqiongai.ai.agent.runtime.event.RequireUserClarificationEvent(
                    c.getQuestion(), c.getToolCallId(), c.getOptions());
        }
        com.yangqiongai.ai.agent.runtime.event.AgentEventType canonical =
                convertEnum(event.getType(), com.yangqiongai.ai.agent.runtime.event.AgentEventType.class);
        if (canonical == null) {
            // 引擎新增、框架未跟进的类型降级CUSTOM透传，主链路不中断
            return com.yangqiongai.ai.agent.runtime.event.AgentEvent.custom(
                    event.getType().name(), toRuntimePayload(event.getPayload()));
        }
        return com.yangqiongai.ai.agent.runtime.event.AgentEvent.of(canonical,
                toRuntimePayload(event.getPayload()), event.getParentAgentPath());
    }

    /**
     * 转换事件载荷为独立类型
     * @param payload
     * @return
     */
    private static Object toHarnessPayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof com.yangqiongai.ai.agent.runtime.message.AgentMessage m) {
            return toHarnessMessage(m);
        }
        if (payload instanceof com.yangqiongai.ai.agent.runtime.message.AgentContentBlock b) {
            return toHarnessBlock(b);
        }
        if (payload instanceof com.yangqiongai.ai.agent.runtime.event.ModelCallEndInfo info) {
            return new ModelCallResult(info.getAgentName(), info.getModelName(), info.getIteration(),
                    new AgentChatUsage(info.getInputTokens(), info.getOutputTokens(), info.getTotalTokens()));
        }
        return payload;
    }

    /**
     * 转换事件载荷为框架类型
     * @param payload
     * @return
     */
    private static Object toRuntimePayload(Object payload) {
        if (payload == null) {
            return null;
        }
        if (payload instanceof AgentMessage m) {
            return toRuntimeMessage(m);
        }
        if (payload instanceof AgentContentBlock b) {
            return toRuntimeBlock(b);
        }
        if (payload instanceof ModelCallInfo info) {
            return new com.yangqiongai.ai.agent.runtime.event.ModelCallStartInfo(
                    info.getAgentName(), info.getModelName(), info.getIteration());
        }
        if (payload instanceof ModelCallResult r) {
            AgentChatUsage usage = r.getUsage();
            return new com.yangqiongai.ai.agent.runtime.event.ModelCallEndInfo(
                    r.getAgentName(), r.getModelName(), r.getIteration(),
                    usage != null ? usage.getPromptTokens() : 0,
                    usage != null ? usage.getCompletionTokens() : 0,
                    usage != null ? usage.getTotalTokens() : 0);
        }
        if (payload instanceof List<?> list) {
            return toRuntimePayloadList(list);
        }
        return payload;
    }

    /**
     * 转换集合载荷为框架类型：元素为引擎消息或内容块时逐项转换，其余原样返回
     * @param list
     * @return
     */
    private static Object toRuntimePayloadList(List<?> list) {
        if (list.isEmpty()) {
            return list;
        }
        Object first = list.get(0);
        if (first instanceof AgentMessage) {
            List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> out =
                    new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toRuntimeMessage((AgentMessage) item));
            }
            return out;
        }
        if (first instanceof AgentContentBlock) {
            List<com.yangqiongai.ai.agent.runtime.message.AgentContentBlock> out =
                    new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(toRuntimeBlock((AgentContentBlock) item));
            }
            return out;
        }
        return list;
    }

    /**
     * 转换确认结果为框架类型
     * @param result
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.event.ConfirmResult toRuntimeConfirmResult(ConfirmResult result) {
        if (result == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.event.ConfirmResult(
                result.getToolCallId(), result.getToolName(), result.isApproved(), result.getReason());
    }

    /**
     * 转换技能为独立类型
     * @param skill
     * @return
     */
    static AgentSkill toHarnessSkill(com.yangqiongai.ai.agent.runtime.skill.AgentSkill skill) {
        if (skill == null) {
            return null;
        }
        return new AgentSkill(skill.getName(), skill.getSource(), skill.getDescription(), skill.getSkillContent(), skill.getResources());
    }

    /**
     * 转换技能为框架类型
     * @param skill
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.skill.AgentSkill toRuntimeSkill(AgentSkill skill) {
        if (skill == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.skill.AgentSkill(skill.getName(), skill.getSource(), skill.getDescription(), skill.getSkillContent(), skill.getResources());
    }

    /**
     * 转换响应格式为框架类型
     * @param format
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat toRuntimeResponseFormat(AgentResponseFormat format) {
        if (format == null) {
            return null;
        }
        return new com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat(format.getType(), toRuntimeJsonSchema(format.getJsonSchema()));
    }

    /**
     * 转换JSON Schema为框架类型
     * @param schema
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentJsonSchema toRuntimeJsonSchema(AgentJsonSchema schema) {
        if (schema == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentJsonSchema.builder()
                .name(schema.getName())
                .schema(schema.getSchema())
                .strict(schema.isStrict() ? Boolean.TRUE : null)
                .build();
    }

    /**
     * 转换权限规则为框架类型
     * @param rule
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule toRuntimePermissionRule(AgentPermissionRule rule) {
        if (rule == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule.builder()
                .toolName(rule.getToolName())
                .mode(convertEnum(rule.getMode(), com.yangqiongai.ai.agent.runtime.config.AgentPermissionMode.class))
                .build();
    }

    /**
     * 转换权限上下文状态为框架类型
     * @param state
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentPermissionContextState toRuntimePermissionContextState(AgentPermissionContextState state) {
        if (state == null) {
            return null;
        }
        List<com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule> rules = new ArrayList<>();
        if (state.getRules() != null) {
            for (AgentPermissionRule rule : state.getRules()) {
                rules.add(toRuntimePermissionRule(rule));
            }
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentPermissionContextState.builder()
                .mode(convertEnum(state.getMode(), com.yangqiongai.ai.agent.runtime.config.AgentPermissionMode.class))
                .rules(rules)
                .build();
    }

    /**
     * 转换压缩配置为框架类型
     * @param config
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentCompactionConfig toRuntimeCompactionConfig(AgentCompactionConfig config) {
        if (config == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentCompactionConfig.builder()
                .triggerMessages(config.getTriggerMessages())
                .keepMessages(config.getKeepMessages())
                .build();
    }

    /**
     * 转换工具结果驱逐配置为框架类型
     * @param config
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentToolResultEvictionConfig toRuntimeEvictionConfig(AgentToolResultEvictionConfig config) {
        if (config == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentToolResultEvictionConfig.builder()
                .maxResultChars(config.getMaxResultChars())
                .previewChars(config.getPreviewChars())
                .build();
    }

    /**
     * 转换记忆配置为框架类型
     * @param config
     * @return
     */
    static com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig toRuntimeMemoryConfig(AgentMemoryConfig config) {
        if (config == null) {
            return null;
        }
        return com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig.builder()
                .compactionConfig(toRuntimeCompactionConfig(config.getCompactionConfig()))
                .toolResultEvictionConfig(toRuntimeEvictionConfig(config.getToolResultEvictionConfig()))
                .build();
    }

    /**
     * 框架中断控制适配器，将独立中断控制包装为框架中断控制
     * @author yangqiong
     */
    static final class RuntimeInterruptControl implements com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptControl {

        /**
         * 独立中断控制委托
         */
        private final AgentInterruptControl delegate;

        RuntimeInterruptControl(AgentInterruptControl delegate) {
            this.delegate = delegate;
        }

        /**
         * 触发中断
         * @param source
         * @param message
         */
        @Override
        public void trigger(com.yangqiongai.ai.agent.runtime.interruption.AgentInterruptSource source,
                            com.yangqiongai.ai.agent.runtime.message.AgentMessage message) {
            delegate.trigger(convertEnum(source, AgentInterruptSource.class), toHarnessMessage(message));
        }

        /**
         * 查询是否已被中断
         * @return
         */
        @Override
        public boolean isInterrupted() {
            return delegate.isInterrupted();
        }

        /**
         * 重置中断状态
         */
        @Override
        public void reset() {
            delegate.reset();
        }
    }
}
