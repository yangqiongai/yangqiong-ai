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
package com.yangqiongai.ai.memory.memory.trigger;

import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 自适应摘要触发策略，基于消息速率动态调整触发阈值
 * 对话节奏快（高频）则提前触发，节奏慢（低频）则延后触发，避免短对话频繁触发与长对话触发过晚
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(prefix = "ai.memory.summary.trigger.adaptive", name = "enabled", havingValue = "true")
public class AdaptiveTriggerStrategy implements SummaryTriggerStrategy {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveTriggerStrategy.class);

    private static final int DEFAULT_BASE_THRESHOLD = 12;

    private static final double FAST_PACE_MESSAGES_PER_MINUTE = 5.0;

    private static final double SLOW_PACE_MESSAGES_PER_MINUTE = 1.0;

    private static final double FAST_FACTOR = 0.7;

    private static final double SLOW_FACTOR = 1.5;

    @Value("${ai.memory.summary.trigger.adaptive.base-threshold:${ai.memory.summary.trigger-count:" + DEFAULT_BASE_THRESHOLD + "}}")
    private int baseThreshold;

    @Override
    public String getName() {
        return "adaptive";
    }

    /**
     * 判断是否触发摘要，基于消息速率自适应调整阈值
     * @param session
     * @param messages
     * @return
     */
    @Override
    public boolean shouldTrigger(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        if (session == null || messages == null || messages.isEmpty()) {
            return false;
        }

        long dialogCount = messages.stream()
                .filter(m -> "user".equals(m.getMessageRole()) || "assistant".equals(m.getMessageRole()))
                .count();
        if (dialogCount == 0) {
            return false;
        }

        int currentRound = session.getSummaryRound() != null ? session.getSummaryRound() : 0;
        double effectiveThreshold = computeEffectiveThreshold(session, messages, currentRound);
        long nextTriggerThreshold = Math.round(effectiveThreshold * (currentRound + 1));

        boolean triggered = dialogCount >= nextTriggerThreshold;
        if (triggered) {
            log.info("自适应摘要触发: dialogCount={}, threshold={}, round={}, rate={}",
                    dialogCount, nextTriggerThreshold, currentRound, computeMessageRate(session, messages));
        }
        return triggered;
    }

    /**
     * 根据消息速率计算有效触发阈值
     * @param session
     * @param messages
     * @param currentRound
     * @return
     */
    private double computeEffectiveThreshold(ConversationSessionInfo session, List<ChatMemoryRecord> messages, int currentRound) {
        double base = baseThreshold > 0 ? baseThreshold : DEFAULT_BASE_THRESHOLD;
        double rate = computeMessageRate(session, messages);
        double factor;
        if (rate >= FAST_PACE_MESSAGES_PER_MINUTE) {
            factor = FAST_FACTOR;
        } else if (rate <= SLOW_PACE_MESSAGES_PER_MINUTE) {
            factor = SLOW_FACTOR;
        } else {
            // 线性插值：慢→快对应 1.5 → 0.7
            double t = (rate - SLOW_PACE_MESSAGES_PER_MINUTE)
                    / (FAST_PACE_MESSAGES_PER_MINUTE - SLOW_PACE_MESSAGES_PER_MINUTE);
            factor = SLOW_FACTOR + t * (FAST_FACTOR - SLOW_FACTOR);
        }
        return base * factor;
    }

    /**
     * 计算会话内消息速率（条/分钟）
     * @param session
     * @param messages
     * @return
     */
    private double computeMessageRate(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        LocalDateTime earliest = null;
        LocalDateTime latest = null;
        for (ChatMemoryRecord m : messages) {
            if (m.getCreateTime() == null) {
                continue;
            }
            if (earliest == null || m.getCreateTime().isBefore(earliest)) {
                earliest = m.getCreateTime();
            }
            if (latest == null || m.getCreateTime().isAfter(latest)) {
                latest = m.getCreateTime();
            }
        }
        if (earliest == null || latest == null || earliest.equals(latest)) {
            // 无法计算时间跨度时假设中等速率
            return (FAST_PACE_MESSAGES_PER_MINUTE + SLOW_PACE_MESSAGES_PER_MINUTE) / 2.0;
        }
        long minutes = Math.max(1, Duration.between(earliest, latest).toMinutes());
        long dialogCount = messages.stream()
                .filter(m -> "user".equals(m.getMessageRole()) || "assistant".equals(m.getMessageRole()))
                .count();
        return (double) dialogCount / minutes;
    }
}
