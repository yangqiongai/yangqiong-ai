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
package com.yangqiongai.ai.agent.core.stream;

import com.yangqiongai.ai.agent.core.stream.ThinkTagSplitter.ThinkSplitResult;
import com.yangqiongai.ai.common.util.StringUtils;
import com.yangqiongai.ai.agent.core.model.content.OutputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextOutputBlock;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.AgentResultEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentTextBlockDeltaEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentThinkingBlockDeltaEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentToolCallDeltaEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 流式事件收集器，处理AgentScope 2.0的AgentEvent流
 * @author yangqiong
 */
public class StreamingAccumulator {

    private static final Logger log = LoggerFactory.getLogger(StreamingAccumulator.class);

    /**
     * 最大累积长度限制，防止内存溢出
     */
    private static final int MAX_ACCUMULATION_LENGTH = 512 * 1024;

    private final StringBuilder visibleAnswer = new StringBuilder();
    private final StringBuilder reasoningContent = new StringBuilder();
    private final AtomicInteger toolCallCount = new AtomicInteger(0);
    private final long startedAtNanos = System.nanoTime();
    private final ThinkTagSplitter tagSplitter = new ThinkTagSplitter();

    private volatile long firstTokenLatencyMs = -1L;
    private volatile int tokenCount;
    private volatile boolean forwardingFailed = false;
    private volatile AgentMessage finalMessage;

    /**
     * 消费AgentEvent事件
     * @param event
     */
    public void consume(AgentEvent event) {
        if (event == null || event.getType() == null) {
            return;
        }
        AgentEventType type = event.getType();
        if (type == AgentEventType.AGENT_RESULT) {
            processResultEvent(event);
            return;
        }
        if (type == AgentEventType.TEXT_BLOCK_DELTA) {
            processTextDelta(event);
            return;
        }
        if (type == AgentEventType.THINKING_BLOCK_DELTA) {
            processThinkingDelta(event);
            return;
        }
        if (type == AgentEventType.TOOL_CALL_DELTA) {
            processToolCallDelta(event);
        }
    }

    /**
     * 获取可见回答文本
     * @return
     */
    public synchronized String getVisibleAnswer() {
        return visibleAnswer.toString();
    }

    /**
     * 获取推理内容
     * @return
     */
    public synchronized String getReasoningContent() {
        return reasoningContent.toString();
    }

    /**
     * 获取最终消息
     * @return
     */
    public AgentMessage getFinalMessage() {
        return finalMessage;
    }

    /**
     * 获取最终输出的多模态内容块（过滤ThinkingBlock等内部类型）
     * @return
     */
    public List<OutputBlock> getFinalOutputBlocks() {
        AgentMessage msg = finalMessage;
        if (msg == null || msg.getContent() == null) {
            return List.of();
        }
        // TODO ContentBlockConverter迁移至AgentContentBlock后可恢复统一调用
        List<OutputBlock> result = new ArrayList<>();
        for (AgentContentBlock block : msg.getContent()) {
            if (block instanceof AgentTextBlock tb) {
                result.add(new TextOutputBlock(tb.getText()));
            }
        }
        return result;
    }

    /**
     * 获取Token计数
     * @return
     */
    public int getTokenCount() {
        return tokenCount;
    }

    /**
     * 获取首Token延迟毫秒数
     * @return
     */
    public long getFirstTokenLatencyMs() {
        return firstTokenLatencyMs;
    }

    /**
     * 获取工具调用次数
     * @return
     */
    public int getToolCallCount() {
        return toolCallCount.get();
    }

    /**
     * 刷新最终回答，处理标签分割器中的残留内容
     * @param determinedAnswer
     */
    public void flushFinalAnswer(String determinedAnswer) {
        ThinkSplitResult pending = tagSplitter.flush();
        String pendingReasoning = pending.reasoning();
        if (isNotEmpty(pendingReasoning)) {
            appendReasoning(pendingReasoning);
        }
        String pendingVisible = pending.visible();
        if (isNotEmpty(pendingVisible)) {
            appendVisible(pendingVisible);
        }
        if (isEmpty(determinedAnswer)) {
            return;
        }
        String currentVisible;
        synchronized (this) {
            currentVisible = visibleAnswer.toString();
        }
        if (determinedAnswer.equals(currentVisible) || currentVisible.endsWith(determinedAnswer)) {
            return;
        }
        if (currentVisible.isEmpty()) {
            appendVisible(determinedAnswer);
            return;
        }
        if (determinedAnswer.startsWith(currentVisible)) {
            String remainder = determinedAnswer.substring(currentVisible.length());
            if (isNotEmpty(remainder)) {
                appendVisible(remainder);
            }
            return;
        }
        log.warn("流式回答与最终消息不一致，无法增量刷新: streamedVisibleLength={}, finalLength={}",
                currentVisible.length(), determinedAnswer.length());
    }

    /**
     * 解析流式回答冲突，优先选择完整版本
     * @param finalMessage
     * @param streamedAnswer
     * @return
     */
    public static String determineStreamAnswer(AgentMessage finalMessage, String streamedAnswer) {
        String finalAnswer = stripThinkingContent(finalMessage == null ? null : finalMessage.getTextContent());
        if (isEmpty(finalAnswer)) {
            return StringUtils.getOrDefault(streamedAnswer);
        }
        if (isEmpty(streamedAnswer)) {
            return finalAnswer;
        }
        if (finalAnswer.equals(streamedAnswer)) {
            return finalAnswer;
        }
        if (finalAnswer.startsWith(streamedAnswer)) {
            log.debug("最终回答扩展了流式回答: streamedLength={}, finalLength={}",
                    streamedAnswer.length(), finalAnswer.length());
            return finalAnswer;
        }
        log.warn("流式回答与最终消息不一致，优先使用最终消息: streamedLength={}, finalLength={}",
                streamedAnswer.length(), finalAnswer.length());
        return finalAnswer;
    }

    private void processResultEvent(AgentEvent event) {
        if (event instanceof AgentResultEvent resultEvent) {
            finalMessage = resultEvent.getResult();
        }
    }

    private void processTextDelta(AgentEvent event) {
        if (!(event instanceof AgentTextBlockDeltaEvent textEvent)) {
            return;
        }
        String delta = textEvent.getDelta();
        if (isEmpty(delta)) {
            return;
        }
        ThinkSplitResult split = tagSplitter.splitChunk(delta);
        if (isNotEmpty(split.reasoning())) {
            appendReasoning(split.reasoning());
        }
        if (isEmpty(split.visible())) {
            return;
        }
        appendVisible(split.visible());
    }

    private void processThinkingDelta(AgentEvent event) {
        if (!(event instanceof AgentThinkingBlockDeltaEvent thinkingEvent)) {
            return;
        }
        String delta = thinkingEvent.getDelta();
        if (isNotEmpty(delta)) {
            appendReasoning(delta);
        }
    }

    private void processToolCallDelta(AgentEvent event) {
        if (event instanceof AgentToolCallDeltaEvent) {
            toolCallCount.incrementAndGet();
        }
    }

    private void appendVisible(String text) {
        if (isEmpty(text) || forwardingFailed) {
            return;
        }
        synchronized (this) {
            if (visibleAnswer.length() + text.length() > MAX_ACCUMULATION_LENGTH) {
                log.warn("流式累积长度超限，截断: currentLength={}, deltaLength={}, maxLimit={}",
                        visibleAnswer.length(), text.length(), MAX_ACCUMULATION_LENGTH);
                int allowable = MAX_ACCUMULATION_LENGTH - visibleAnswer.length();
                if (allowable > 0) {
                    visibleAnswer.append(text, 0, allowable);
                }
                return;
            }
            visibleAnswer.append(text);
            tokenCount++;
            if (firstTokenLatencyMs < 0) {
                firstTokenLatencyMs = Duration.ofNanos(System.nanoTime() - startedAtNanos).toMillis();
                log.info("首Token到达: latencyMs={}, length={}, count={}",
                        firstTokenLatencyMs, text.length(), tokenCount);
            }
        }
    }

    private void appendReasoning(String text) {
        if (isEmpty(text)) {
            return;
        }
        synchronized (this) {
            reasoningContent.append(text);
        }
    }

    private static String stripThinkingContent(String text) {
        if (isEmpty(text)) {
            return "";
        }
        return ThinkTagSplitter.split(text).visible();
    }

    private static boolean isEmpty(String value) {
        return StringUtils.isEmpty(value);
    }

    private static boolean isNotEmpty(String value) {
        return StringUtils.isNotEmpty(value);
    }
}
