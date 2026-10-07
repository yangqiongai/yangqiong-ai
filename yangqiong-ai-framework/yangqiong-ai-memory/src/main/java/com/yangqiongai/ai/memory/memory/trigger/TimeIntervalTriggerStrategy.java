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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 时间间隔触发策略，距上次会话更新超过配置时长时触发摘要
 * @author yangqiong
 */
@Component
public class TimeIntervalTriggerStrategy implements SummaryTriggerStrategy {

    private static final long DEFAULT_INTERVAL_MINUTES = 30;

    @Value("${ai.memory.summary.trigger.time-interval-minutes:${ai.conversation.summary.trigger.time-interval-minutes:" + DEFAULT_INTERVAL_MINUTES + "}}")
    private long intervalMinutes;

    @Override
    public String getName() {
        return "time-interval";
    }

    @Override
    public boolean shouldTrigger(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        if (session == null || session.getUpdateTime() == null) {
            return false;
        }
        // 仅当存在对话消息时才考虑时间触发
        boolean hasDialog = messages.stream()
                .anyMatch(m -> "user".equals(m.getMessageRole()) || "assistant".equals(m.getMessageRole()));
        if (!hasDialog) {
            return false;
        }
        long interval = intervalMinutes > 0 ? intervalMinutes : DEFAULT_INTERVAL_MINUTES;
        long elapsedMinutes = Duration.between(session.getUpdateTime(), LocalDateTime.now()).toMinutes();
        return elapsedMinutes >= interval;
    }
}
