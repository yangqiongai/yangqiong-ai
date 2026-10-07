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
package com.yangqiongai.ai.trigger.repository;

import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent触发规则
 * @author yangqiong
 */
public interface AgentTriggerRepository {

    /**
     * 插入触发规则
     * @param entity
     * @return
     */
    Long insert(AgentTriggerEntity entity);

    /**
     * 更新触发规则
     * @param entity
     */
    void update(AgentTriggerEntity entity);

    /**
     * 根据ID查询
     * @param id
     * @return
     */
    AgentTriggerEntity selectById(Long id);

    /**
     * 根据编码查询
     * @param triggerCode
     * @return
     */
    AgentTriggerEntity findByCode(String triggerCode);

    /**
     * 根据WEBHOOK令牌查询启用规则
     * @param webhookToken
     * @return
     */
    AgentTriggerEntity findEnabledByWebhookToken(String webhookToken);

    /**
     * 根据ID删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 查询指定类型全部启用规则
     * @param triggerType
     * @return
     */
    List<AgentTriggerEntity> findEnabledByType(String triggerType);

    /**
     * 查询指定事件源全部启用规则
     * @param eventSource
     * @return
     */
    List<AgentTriggerEntity> findEnabledByEventSource(String eventSource);

    /**
     * 条件分页查询
     * @param triggerType
     * @param agentCode
     * @param enabled
     * @param offset
     * @param limit
     * @return
     */
    List<AgentTriggerEntity> findPage(String triggerType, String agentCode, Integer enabled, int offset, int limit);

    /**
     * 条件计数
     * @param triggerType
     * @param agentCode
     * @param enabled
     * @return
     */
    long countByCondition(String triggerType, String agentCode, Integer enabled);

    /**
     * 更新最近触发时刻
     * @param id
     * @param lastFireTime
     */
    void updateLastFireTime(Long id, LocalDateTime lastFireTime);
}
