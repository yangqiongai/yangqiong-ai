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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流定义
 * @author yangqiong
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDefinition {

    private String name;

    private String description;

    /**
     * 定义版本号（企业版版本管理/灰度使用，社区版不感知）
     */
    private Integer version;

    private List<WorkflowNode> nodes;

    private List<WorkflowEdge> edges;

    @Builder.Default
    private StateConfig stateConfig = StateConfig.builder().build();

    @Builder.Default
    private ErrorStrategy errorStrategy = ErrorStrategy.STOP;

    @Builder.Default
    private int maxRetries = 0;

    /**
     * 节点超时时间（秒），默认120秒
     */
    @Builder.Default
    private int nodeTimeoutSeconds = 120;

    // ==================== 索引（非序列化字段） ====================

    @JsonIgnore
    private transient Map<String, WorkflowNode> nodeIndex;

    @JsonIgnore
    private transient Map<String, List<WorkflowEdge>> outgoingEdgeIndex;

    @JsonIgnore
    private transient Map<String, List<WorkflowEdge>> incomingEdgeIndex;

    public void setNodes(List<WorkflowNode> nodes) {
        this.nodes = nodes;
        rebuildIndex();
    }

    public void setEdges(List<WorkflowEdge> edges) {
        this.edges = edges;
        rebuildIndex();
    }

    private void rebuildIndex() {
        nodeIndex = new HashMap<>();
        if (nodes != null) {
            for (WorkflowNode node : nodes) {
                nodeIndex.put(node.getId(), node);
            }
        }

        outgoingEdgeIndex = new HashMap<>();
        incomingEdgeIndex = new HashMap<>();
        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                outgoingEdgeIndex.computeIfAbsent(edge.getSourceId(), k -> new java.util.ArrayList<>()).add(edge);
                incomingEdgeIndex.computeIfAbsent(edge.getTargetId(), k -> new java.util.ArrayList<>()).add(edge);
            }
        }
    }

    private void ensureIndex() {
        if (nodeIndex == null) {
            rebuildIndex();
        }
    }

    public WorkflowNode findNode(String nodeId) {
        if (nodeId == null) return null;
        ensureIndex();
        return nodeIndex.get(nodeId);
    }

    public List<WorkflowEdge> findOutgoingEdges(String nodeId) {
        if (nodeId == null) return List.of();
        ensureIndex();
        return outgoingEdgeIndex.getOrDefault(nodeId, List.of());
    }

    public List<WorkflowEdge> findIncomingEdges(String nodeId) {
        if (nodeId == null) return List.of();
        ensureIndex();
        return incomingEdgeIndex.getOrDefault(nodeId, List.of());
    }
}
