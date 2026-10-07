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
package com.yangqiongai.ai.workflow.event;

/**
 * 工作流定义删除事件，定义删除后发布用于企业版版本表同步清理
 * @author yangqiong
 */
public class WorkflowDefinitionDeletedEvent {

    /**
     * 定义名称
     */
    private final String definitionName;

    public WorkflowDefinitionDeletedEvent(String definitionName) {
        this.definitionName = definitionName;
    }

    public String getDefinitionName() {
        return definitionName;
    }
}
