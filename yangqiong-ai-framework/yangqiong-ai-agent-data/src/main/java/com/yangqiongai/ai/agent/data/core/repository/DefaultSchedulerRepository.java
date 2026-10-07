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
package com.yangqiongai.ai.agent.data.core.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.agent.scheduler.model.SchedulerInfo;
import com.yangqiongai.ai.agent.scheduler.repository.SchedulerRepository;
import com.yangqiongai.ai.agent.data.core.entity.SchedulerEntity;
import com.yangqiongai.ai.agent.data.core.mapper.SchedulerMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent定时调度仓库默认实现
 * @author yangqiong
 */
public class DefaultSchedulerRepository implements SchedulerRepository {

    @Autowired
    private SchedulerMapper schedulerMapper;

    @Override
    public void save(SchedulerInfo info) {
        SchedulerEntity entity = toEntity(info);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        schedulerMapper.insert(entity);
        info.setId(entity.getId());
    }

    @Override
    public void updateById(SchedulerInfo info) {
        SchedulerEntity entity = toEntity(info);
        entity.setUpdateTime(LocalDateTime.now());
        schedulerMapper.updateById(entity);
    }

    @Override
    public void deleteById(Long id) {
        schedulerMapper.deleteById(id);
    }

    @Override
    public SchedulerInfo findByScheduleId(String scheduleId) {
        SchedulerEntity entity = schedulerMapper.selectOne(
                new LambdaQueryWrapper<SchedulerEntity>().eq(SchedulerEntity::getScheduleId, scheduleId));
        return entity != null ? toInfo(entity) : null;
    }

    @Override
    public List<SchedulerInfo> listByUserId(String userId) {
        return schedulerMapper.selectList(
                new LambdaQueryWrapper<SchedulerEntity>().eq(SchedulerEntity::getUserId, userId))
                .stream().map(this::toInfo).toList();
    }

    @Override
    public List<SchedulerInfo> listAll() {
        return schedulerMapper.selectList(new LambdaQueryWrapper<>())
                .stream().map(this::toInfo).toList();
    }

    private SchedulerInfo toInfo(SchedulerEntity entity) {
        SchedulerInfo info = new SchedulerInfo();
        info.setId(entity.getId());
        info.setScheduleId(entity.getScheduleId());
        info.setUserId(entity.getUserId());
        info.setTaskName(entity.getTaskName());
        info.setAgentCode(entity.getAgentCode());
        info.setCronExpression(entity.getCronExpression());
        info.setInputText(entity.getInputText());
        info.setDescription(entity.getDescription());
        info.setBody(entity.getBody());
        info.setEnabled(entity.getEnabled());
        info.setNextFireTime(entity.getNextFireTime());
        info.setLastFireTime(entity.getLastFireTime());
        return info;
    }

    private SchedulerEntity toEntity(SchedulerInfo info) {
        SchedulerEntity entity = new SchedulerEntity();
        entity.setId(info.getId());
        entity.setScheduleId(info.getScheduleId());
        entity.setUserId(info.getUserId());
        entity.setTaskName(info.getTaskName());
        entity.setAgentCode(info.getAgentCode());
        entity.setCronExpression(info.getCronExpression());
        entity.setInputText(info.getInputText());
        entity.setDescription(info.getDescription());
        entity.setBody(info.getBody());
        entity.setEnabled(info.getEnabled());
        entity.setNextFireTime(info.getNextFireTime());
        entity.setLastFireTime(info.getLastFireTime());
        return entity;
    }
}
