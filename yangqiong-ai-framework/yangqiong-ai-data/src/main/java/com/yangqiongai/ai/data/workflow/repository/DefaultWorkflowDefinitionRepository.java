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
package com.yangqiongai.ai.data.workflow.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.data.workflow.entity.WorkflowDefinitionEntity;
import com.yangqiongai.ai.data.workflow.mapper.WorkflowDefinitionMapper;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.repository.WorkflowDefinitionRepository;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 工作流定义
 * @author yangqiong
 */
public class DefaultWorkflowDefinitionRepository implements WorkflowDefinitionRepository {

    @Autowired
    private WorkflowDefinitionMapper workflowDefinitionMapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public WorkflowDefinition loadByName(String definitionName) {
        LambdaQueryWrapper<WorkflowDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .eq(WorkflowDefinitionEntity::getStatus, 1)
                .orderByDesc(WorkflowDefinitionEntity::getVersion)
                .last("LIMIT 1");
        WorkflowDefinitionEntity entity = workflowDefinitionMapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return entity.toModel(objectMapper);
    }

    @Override
    public WorkflowDefinition loadByNameAndVersion(String definitionName, int version) {
        LambdaQueryWrapper<WorkflowDefinitionEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(WorkflowDefinitionEntity::getDefinitionName, definitionName)
                .eq(WorkflowDefinitionEntity::getVersion, version);
        WorkflowDefinitionEntity entity = workflowDefinitionMapper.selectOne(wrapper);
        if (entity == null) {
            return null;
        }
        return entity.toModel(objectMapper);
    }
}
