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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 消息数量触发策略，迁移现有基于消息条数的触发逻辑
 * @author yangqiong
 */
@Component
public class MessageCountTriggerStrategy implements SummaryTriggerStrategy {

    private static final int DEFAULT_SUMMARY_TRIGGER_COUNT = 12;

    @Value("${ai.memory.summary.trigger-count:${ai.conversation.summary.trigger-count:" + DEFAULT_SUMMARY_TRIGGER_COUNT + "}}")
    private int summaryTriggerCount;

    @Override
    public String getName() {
        return "message-count";
    }

    @Override
    public boolean shouldTrigger(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        long dialogCount = messages.stream()
                .filter(m -> "user".equals(m.getMessageRole()) || "assistant".equals(m.getMessageRole()))
                .count();
        int triggerCount = summaryTriggerCount > 0 ? summaryTriggerCount : DEFAULT_SUMMARY_TRIGGER_COUNT;
        int currentRound = session.getSummaryRound() != null ? session.getSummaryRound() : 0;
        long nextTriggerThreshold = (long) (currentRound + 1) * triggerCount;
        return dialogCount >= nextTriggerThreshold;
    }
}
