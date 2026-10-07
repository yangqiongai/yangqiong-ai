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

import java.util.List;

/**
 * 摘要触发策略
 * @author yangqiong
 */
public interface SummaryTriggerStrategy {

    /**
     * 获取策略名称，用于配置启用的策略
     * @return
     */
    String getName();

    /**
     * 判断是否触发摘要
     * @param session
     * @param messages
     * @return
     */
    boolean shouldTrigger(ConversationSessionInfo session, List<ChatMemoryRecord> messages);
}
