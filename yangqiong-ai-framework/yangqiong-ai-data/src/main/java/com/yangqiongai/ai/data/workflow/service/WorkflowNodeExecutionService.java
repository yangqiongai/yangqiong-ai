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
package com.yangqiongai.ai.data.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yangqiongai.ai.data.workflow.entity.WorkflowNodeExecutionEntity;
import com.yangqiongai.ai.data.workflow.mapper.WorkflowNodeExecutionMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 工作流节点执行轨迹
 * @author yangqiong
 */
@Service
public class WorkflowNodeExecutionService extends ServiceImpl<WorkflowNodeExecutionMapper, WorkflowNodeExecutionEntity> {

    /**
     * 按实例查询节点轨迹，按执行顺序排序
     * @param instanceId
     * @return
     */
    public List<WorkflowNodeExecutionEntity> listByInstance(String instanceId) {
        LambdaQueryWrapper<WorkflowNodeExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowNodeExecutionEntity::getInstanceId, instanceId);
        wrapper.orderByAsc(WorkflowNodeExecutionEntity::getExecutionOrder);
        wrapper.orderByAsc(WorkflowNodeExecutionEntity::getId);
        return list(wrapper);
    }

    /**
     * 删除指定更新时间之前的节点轨迹（保留期清理用）
     * @param beforeTime
     * @return
     */
    public int removeExpiredBefore(java.time.LocalDateTime beforeTime) {
        LambdaQueryWrapper<WorkflowNodeExecutionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.lt(WorkflowNodeExecutionEntity::getStartTime, beforeTime);
        return baseMapper.delete(wrapper);
    }
}
