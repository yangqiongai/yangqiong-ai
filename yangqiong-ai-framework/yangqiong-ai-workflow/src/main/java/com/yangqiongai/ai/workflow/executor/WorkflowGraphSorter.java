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
package com.yangqiongai.ai.workflow.executor;

import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowEdge;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * 工作流图分析
 * @author yangqiong
 */
@Service
public class WorkflowGraphSorter {

    /**
     * DAG拓扑排序（Kahn算法）
     * @param definition
     * @return
     */
    public List<String> topologicalSort(WorkflowDefinition definition) {
        List<WorkflowNode> nodes = definition.getNodes();
        List<WorkflowEdge> edges = definition.getEdges();

        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 构建邻接表和入度表
        Map<String, Set<String>> adjacency = new LinkedHashMap<>();
        Map<String, Integer> inDegree = new LinkedHashMap<>();

        for (WorkflowNode node : nodes) {
            adjacency.put(node.getId(), new LinkedHashSet<>());
            inDegree.put(node.getId(), 0);
        }

        if (edges != null) {
            for (WorkflowEdge edge : edges) {
                String source = edge.getSourceId();
                String target = edge.getTargetId();
                if (adjacency.containsKey(source) && adjacency.containsKey(target)) {
                    // 同一对source→target的重复边只计算一次入度（如多条条件边指向同一节点）
                    if (adjacency.get(source).add(target)) {
                        inDegree.merge(target, 1, Integer::sum);
                    }
                }
            }
        }

        // BFS入度为0的节点
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            sorted.add(current);
            for (String neighbor : adjacency.getOrDefault(current, Set.of())) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        // 检测环
        if (sorted.size() != nodes.size()) {
            throw new IllegalStateException("工作流存在环，无法进行拓扑排序: " + definition.getName());
        }

        return sorted;
    }

    /**
     * 查找节点的直接下游节点
     * @param definition
     * @param nodeId
     * @return
     */
    public List<String> findDownstreamNodes(WorkflowDefinition definition, String nodeId) {
        List<String> downstream = new ArrayList<>();
        List<WorkflowEdge> edges = definition.findOutgoingEdges(nodeId);
        for (WorkflowEdge edge : edges) {
            if (!downstream.contains(edge.getTargetId())) {
                downstream.add(edge.getTargetId());
            }
        }
        return downstream;
    }
}
