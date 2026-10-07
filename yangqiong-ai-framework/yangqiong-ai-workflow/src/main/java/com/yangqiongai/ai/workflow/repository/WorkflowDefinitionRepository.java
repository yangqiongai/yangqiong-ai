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
package com.yangqiongai.ai.workflow.repository;

import com.yangqiongai.ai.workflow.model.WorkflowDefinition;

/**
 * 工作流定义
 * @author yangqiong
 */
public interface WorkflowDefinitionRepository {

    /**
     * 根据名称查询最新启用版本的工作流定义
     * @param definitionName
     * @return
     */
    WorkflowDefinition loadByName(String definitionName);

    /**
     * 根据名称和版本查询工作流定义
     * @param definitionName
     * @param version
     * @return
     */
    WorkflowDefinition loadByNameAndVersion(String definitionName, int version);
}
