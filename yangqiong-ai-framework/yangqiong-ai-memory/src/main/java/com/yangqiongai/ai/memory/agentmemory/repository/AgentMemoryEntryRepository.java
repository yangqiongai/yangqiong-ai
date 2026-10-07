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
package com.yangqiongai.ai.memory.agentmemory.repository;

import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent运行记忆条目
 * @author yangqiong
 */
public interface AgentMemoryEntryRepository {

    /**
     * 插入记忆条目
     * @param entry
     * @return
     */
    Long insert(AgentMemoryEntryInfo entry);

    /**
     * 根据ID查询
     * @param id
     * @return
     */
    AgentMemoryEntryInfo selectById(Long id);

    /**
     * 查询同锚点同类型的生效记忆（去重判断）
     * @param agentCode
     * @param userAnchor
     * @param inputHash
     * @return
     */
    AgentMemoryEntryInfo findActiveByInputHash(String agentCode, String userAnchor, String inputHash);

    /**
     * 查询指定锚点的生效记忆（降级检索兜底，按置信度降序）
     * @param agentCode
     * @param userAnchor
     * @param limit
     * @return
     */
    List<AgentMemoryEntryInfo> findActiveByAgentAndUser(String agentCode, String userAnchor, int limit);

    /**
     * 按ID列表批量查询生效记忆
     * @param ids
     * @return
     */
    List<AgentMemoryEntryInfo> findByIds(List<Long> ids);

    /**
     * 查询TTL已过期的生效记忆（治理扫描）
     * @param now
     * @param limit
     * @return
     */
    List<AgentMemoryEntryInfo> findExpiredTtl(LocalDateTime now, int limit);

    /**
     * 分页扫描指定状态的条目（keyset分页）
     * @param status
     * @param lastId
     * @param limit
     * @return
     */
    List<AgentMemoryEntryInfo> scanByStatus(String status, Long lastId, int limit);

    /**
     * 更新状态
     * @param id
     * @param status
     */
    void updateStatus(Long id, String status);

    /**
     * 更新置信度
     * @param id
     * @param confidence
     */
    void updateConfidence(Long id, Double confidence);

    /**
     * 更新内容并递增版本号
     * @param id
     * @param content
     */
    void updateContent(Long id, String content);

    /**
     * 批量记录访问信息
     * @param ids
     */
    void batchUpdateAccessInfo(List<Long> ids);

    /**
     * 按用户锚点查询全部条目（擦除前收集向量ID用）
     * @param userAnchor
     * @return
     */
    List<AgentMemoryEntryInfo> findByUserAnchor(String userAnchor);

    /**
     * 按Agent编码查询全部条目
     * @param agentCode
     * @return
     */
    List<AgentMemoryEntryInfo> findByAgentCode(String agentCode);

    /**
     * 按用户锚点删除
     * @param userAnchor
     * @return
     */
    int deleteByUserAnchor(String userAnchor);

    /**
     * 按Agent编码删除
     * @param agentCode
     * @return
     */
    int deleteByAgentCode(String agentCode);

    /**
     * 按ID删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 条件分页查询（管理端）
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @param status
     * @param offset
     * @param limit
     * @return
     */
    List<AgentMemoryEntryInfo> findPage(String agentCode, String userAnchor, String memoryType,
                                    String status, int offset, int limit);

    /**
     * 条件计数
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @param status
     * @return
     */
    long countByCondition(String agentCode, String userAnchor, String memoryType, String status);

    /**
     * 按状态计数
     * @param status
     * @return
     */
    long countByStatus(String status);
}
