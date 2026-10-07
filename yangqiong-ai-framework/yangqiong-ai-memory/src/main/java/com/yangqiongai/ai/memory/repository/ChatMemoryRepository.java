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
package com.yangqiongai.ai.memory.repository;

import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.MemoryPageResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 对话记忆
 * @author yangqiong
 */
public interface ChatMemoryRepository {

    /**
     * 保存消息
     * @param record
     * @return
     */
    ChatMemoryRecord saveMessage(ChatMemoryRecord record);

    /**
     * 根据会话ID查找消息列表
     * @param sessionId
     * @return
     */
    List<ChatMemoryRecord> findBySessionId(String sessionId);

    /**
     * 根据会话ID删除消息
     * @param sessionId
     */
    void deleteBySessionId(String sessionId);

    /**
     * 统计用户消息数
     * @param userId
     * @return
     */
    long countByUserId(String userId);

    /**
     * 统计用户总Token用量（优先使用真实Token，无真实值时降级为估算值）
     * @param userId
     * @return
     */
    long sumTokensByUserId(String userId);

    /**
     * 统计用户指定时间之后的Token用量（优先使用真实Token，无真实值时降级为估算值）
     * @param userId
     * @param startTime
     * @return
     */
    long sumTokensByUserIdAndCreateTimeAfter(String userId, LocalDateTime startTime);

    /**
     * 多条件分页查询消息
     * @param sessionId
     * @param userId
     * @param messageRole
     * @param scopeId
     * @param keyword
     * @param page
     * @param size
     * @return
     */
    MemoryPageResult findPage(String sessionId, String userId, String messageRole,
                              String scopeId, String keyword, int page, int size);

    /**
     * 关键字检索消息内容
     * @param keyword
     * @param scopeId
     * @param limit
     * @return
     */
    List<ChatMemoryRecord> search(String keyword, String scopeId, int limit);

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    ChatMemoryRecord findById(Long id);

    /**
     * 根据主键删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 按维度批量删除
     * @param sessionId
     * @param userId
     * @param scopeId
     * @return
     */
    int deleteBatch(String sessionId, String userId, String scopeId);

    /**
     * 按消息角色分组统计
     * @param scopeId
     * @return
     */
    List<Map<String, Object>> statsByRole(String scopeId);
}

