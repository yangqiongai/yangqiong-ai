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
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.mapper.AgentTriggerMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent触发规则
 * @author yangqiong
 */
@Repository
public class DefaultAgentTriggerRepository implements AgentTriggerRepository {

    @Autowired
    private AgentTriggerMapper agentTriggerMapper;

    @Override
    public Long insert(AgentTriggerEntity entity) {
        agentTriggerMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(AgentTriggerEntity entity) {
        agentTriggerMapper.updateById(entity);
    }

    @Override
    public AgentTriggerEntity selectById(Long id) {
        return agentTriggerMapper.selectById(id);
    }

    @Override
    public AgentTriggerEntity findByCode(String triggerCode) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerEntity::getTriggerCode, triggerCode);
        return agentTriggerMapper.selectOne(wrapper);
    }

    @Override
    public AgentTriggerEntity findEnabledByWebhookToken(String webhookToken) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerEntity::getTriggerType, AgentTriggerEntity.TYPE_WEBHOOK)
                .eq(AgentTriggerEntity::getWebhookToken, webhookToken)
                .eq(AgentTriggerEntity::getEnabled, 1);
        return agentTriggerMapper.selectOne(wrapper);
    }

    @Override
    public void deleteById(Long id) {
        agentTriggerMapper.deleteById(id);
    }

    @Override
    public List<AgentTriggerEntity> findEnabledByType(String triggerType) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerEntity::getTriggerType, triggerType)
                .eq(AgentTriggerEntity::getEnabled, 1)
                .orderByAsc(AgentTriggerEntity::getId);
        return agentTriggerMapper.selectList(wrapper);
    }

    @Override
    public List<AgentTriggerEntity> findEnabledByEventSource(String eventSource) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AgentTriggerEntity::getTriggerType, AgentTriggerEntity.TYPE_EVENT)
                .eq(AgentTriggerEntity::getEventSource, eventSource)
                .eq(AgentTriggerEntity::getEnabled, 1)
                .orderByAsc(AgentTriggerEntity::getId);
        return agentTriggerMapper.selectList(wrapper);
    }

    @Override
    public List<AgentTriggerEntity> findPage(String triggerType, String agentCode, Integer enabled,
                                             int offset, int limit) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = buildCondition(triggerType, agentCode, enabled);
        wrapper.orderByDesc(AgentTriggerEntity::getId).last("LIMIT " + offset + "," + limit);
        return agentTriggerMapper.selectList(wrapper);
    }

    @Override
    public long countByCondition(String triggerType, String agentCode, Integer enabled) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = buildCondition(triggerType, agentCode, enabled);
        return agentTriggerMapper.selectCount(wrapper);
    }

    @Override
    public void updateLastFireTime(Long id, LocalDateTime lastFireTime) {
        LambdaUpdateWrapper<AgentTriggerEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(AgentTriggerEntity::getId, id)
                .set(AgentTriggerEntity::getLastFireTime, lastFireTime);
        agentTriggerMapper.update(wrapper);
    }

    private LambdaQueryWrapper<AgentTriggerEntity> buildCondition(String triggerType, String agentCode,
                                                                  Integer enabled) {
        LambdaQueryWrapper<AgentTriggerEntity> wrapper = new LambdaQueryWrapper<>();
        if (triggerType != null && !triggerType.isBlank()) {
            wrapper.eq(AgentTriggerEntity::getTriggerType, triggerType);
        }
        if (agentCode != null && !agentCode.isBlank()) {
            wrapper.eq(AgentTriggerEntity::getAgentCode, agentCode);
        }
        if (enabled != null) {
            wrapper.eq(AgentTriggerEntity::getEnabled, enabled);
        }
        return wrapper;
    }
}
