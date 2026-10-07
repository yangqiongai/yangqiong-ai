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
import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import com.yangqiongai.ai.agent.core.repository.AgentTaskStepRepository;
import com.yangqiongai.ai.agent.data.core.entity.AgentTaskStepEntity;
import com.yangqiongai.ai.agent.data.core.mapper.AgentTaskStepMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Agent任务步骤仓库默认实现
 * @author yangqiong
 */
public class DefaultAgentTaskStepRepository implements AgentTaskStepRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultAgentTaskStepRepository.class);

    @Autowired
    private AgentTaskStepMapper agentTaskStepMapper;

    @Value("${ai.agent.task.persistence.enabled:true}")
    private boolean persistenceEnabled;

    @Override
    public void saveSteps(List<AgentTaskStepInfo> steps) {
        if (!persistenceEnabled || steps == null || steps.isEmpty()) return;
        try {
            for (AgentTaskStepInfo step : steps) {
                AgentTaskStepEntity entity = toEntity(step);
                if (entity.getCreateTime() == null) {
                    entity.setCreateTime(LocalDateTime.now());
                }
                agentTaskStepMapper.insert(entity);
            }
        } catch (Exception e) {
            log.error("批量写入步骤失败: stepCount={}", steps.size(), e);
        }
    }

    @Override
    public List<AgentTaskStepInfo> querySteps(String taskId) {
        if (!persistenceEnabled) return Collections.emptyList();
        try {
            LambdaQueryWrapper<AgentTaskStepEntity> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(AgentTaskStepEntity::getTaskId, taskId)
                    .orderByAsc(AgentTaskStepEntity::getStepOrder);
            return agentTaskStepMapper.selectList(wrapper).stream().map(this::toInfo).toList();
        } catch (Exception e) {
            log.error("查询任务步骤失败: taskId={}", taskId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public List<AgentTaskStepInfo> queryStepsByTaskTree(String taskId) {
        if (!persistenceEnabled) return Collections.emptyList();
        try {
            return agentTaskStepMapper.selectStepsByTaskTree(taskId).stream().map(this::toInfo).toList();
        } catch (Exception e) {
            log.error("查询任务树步骤失败: taskId={}", taskId, e);
            return Collections.emptyList();
        }
    }

    private AgentTaskStepInfo toInfo(AgentTaskStepEntity entity) {
        AgentTaskStepInfo info = new AgentTaskStepInfo();
        info.setId(entity.getId());
        info.setTaskId(entity.getTaskId());
        info.setScopeId(entity.getScopeId());
        info.setStepOrder(entity.getStepOrder());
        info.setStepType(entity.getStepType());
        info.setAgentName(entity.getAgentName());
        info.setStepContent(entity.getStepContent());
        info.setToolName(entity.getToolName());
        info.setToolInput(entity.getToolInput());
        info.setToolOutput(entity.getToolOutput());
        info.setInputTokens(entity.getInputTokens());
        info.setOutputTokens(entity.getOutputTokens());
        info.setTotalTokens(entity.getTotalTokens());
        info.setDurationMs(entity.getDurationMs());
        return info;
    }

    private AgentTaskStepEntity toEntity(AgentTaskStepInfo info) {
        AgentTaskStepEntity entity = new AgentTaskStepEntity();
        entity.setId(info.getId());
        entity.setTaskId(info.getTaskId());
        entity.setScopeId(info.getScopeId());
        entity.setStepOrder(info.getStepOrder());
        entity.setStepType(info.getStepType());
        entity.setAgentName(info.getAgentName());
        entity.setStepContent(info.getStepContent());
        entity.setToolName(info.getToolName());
        entity.setToolInput(info.getToolInput());
        entity.setToolOutput(info.getToolOutput());
        entity.setInputTokens(info.getInputTokens());
        entity.setOutputTokens(info.getOutputTokens());
        entity.setTotalTokens(info.getTotalTokens());
        entity.setDurationMs(info.getDurationMs());
        return entity;
    }
}
