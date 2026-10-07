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

import com.yangqiongai.ai.memory.model.UserLongTermMemoryInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户长期记忆
 * @author yangqiong
 */
public interface UserLongTermMemoryRepository {

    /**
     * 插入记忆
     * @param memory
     */
    void insert(UserLongTermMemoryInfo memory);

    /**
     * 根据ID查询
     * @param id
     * @return
     */
    UserLongTermMemoryInfo selectById(Long id);

    /**
     * 根据ID删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 按条件删除
     * @param userId
     * @return
     */
    int deleteByUserId(String userId);

    /**
     * 查询有效记忆列表（非DEPRECATED且未过期）
     * @param userId
     * @return
     */
    List<UserLongTermMemoryInfo> findEffectiveMemories(String userId);

    /**
     * 按ID列表批量查询有效记忆
     * @param ids
     * @return
     */
    List<UserLongTermMemoryInfo> findByIdsEffective(List<Long> ids);

    /**
     * 查询用户最新的SUMMARY类型记忆
     * @param userId
     * @return
     */
    UserLongTermMemoryInfo findLatestSummaryMemory(String userId);

    /**
     * 更新记忆内容
     * @param id
     * @param content
     * @param relatedSessionIds
     */
    void updateContentAndRelatedIds(Long id, String content, String relatedSessionIds);

    /**
     * 更新记忆内容、评分
     * @param id
     * @param content
     * @param importanceScore
     * @param baseScore
     */
    void updateContentAndScore(Long id, String content, Integer importanceScore, Integer baseScore);

    /**
     * 更新记忆内容、评分、来源
     * @param id
     * @param content
     * @param importanceScore
     * @param baseScore
     * @param source
     */
    void updateContentScoreAndSource(Long id, String content, Integer importanceScore, Integer baseScore, String source);

    /**
     * 标记记忆失效
     * @param memoryId
     */
    void markExpired(Long memoryId);

    /**
     * 查询用户关键词匹配记忆列表
     * @param userId
     * @return
     */
    List<UserLongTermMemoryInfo> findMemoriesForKeywordMatch(String userId);

    /**
     * 查询用户指定类型的有效偏好记忆
     * @param userId
     * @param memoryType
     * @param tags
     * @return
     */
    List<UserLongTermMemoryInfo> findActiveMemoriesByTypeAndTags(String userId, String memoryType, String tags);

    /**
     * 批量更新访问信息
     * @param ids
     */
    void batchUpdateAccessInfo(List<Long> ids);

    /**
     * 衰减服务：更新评分
     * @param id
     * @param importanceScore
     */
    void updateImportanceScore(Long id, Integer importanceScore);

    /**
     * 衰减服务：分页扫描非DEPRECATED且baseScore非空的记忆（keyset分页）
     * @param lastId
     * @param limit
     * @return
     */
    List<UserLongTermMemoryInfo> scanNonDeprecatedByPage(Long lastId, int limit);

    /**
     * 衰减服务：批量将指定ID的记忆标记为DEPRECATED
     * @param memoryIds
     */
    void batchMarkAsDeprecated(List<Long> memoryIds);

    /**
     * 调度服务：按ID列表和标签过滤有效记忆
     * @param ids
     * @param tagsList
     * @return
     */
    List<UserLongTermMemoryInfo> findByIdsAndTags(List<Long> ids, List<String> tagsList);

    /**
     * 整理代理：查询所有有非DEPRECATED记忆的用户ID
     * @return
     */
    List<String> findAllActiveUserIds();

    /**
     * 整理代理：查询用户非DEPRECATED且未过期的记忆（按updateTime升序，限制数量）
     * @param userId
     * @param limit
     * @return
     */
    List<UserLongTermMemoryInfo> findUserMemoriesForOrganize(String userId, int limit);

    /**
     * 整理代理：归档记忆（标记为DEPRECATED并设置validUntil=now）
     * @param memoryId
     */
    void archiveAsDeprecated(Long memoryId);

    /**
     * 整理代理：更新记忆内容
     * @param id
     * @param content
     */
    void updateContent(Long id, String content);

    /**
     * 统计用户长期记忆条数
     * @param userId
     * @return
     */
    long countByUserId(String userId);
}
