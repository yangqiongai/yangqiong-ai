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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.trigger.entity.AgentTriggerLogEntity;
import com.yangqiongai.ai.trigger.mapper.AgentTriggerLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent触发记录
 * @author yangqiong
 */
@Repository
public class DefaultAgentTriggerLogRepository implements AgentTriggerLogRepository {

    @Autowired
    private AgentTriggerLogMapper agentTriggerLogMapper;

    @Override
    public Long insert(AgentTriggerLogEntity entity) {
        agentTriggerLogMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public AgentTriggerLogEntity findLastByDedupKey(Long triggerId, String dedupKey) {
        LambdaQueryWrapper<AgentTriggerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerLogEntity::getTriggerId, triggerId)
                .eq(AgentTriggerLogEntity::getDedupKey, dedupKey)
                .orderByDesc(AgentTriggerLogEntity::getId)
                .last("LIMIT 1");
        return agentTriggerLogMapper.selectOne(wrapper);
    }

    @Override
    public AgentTriggerLogEntity findLastByTrigger(Long triggerId) {
        LambdaQueryWrapper<AgentTriggerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerLogEntity::getTriggerId, triggerId)
                .orderByDesc(AgentTriggerLogEntity::getId)
                .last("LIMIT 1");
        return agentTriggerLogMapper.selectOne(wrapper);
    }

    @Override
    public long countByTriggerAndTimeRange(Long triggerId, LocalDateTime dayStart, LocalDateTime dayEnd) {
        LambdaQueryWrapper<AgentTriggerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerLogEntity::getTriggerId, triggerId)
                .ge(AgentTriggerLogEntity::getCreateTime, dayStart)
                .lt(AgentTriggerLogEntity::getCreateTime, dayEnd);
        return agentTriggerLogMapper.selectCount(wrapper);
    }

    @Override
    public List<AgentTriggerLogEntity> findByTrigger(Long triggerId, int offset, int limit) {
        LambdaQueryWrapper<AgentTriggerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerLogEntity::getTriggerId, triggerId)
                .orderByDesc(AgentTriggerLogEntity::getId)
                .last("LIMIT " + offset + "," + limit);
        return agentTriggerLogMapper.selectList(wrapper);
    }

    @Override
    public long countByTrigger(Long triggerId) {
        LambdaQueryWrapper<AgentTriggerLogEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerLogEntity::getTriggerId, triggerId);
        return agentTriggerLogMapper.selectCount(wrapper);
    }
}
