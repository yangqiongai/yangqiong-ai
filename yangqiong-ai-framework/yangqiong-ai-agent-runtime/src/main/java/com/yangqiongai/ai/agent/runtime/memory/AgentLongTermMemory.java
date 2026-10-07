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
package com.yangqiongai.ai.agent.runtime.memory;

import java.util.List;
import java.util.Map;

/**
 * Agent长期记忆
 * @author yangqiong
 */
public interface AgentLongTermMemory {

    /**
     * 存储记忆
     * @param userId
     * @param sessionId
     * @param content
     * @param metadata
     * @return
     */
    void store(String userId, String sessionId, String content, Map<String, Object> metadata);

    /**
     * 检索记忆
     * @param userId
     * @param query
     * @param limit
     * @return
     */
    List<String> search(String userId, String query, int limit);

    /**
     * 删除记忆
     * @param memoryId
     * @return
     */
    void delete(String memoryId);
}
