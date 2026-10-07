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

import com.yangqiongai.ai.trigger.entity.AgentTriggerLogEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent触发记录
 * @author yangqiong
 */
public interface AgentTriggerLogRepository {

    /**
     * 插入触发记录
     * @param entity
     * @return
     */
    Long insert(AgentTriggerLogEntity entity);

    /**
     * 查询指定规则最近一条同幂等键记录
     * @param triggerId
     * @param dedupKey
     * @return
     */
    AgentTriggerLogEntity findLastByDedupKey(Long triggerId, String dedupKey);

    /**
     * 查询指定规则最近一条触发记录（去重窗口判断）
     * @param triggerId
     * @return
     */
    AgentTriggerLogEntity findLastByTrigger(Long triggerId);

    /**
     * 统计指定规则当日触发条数
     * @param triggerId
     * @param dayStart
     * @param dayEnd
     * @return
     */
    long countByTriggerAndTimeRange(Long triggerId, LocalDateTime dayStart, LocalDateTime dayEnd);

    /**
     * 按规则分页查询触发记录
     * @param triggerId
     * @param offset
     * @param limit
     * @return
     */
    List<AgentTriggerLogEntity> findByTrigger(Long triggerId, int offset, int limit);

    /**
     * 按规则统计触发条数
     * @param triggerId
     * @return
     */
    long countByTrigger(Long triggerId);
}
