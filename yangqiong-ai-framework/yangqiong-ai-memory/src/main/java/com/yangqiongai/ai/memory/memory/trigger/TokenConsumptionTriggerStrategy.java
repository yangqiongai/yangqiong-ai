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

import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token消耗触发策略，累计Token超过阈值时触发摘要
 * @author yangqiong
 */
@Component
public class TokenConsumptionTriggerStrategy implements SummaryTriggerStrategy {

    private static final int DEFAULT_TOKEN_THRESHOLD = 3000;

    @Value("${ai.memory.summary.trigger.token-threshold:${ai.conversation.summary.trigger.token-threshold:" + DEFAULT_TOKEN_THRESHOLD + "}}")
    private int tokenThreshold;

    @Override
    public String getName() {
        return "token-consumption";
    }

    @Override
    public boolean shouldTrigger(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        long totalTokens = messages.stream()
                .filter(m -> "user".equals(m.getMessageRole()) || "assistant".equals(m.getMessageRole()))
                .mapToLong(this::resolveTokenCount)
                .sum();
        int threshold = tokenThreshold > 0 ? tokenThreshold : DEFAULT_TOKEN_THRESHOLD;
        // 已摘要轮次会降低累计Token的基准，避免每次都触发
        int currentRound = session.getSummaryRound() != null ? session.getSummaryRound() : 0;
        long effectiveThreshold = (long) threshold * (currentRound + 1);
        return totalTokens >= effectiveThreshold;
    }

    /**
     * 解析消息的Token数量，若tokenCount为null则使用TokenEstimator估算
     * @param msg
     * @return
     */
    private int resolveTokenCount(ChatMemoryRecord msg) {
        if (msg.getTokenCount() != null) {
            return msg.getTokenCount();
        }
        return TokenEstimator.estimateTokens(msg.getMessageContent());
    }
}
