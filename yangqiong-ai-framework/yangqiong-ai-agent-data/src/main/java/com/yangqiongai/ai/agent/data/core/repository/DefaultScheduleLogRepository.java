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
import com.yangqiongai.ai.agent.data.core.entity.ScheduleLogEntity;
import com.yangqiongai.ai.agent.data.core.mapper.ScheduleLogMapper;
import com.yangqiongai.ai.agent.scheduler.model.ScheduleLogInfo;
import com.yangqiongai.ai.agent.scheduler.repository.ScheduleLogRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 调度执行历史仓库默认实现
 * @author yangqiong
 */
public class DefaultScheduleLogRepository implements ScheduleLogRepository {

    @Autowired
    private ScheduleLogMapper scheduleLogMapper;

    @Override
    public void save(ScheduleLogInfo info) {
        ScheduleLogEntity entity = toEntity(info);
        entity.setCreateTime(LocalDateTime.now());
        scheduleLogMapper.insert(entity);
        info.setId(entity.getId());
    }

    @Override
    public long countByScheduleId(String scheduleId) {
        return scheduleLogMapper.selectCount(new LambdaQueryWrapper<ScheduleLogEntity>()
                .eq(ScheduleLogEntity::getScheduleId, scheduleId));
    }

    @Override
    public List<ScheduleLogInfo> pageByScheduleId(String scheduleId, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.max(pageSize, 1);
        int offset = (safePageNum - 1) * safePageSize;
        return scheduleLogMapper.selectList(new LambdaQueryWrapper<ScheduleLogEntity>()
                .eq(ScheduleLogEntity::getScheduleId, scheduleId)
                .orderByDesc(ScheduleLogEntity::getFireTime)
                .last("LIMIT " + offset + "," + safePageSize))
                .stream().map(this::toInfo).toList();
    }

    @Override
    public List<ScheduleLogInfo> findRecentByScheduleId(String scheduleId, int limit) {
        int safeLimit = Math.max(limit, 1);
        return scheduleLogMapper.selectList(new LambdaQueryWrapper<ScheduleLogEntity>()
                .eq(ScheduleLogEntity::getScheduleId, scheduleId)
                .orderByDesc(ScheduleLogEntity::getFireTime)
                .last("LIMIT " + safeLimit))
                .stream().map(this::toInfo).toList();
    }

    /**
     * 实体转模型
     * @param entity
     * @return
     */
    private ScheduleLogInfo toInfo(ScheduleLogEntity entity) {
        ScheduleLogInfo info = new ScheduleLogInfo();
        info.setId(entity.getId());
        info.setScheduleId(entity.getScheduleId());
        info.setAgentCode(entity.getAgentCode());
        info.setUserId(entity.getUserId());
        info.setInputText(entity.getInputText());
        info.setOutputText(entity.getOutputText());
        info.setSuccess(entity.getSuccess());
        info.setErrorMessage(entity.getErrorMessage());
        info.setDurationMs(entity.getDurationMs());
        info.setInputTokens(entity.getInputTokens());
        info.setOutputTokens(entity.getOutputTokens());
        info.setTotalTokens(entity.getTotalTokens());
        info.setFireTime(entity.getFireTime());
        info.setCreateTime(entity.getCreateTime());
        return info;
    }

    /**
     * 模型转实体
     * @param info
     * @return
     */
    private ScheduleLogEntity toEntity(ScheduleLogInfo info) {
        ScheduleLogEntity entity = new ScheduleLogEntity();
        entity.setId(info.getId());
        entity.setScheduleId(info.getScheduleId());
        entity.setAgentCode(info.getAgentCode());
        entity.setUserId(info.getUserId());
        entity.setInputText(info.getInputText());
        entity.setOutputText(info.getOutputText());
        entity.setSuccess(info.getSuccess());
        entity.setErrorMessage(info.getErrorMessage());
        entity.setDurationMs(info.getDurationMs());
        entity.setInputTokens(info.getInputTokens());
        entity.setOutputTokens(info.getOutputTokens());
        entity.setTotalTokens(info.getTotalTokens());
        entity.setFireTime(info.getFireTime());
        return entity;
    }
}
