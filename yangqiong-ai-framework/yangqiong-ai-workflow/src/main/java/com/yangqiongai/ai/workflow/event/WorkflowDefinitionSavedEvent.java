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
 * 工作流定义保存事件，定义创建/更新后发布用于企业版版本表同步
 * @author yangqiong
 */
public class WorkflowDefinitionSavedEvent {

    /**
     * 定义名称
     */
    private final String definitionName;

    /**
     * 定义JSON内容
     */
    private final String definitionJson;

    public WorkflowDefinitionSavedEvent(String definitionName, String definitionJson) {
        this.definitionName = definitionName;
        this.definitionJson = definitionJson;
    }

    public String getDefinitionName() {
        return definitionName;
    }

    public String getDefinitionJson() {
        return definitionJson;
    }
}
