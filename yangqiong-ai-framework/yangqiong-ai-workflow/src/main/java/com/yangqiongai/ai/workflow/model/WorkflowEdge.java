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
package com.yangqiongai.ai.workflow.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

/**
 * 工作流边
 * @author yangqiong
 */
@Data
public class WorkflowEdge {

    /**
     * 边ID
     */
    private String id;

    /**
     * 源节点ID
     */
    private String sourceId;

    /**
     * 目标节点ID
     */
    private String targetId;

    /**
     * 边类型
     */
    private EdgeType type;

    /**
     * 条件表达式（条件边专用）
     */
    private String conditionExpression;

    /**
     * 条件标签（前端显示用）
     */
    private String conditionLabel;

    /**
     * 是否为条件边
     * @return
     */
    @JsonIgnore
    public boolean isConditional() {
        return type == EdgeType.CONDITIONAL;
    }

    /**
     * 是否为并行边
     * @return
     */
    @JsonIgnore
    public boolean isParallel() {
        return type == EdgeType.PARALLEL;
    }
}
