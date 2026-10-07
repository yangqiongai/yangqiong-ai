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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WorkflowDefinition 单元测试
 *
 * @author test
 */
class WorkflowDefinitionTest {

    // ==================== 辅助方法 ====================

    private WorkflowNode createNode(String id, String name, NodeType type) {
        WorkflowNode node = new WorkflowNode();
        node.setId(id);
        node.setName(name);
        node.setType(type);
        return node;
    }

    private WorkflowEdge createEdge(String id, String sourceId, String targetId, EdgeType type) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setId(id);
        edge.setSourceId(sourceId);
        edge.setTargetId(targetId);
        edge.setType(type);
        return edge;
    }

    // ==================== Builder 模式测试 ====================

    @Nested
    @DisplayName("Builder 模式构建")
    class BuilderTests {

        @Test
        @DisplayName("使用 Builder 构建完整的 WorkflowDefinition")
        void shouldBuildDefinitionWithBuilder() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);
            WorkflowNode endNode = createNode("end", "结束", NodeType.END);
            WorkflowEdge edge = createEdge("e1", "start", "end", EdgeType.NORMAL);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test-workflow")
                    .description("测试工作流")
                    .nodes(List.of(startNode, endNode))
                    .edges(List.of(edge))
                    .errorStrategy(ErrorStrategy.STOP)
                    .maxRetries(3)
                    .nodeTimeoutSeconds(60)
                    .build();

            assertThat(def.getName()).isEqualTo("test-workflow");
            assertThat(def.getDescription()).isEqualTo("测试工作流");
            assertThat(def.getNodes()).hasSize(2);
            assertThat(def.getEdges()).hasSize(1);
            assertThat(def.getErrorStrategy()).isEqualTo(ErrorStrategy.STOP);
            assertThat(def.getMaxRetries()).isEqualTo(3);
            assertThat(def.getNodeTimeoutSeconds()).isEqualTo(60);
        }

        @Test
        @DisplayName("Builder 默认值：errorStrategy=STOP, maxRetries=0, nodeTimeoutSeconds=120")
        void shouldUseBuilderDefaults() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("minimal")
                    .build();

            assertThat(def.getErrorStrategy()).isEqualTo(ErrorStrategy.STOP);
            assertThat(def.getMaxRetries()).isEqualTo(0);
            assertThat(def.getNodeTimeoutSeconds()).isEqualTo(120);
            assertThat(def.getStateConfig()).isNotNull();
            assertThat(def.getStateConfig().isPersistEnabled()).isTrue();
            assertThat(def.getStateConfig().getTtlHours()).isEqualTo(48);
        }
    }

    // ==================== findNode 测试 ====================

    @Nested
    @DisplayName("findNode 查找节点")
    class FindNodeTests {

        @Test
        @DisplayName("findNode 返回正确的节点")
        void shouldReturnCorrectNode() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);
            WorkflowNode agentNode = createNode("agent1", "Agent", NodeType.AGENT);
            WorkflowNode endNode = createNode("end", "结束", NodeType.END);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(startNode, agentNode, endNode))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findNode("start")).isSameAs(startNode);
            assertThat(def.findNode("agent1")).isSameAs(agentNode);
            assertThat(def.findNode("end")).isSameAs(endNode);
        }

        @Test
        @DisplayName("findNode 对不存在的 nodeId 返回 null")
        void shouldReturnNullForNonExistentNode() {
            WorkflowNode node = createNode("n1", "节点1", NodeType.AGENT);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(node))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findNode("non-existent")).isNull();
        }

        @Test
        @DisplayName("findNode 对 null 输入返回 null")
        void shouldReturnNullForNullInput() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(createNode("n1", "节点1", NodeType.AGENT)))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findNode(null)).isNull();
        }
    }

    // ==================== findOutgoingEdges / findIncomingEdges 测试 ====================

    @Nested
    @DisplayName("边查找测试")
    class FindEdgesTests {

        @Test
        @DisplayName("findOutgoingEdges 返回正确的出边")
        void shouldReturnCorrectOutgoingEdges() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);
            WorkflowNode agentA = createNode("a", "A", NodeType.AGENT);
            WorkflowNode agentB = createNode("b", "B", NodeType.AGENT);

            WorkflowEdge edgeToA = createEdge("e1", "start", "a", EdgeType.NORMAL);
            WorkflowEdge edgeToB = createEdge("e2", "start", "b", EdgeType.NORMAL);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(startNode, agentA, agentB))
                    .edges(List.of(edgeToA, edgeToB))
                    .build();

            List<WorkflowEdge> outgoing = def.findOutgoingEdges("start");
            assertThat(outgoing).hasSize(2);
            assertThat(outgoing).containsExactlyInAnyOrder(edgeToA, edgeToB);
        }

        @Test
        @DisplayName("findIncomingEdges 返回正确的入边")
        void shouldReturnCorrectIncomingEdges() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);
            WorkflowNode agentA = createNode("a", "A", NodeType.AGENT);
            WorkflowNode endNode = createNode("end", "结束", NodeType.END);

            WorkflowEdge edgeFromStart = createEdge("e1", "start", "a", EdgeType.NORMAL);
            WorkflowEdge edgeFromEnd = createEdge("e2", "end", "a", EdgeType.NORMAL);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(startNode, agentA, endNode))
                    .edges(List.of(edgeFromStart, edgeFromEnd))
                    .build();

            List<WorkflowEdge> incoming = def.findIncomingEdges("a");
            assertThat(incoming).hasSize(2);
            assertThat(incoming).containsExactlyInAnyOrder(edgeFromStart, edgeFromEnd);
        }

        @Test
        @DisplayName("findOutgoingEdges 对没有出边的节点返回空列表")
        void shouldReturnEmptyForNoOutgoingEdges() {
            WorkflowNode endNode = createNode("end", "结束", NodeType.END);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(endNode))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findOutgoingEdges("end")).isEmpty();
        }

        @Test
        @DisplayName("findIncomingEdges 对没有入边的节点返回空列表")
        void shouldReturnEmptyForNoIncomingEdges() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(startNode))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findIncomingEdges("start")).isEmpty();
        }

        @Test
        @DisplayName("findOutgoingEdges 对 null 输入返回空列表")
        void shouldReturnEmptyForNullNodeIdOutgoing() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(createNode("n1", "节点1", NodeType.START)))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findOutgoingEdges(null)).isEmpty();
        }

        @Test
        @DisplayName("findIncomingEdges 对 null 输入返回空列表")
        void shouldReturnEmptyForNullNodeIdIncoming() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(createNode("n1", "节点1", NodeType.START)))
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findIncomingEdges(null)).isEmpty();
        }
    }

    // ==================== 索引重建测试 ====================

    @Nested
    @DisplayName("索引重建测试")
    class IndexRebuildTests {

        @Test
        @DisplayName("setNodes 触发索引重建")
        void shouldRebuildIndexWhenNodesSet() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .build();

            // Builder 不触发 rebuildIndex，nodeIndex 为 null
            // 但 findNode 会通过 ensureIndex 懒加载
            WorkflowNode node = createNode("n1", "节点1", NodeType.AGENT);
            def.setNodes(List.of(node));

            assertThat(def.findNode("n1")).isSameAs(node);
        }

        @Test
        @DisplayName("setEdges 触发索引重建")
        void shouldRebuildIndexWhenEdgesSet() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);
            WorkflowNode endNode = createNode("end", "结束", NodeType.END);

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(startNode, endNode))
                    .build();

            // Builder 不触发 rebuildIndex，通过 setEdges 触发
            WorkflowEdge edge = createEdge("e1", "start", "end", EdgeType.NORMAL);
            def.setEdges(List.of(edge));

            assertThat(def.findOutgoingEdges("start")).hasSize(1);
            assertThat(def.findIncomingEdges("end")).hasSize(1);
        }

        @Test
        @DisplayName("ensureIndex 懒加载：Builder 构建后首次查找触发索引构建")
        void shouldLazilyRebuildIndexOnFirstAccess() {
            WorkflowNode node = createNode("n1", "节点1", NodeType.AGENT);
            WorkflowEdge edge = createEdge("e1", "n1", "n2", EdgeType.NORMAL);

            // 通过 Builder 构建，不会调用 setNodes/setEdges，索引为 null
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(node))
                    .edges(List.of(edge))
                    .build();

            // 首次调用 findNode 触发 ensureIndex
            assertThat(def.findNode("n1")).isSameAs(node);
            assertThat(def.findOutgoingEdges("n1")).hasSize(1);
        }

        @Test
        @DisplayName("setNodes 替换节点后索引更新")
        void shouldUpdateIndexAfterNodesReplaced() {
            WorkflowNode oldNode = createNode("old", "旧节点", NodeType.AGENT);
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("test")
                    .nodes(List.of(oldNode))
                    .edges(Collections.emptyList())
                    .build();

            // 先触发索引构建
            assertThat(def.findNode("old")).isSameAs(oldNode);

            // 替换节点
            WorkflowNode newNode = createNode("new", "新节点", NodeType.AGENT);
            def.setNodes(List.of(newNode));

            assertThat(def.findNode("old")).isNull();
            assertThat(def.findNode("new")).isSameAs(newNode);
        }
    }

    // ==================== 空定义测试 ====================

    @Nested
    @DisplayName("空定义测试")
    class EmptyDefinitionTests {

        @Test
        @DisplayName("空定义：没有节点和边")
        void shouldHandleEmptyDefinition() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("empty")
                    .description("空工作流")
                    .nodes(Collections.emptyList())
                    .edges(Collections.emptyList())
                    .build();

            assertThat(def.findNode("any")).isNull();
            assertThat(def.findOutgoingEdges("any")).isEmpty();
            assertThat(def.findIncomingEdges("any")).isEmpty();
        }

        @Test
        @DisplayName("null 节点和边列表")
        void shouldHandleNullNodesAndEdges() {
            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("null-def")
                    .build();

            assertThat(def.findNode("any")).isNull();
            assertThat(def.findOutgoingEdges("any")).isEmpty();
            assertThat(def.findIncomingEdges("any")).isEmpty();
        }
    }

    // ==================== 多边测试 ====================

    @Nested
    @DisplayName("多边场景测试")
    class MultipleEdgesTests {

        @Test
        @DisplayName("两个节点之间可以有多条边")
        void shouldSupportMultipleEdgesBetweenNodes() {
            WorkflowNode startNode = createNode("start", "开始", NodeType.START);
            WorkflowNode endNode = createNode("end", "结束", NodeType.END);

            WorkflowEdge normalEdge = createEdge("e1", "start", "end", EdgeType.NORMAL);
            WorkflowEdge conditionalEdge = createEdge("e2", "start", "end", EdgeType.CONDITIONAL);
            conditionalEdge.setConditionExpression("research");
            conditionalEdge.setConditionLabel("调研");

            WorkflowDefinition def = WorkflowDefinition.builder()
                    .name("multi-edge")
                    .nodes(List.of(startNode, endNode))
                    .edges(List.of(normalEdge, conditionalEdge))
                    .build();

            List<WorkflowEdge> outgoing = def.findOutgoingEdges("start");
            assertThat(outgoing).hasSize(2);

            List<WorkflowEdge> incoming = def.findIncomingEdges("end");
            assertThat(incoming).hasSize(2);
        }

        @Test
        @DisplayName("条件边 isConditional 返回 true")
        void shouldIdentifyConditionalEdge() {
            WorkflowEdge edge = createEdge("e1", "a", "b", EdgeType.CONDITIONAL);
            edge.setConditionExpression("${type}");
            edge.setConditionLabel("类型分支");

            assertThat(edge.isConditional()).isTrue();
            assertThat(edge.isParallel()).isFalse();
        }

        @Test
        @DisplayName("并行边 isParallel 返回 true")
        void shouldIdentifyParallelEdge() {
            WorkflowEdge edge = createEdge("e1", "a", "b", EdgeType.PARALLEL);

            assertThat(edge.isParallel()).isTrue();
            assertThat(edge.isConditional()).isFalse();
        }

        @Test
        @DisplayName("普通边既不是条件边也不是并行边")
        void shouldIdentifyNormalEdge() {
            WorkflowEdge edge = createEdge("e1", "a", "b", EdgeType.NORMAL);

            assertThat(edge.isConditional()).isFalse();
            assertThat(edge.isParallel()).isFalse();
        }
    }

    // ==================== WorkflowNode 配置方法测试 ====================

    @Nested
    @DisplayName("WorkflowNode 配置方法测试")
    class WorkflowNodeConfigTests {

        @Test
        @DisplayName("getConfigValue / setConfigValue 正常工作")
        void shouldGetAndSetConfigValue() {
            WorkflowNode node = new WorkflowNode();
            node.setConfigValue("key1", "value1");
            node.setConfigValue("key2", 42);

            assertThat(node.getConfigValue("key1")).isEqualTo("value1");
            assertThat(node.getConfigValue("key2")).isEqualTo(42);
            assertThat(node.getConfigValue("nonexistent")).isNull();
        }

        @Test
        @DisplayName("getConfigString 返回字符串值")
        void shouldReturnConfigString() {
            WorkflowNode node = new WorkflowNode();
            node.setConfigValue("name", "test");

            assertThat(node.getConfigString("name")).isEqualTo("test");
            assertThat(node.getConfigString("missing")).isNull();
        }

        @Test
        @DisplayName("getConfigInt 返回整型值")
        void shouldReturnConfigInt() {
            WorkflowNode node = new WorkflowNode();
            node.setConfigValue("count", 10);
            node.setConfigValue("price", 3.14);

            assertThat(node.getConfigInt("count")).isEqualTo(10);
            assertThat(node.getConfigInt("price")).isEqualTo(3);
            assertThat(node.getConfigInt("missing")).isNull();
        }

        @Test
        @DisplayName("getConfigValue 在 config 为 null 时返回 null")
        void shouldReturnNullWhenConfigIsNull() {
            WorkflowNode node = new WorkflowNode();
            assertThat(node.getConfigValue("any")).isNull();
            assertThat(node.getConfigString("any")).isNull();
            assertThat(node.getConfigInt("any")).isNull();
        }

        @Test
        @DisplayName("setConfigValue 在 config 为 null 时自动初始化 Map")
        void shouldInitializeConfigMapWhenNull() {
            WorkflowNode node = new WorkflowNode();
            assertThat(node.getConfig()).isNull();

            node.setConfigValue("key", "value");
            assertThat(node.getConfig()).isNotNull();
            assertThat(node.getConfig()).containsEntry("key", "value");
        }
    }

    // ==================== NodePosition 测试 ====================

    @Test
    @DisplayName("NodePosition 正确存储坐标")
    void shouldStoreNodePosition() {
        NodePosition pos = new NodePosition(100.5, 200.3);
        assertThat(pos.getX()).isEqualTo(100.5);
        assertThat(pos.getY()).isEqualTo(200.3);

        WorkflowNode node = createNode("n1", "节点1", NodeType.AGENT);
        node.setPosition(pos);
        assertThat(node.getPosition()).isNotNull();
        assertThat(node.getPosition().getX()).isEqualTo(100.5);
    }

    // ==================== StateConfig 默认值测试 ====================

    @Test
    @DisplayName("StateConfig 默认值：persistEnabled=true, ttlHours=48")
    void shouldUseStateConfigDefaults() {
        StateConfig config = StateConfig.builder().build();
        assertThat(config.isPersistEnabled()).isTrue();
        assertThat(config.getTtlHours()).isEqualTo(48);
    }

    // ==================== ErrorStrategy 枚举测试 ====================

    @Test
    @DisplayName("ErrorStrategy 包含 STOP, SKIP, RETRY")
    void shouldContainAllErrorStrategies() {
        assertThat(ErrorStrategy.values()).containsExactlyInAnyOrder(
                ErrorStrategy.STOP, ErrorStrategy.SKIP, ErrorStrategy.RETRY
        );
    }

    // ==================== NodeType 枚举测试 ====================

    @Test
    @DisplayName("NodeType 包含所有类型")
    void shouldContainAllNodeTypes() {
        assertThat(NodeType.values()).containsExactlyInAnyOrder(
                NodeType.AGENT, NodeType.CONDITION, NodeType.PARALLEL,
                NodeType.LOOP, NodeType.SUBGRAPH, NodeType.TRANSFORM,
                NodeType.SCRIPT, NodeType.HTTP, NodeType.ASSIGN,
                NodeType.TIME_CONTROL, NodeType.APPROVAL, NodeType.NOTIFY,
                NodeType.START, NodeType.END
        );
    }

    // ==================== EdgeType 枚举测试 ====================

    @Test
    @DisplayName("EdgeType 包含所有类型")
    void shouldContainAllEdgeTypes() {
        assertThat(EdgeType.values()).containsExactlyInAnyOrder(
                EdgeType.NORMAL, EdgeType.CONDITIONAL, EdgeType.PARALLEL
        );
    }
}
