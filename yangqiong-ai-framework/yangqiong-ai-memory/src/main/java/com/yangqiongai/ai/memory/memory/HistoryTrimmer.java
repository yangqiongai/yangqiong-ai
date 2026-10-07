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
package com.yangqiongai.ai.memory.memory;

import com.yangqiongai.ai.common.util.PreciseTokenEstimator;
import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.metrics.ConversationMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话历史裁剪
 * @author yangqiong
 */
@Service
public class HistoryTrimmer {

    private static final Logger log = LoggerFactory.getLogger(HistoryTrimmer.class);

    /**
     * 摘要消息的Token估算系数
     */
    private static final double SUMMARY_TOKEN_FACTOR = 1.2;

    @Autowired
    private ConversationMetrics conversationMetrics;

    /**
     * 裁剪策略，可选值：time-order（默认）、importance-first
     */
    @Value("${ai.memory.trim.strategy:${ai.conversation.trim.strategy:time-order}}")
    private String trimStrategy;

    /**
     * Token估算器类型，可选值：fast（默认快速估算）、precise（jtokkit精确估算）
     */
    @Value("${ai.memory.token.estimator:${ai.conversation.token.estimator:fast}}")
    private String tokenEstimator;

    /**
     * 精确Token估算器，jtokkit不可用时为null
     */
    @Autowired(required = false)
    private PreciseTokenEstimator preciseTokenEstimator;

    /**
     * 主裁剪方法，按Token预算裁剪历史，优先保留摘要和近期消息
     * @param messages
     * @param maxTokens
     * @param summary
     * @return
     */
    public List<ChatMemoryRecord> trim(List<ChatMemoryRecord> messages, int maxTokens, String summary) {
        if (messages == null || messages.isEmpty()) {
            return buildSummaryOnlyList(summary);
        }

        // 分离系统消息和普通消息
        List<ChatMemoryRecord> systemMessages = new ArrayList<>();
        List<ChatMemoryRecord> regularMessages = new ArrayList<>();
        for (ChatMemoryRecord msg : messages) {
            if ("system".equals(msg.getMessageRole())) {
                systemMessages.add(msg);
            } else {
                regularMessages.add(msg);
            }
        }

        // 计算系统消息占用Token
        int systemTokens = sumTokens(systemMessages);

        // 计算摘要占用Token
        int summaryTokens = 0;
        if (summary != null && !summary.isBlank()) {
            summaryTokens = (int) Math.ceil(estimateTokens(summary) * SUMMARY_TOKEN_FACTOR);
        }

        int remainingBudget = maxTokens - systemTokens - summaryTokens;
        if (remainingBudget <= 0) {
            log.warn("系统消息和摘要已超出Token预算, systemTokens={}, summaryTokens={}, maxTokens={}",
                    systemTokens, summaryTokens, maxTokens);
            List<ChatMemoryRecord> result = new ArrayList<>(systemMessages);
            addSummaryMemory(result, summary);
            return result;
        }

        // 根据裁剪策略保留消息
        List<ChatMemoryRecord> keptRegular;
        int usedTokens;
        if ("importance-first".equalsIgnoreCase(trimStrategy)) {
            keptRegular = selectByImportance(regularMessages, remainingBudget);
        } else {
            keptRegular = selectByTimeOrder(regularMessages, remainingBudget);
        }
        usedTokens = sumTokens(keptRegular);

        // 合并结果
        List<ChatMemoryRecord> result = new ArrayList<>(systemMessages);
        addSummaryMemory(result, summary);
        result.addAll(keptRegular);

        int totalUsedTokens = systemTokens + summaryTokens + usedTokens;
        conversationMetrics.recordTrimMessagesKept(result.size());
        conversationMetrics.recordTrimTokenUsage(totalUsedTokens);

        log.debug("历史裁剪完成, 策略={}, 原始消息数={}, 裁剪后消息数={}, 使用Token={}",
                trimStrategy, messages.size(), result.size(), totalUsedTokens);
        return result;
    }

    /**
     * 时间顺序裁剪：从最新消息开始保留，超出预算的旧消息丢弃
     * @param regularMessages
     * @param remainingBudget
     * @return
     */
    private List<ChatMemoryRecord> selectByTimeOrder(List<ChatMemoryRecord> regularMessages, int remainingBudget) {
        List<ChatMemoryRecord> kept = new ArrayList<>();
        int usedTokens = 0;
        for (int i = regularMessages.size() - 1; i >= 0; i--) {
            ChatMemoryRecord msg = regularMessages.get(i);
            int msgTokens = resolveTokenCount(msg);
            if (usedTokens + msgTokens > remainingBudget) {
                break;
            }
            kept.add(0, msg);
            usedTokens += msgTokens;
        }
        return kept;
    }

    /**
     * 重要性优先裁剪：在Token预算内优先保留高重要性消息，同等重要性按时间降序，结果按时间正序输出
     * @param regularMessages
     * @param remainingBudget
     * @return
     */
    private List<ChatMemoryRecord> selectByImportance(List<ChatMemoryRecord> regularMessages, int remainingBudget) {
        List<ChatMemoryRecord> sorted = regularMessages.stream()
                .sorted(Comparator
                        .comparingInt((ChatMemoryRecord m) -> m.getImportanceScore() == null ? 0 : m.getImportanceScore()).reversed()
                        .thenComparing(Comparator.comparingInt(regularMessages::indexOf).reversed()))
                .collect(Collectors.toList());
        List<ChatMemoryRecord> kept = new ArrayList<>();
        int usedTokens = 0;
        for (ChatMemoryRecord msg : sorted) {
            int msgTokens = resolveTokenCount(msg);
            if (usedTokens + msgTokens > remainingBudget) {
                continue;
            }
            kept.add(msg);
            usedTokens += msgTokens;
        }
        kept.sort(Comparator.comparingInt(regularMessages::indexOf));
        return kept;
    }

    /**
     * 按Token预算裁剪历史（无摘要）
     * @param history
     * @param currentUserMessage
     * @param tokenBudget
     * @return
     */
    public List<ChatMemoryRecord> trimByTokenBudget(List<ChatMemoryRecord> history, String currentUserMessage, int tokenBudget) {
        return trim(history, tokenBudget, null);
    }

    /**
     * 构建仅含摘要的消息列表
     * @param summary
     * @return
     */
    private List<ChatMemoryRecord> buildSummaryOnlyList(String summary) {
        List<ChatMemoryRecord> result = new ArrayList<>();
        addSummaryMemory(result, summary);
        return result;
    }

    /**
     * 将摘要作为系统消息添加到列表头部
     * @param list
     * @param summary
     */
    private void addSummaryMemory(List<ChatMemoryRecord> list, String summary) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        ChatMemoryRecord summaryMemory = new ChatMemoryRecord();
        summaryMemory.setMessageRole("system");
        summaryMemory.setMessageContent("Summary from previous conversation:\n" + summary);
        summaryMemory.setTokenCount(estimateTokens(summary));
        list.add(0, summaryMemory);
    }

    /**
     * 计算消息列表的Token总和
     * @param messages
     * @return
     */
    private int sumTokens(List<ChatMemoryRecord> messages) {
        int total = 0;
        for (ChatMemoryRecord msg : messages) {
            total += resolveTokenCount(msg);
        }
        return total;
    }

    /**
     * 解析消息的Token数量，若tokenCount为null则使用估算器估算
     * @param msg
     * @return
     */
    private int resolveTokenCount(ChatMemoryRecord msg) {
        if (msg.getTokenCount() != null) {
            return msg.getTokenCount();
        }
        return estimateTokens(msg.getMessageContent());
    }

    /**
     * 估算文本的token数量，根据配置选择快速估算或精确估算
     * @param text
     * @return
     */
    private int estimateTokens(String text) {
        if ("precise".equalsIgnoreCase(tokenEstimator) && preciseTokenEstimator != null) {
            return preciseTokenEstimator.estimateTokens(text);
        }
        return TokenEstimator.estimateTokens(text);
    }
}
