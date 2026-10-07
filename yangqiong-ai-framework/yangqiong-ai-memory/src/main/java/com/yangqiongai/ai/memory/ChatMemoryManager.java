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
package com.yangqiongai.ai.memory;

import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.MemoryPageResult;
import com.yangqiongai.ai.memory.repository.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 对话记忆管理
 * @author yangqiong
 */
@Service
public class ChatMemoryManager {

    @Autowired(required = false)
    private ChatMemoryRepository chatMemoryRepository;

    /**
     * 保存消息
     * @param chatMemory
     * @return
     */
    public ChatMemoryRecord saveMessage(ChatMemoryRecord chatMemory) {
        return chatMemoryRepository.saveMessage(chatMemory);
    }

    /**
     * 根据会话ID查找消息列表
     * @param sessionId
     * @return
     */
    public List<ChatMemoryRecord> findBySessionId(String sessionId) {
        return chatMemoryRepository.findBySessionId(sessionId);
    }

    /**
     * 根据会话ID删除消息
     * @param sessionId
     */
    public void deleteBySessionId(String sessionId) {
        chatMemoryRepository.deleteBySessionId(sessionId);
    }

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
    public MemoryPageResult findPage(String sessionId, String userId, String messageRole,
                                     String scopeId, String keyword, int page, int size) {
        return chatMemoryRepository.findPage(sessionId, userId, messageRole, scopeId, keyword, page, size);
    }

    /**
     * 关键字检索消息内容
     * @param keyword
     * @param scopeId
     * @param limit
     * @return
     */
    public List<ChatMemoryRecord> search(String keyword, String scopeId, int limit) {
        return chatMemoryRepository.search(keyword, scopeId, limit);
    }

    /**
     * 根据主键查询
     * @param id
     * @return
     */
    public ChatMemoryRecord findById(Long id) {
        return chatMemoryRepository.findById(id);
    }

    /**
     * 根据主键删除
     * @param id
     */
    public void deleteById(Long id) {
        chatMemoryRepository.deleteById(id);
    }

    /**
     * 按维度批量删除
     * @param sessionId
     * @param userId
     * @param scopeId
     * @return
     */
    public int deleteBatch(String sessionId, String userId, String scopeId) {
        return chatMemoryRepository.deleteBatch(sessionId, userId, scopeId);
    }

    /**
     * 按消息角色分组统计
     * @param scopeId
     * @return
     */
    public List<Map<String, Object>> statsByRole(String scopeId) {
        return chatMemoryRepository.statsByRole(scopeId);
    }
}

