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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LoopNode 单元测试
 *
 * @author yangqiong
 */
class LoopNodeTest {

    @Nested
    @DisplayName("子节点列表获取")
    class SubNodesTests {

        @Test
        @DisplayName("config中的Map结构子节点自动转换为WorkflowNode")
        void shouldConvertMapSubNodesToWorkflowNode() {
            LoopNode loopNode = new LoopNode();
            loopNode.setId("loop1");

            Map<String, Object> itemConfig = new HashMap<>();
            itemConfig.put("transformType", "TEMPLATE");
            itemConfig.put("transformConfig", Map.of("template", "巡检项：${checkItem}"));

            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("id", "tr_item");
            itemMap.put("name", "处理巡检项");
            itemMap.put("type", "TRANSFORM");
            itemMap.put("config", itemConfig);

            loopNode.setConfigValue("subNodes", List.of(itemMap));

            List<WorkflowNode> subNodes = loopNode.getSubNodes();
            assertThat(subNodes).hasSize(1);
            assertThat(subNodes.get(0)).isInstanceOf(WorkflowNode.class);
            assertThat(subNodes.get(0).getId()).isEqualTo("tr_item");
            assertThat(subNodes.get(0).getName()).isEqualTo("处理巡检项");
            assertThat(subNodes.get(0).getType()).isEqualTo(NodeType.TRANSFORM);
            assertThat(subNodes.get(0).getConfig()).containsEntry("transformType", "TEMPLATE");
        }

        @Test
        @DisplayName("WorkflowNode类型的子节点直接返回不重复转换")
        void shouldReturnWorkflowNodeSubNodesDirectly() {
            LoopNode loopNode = new LoopNode();

            WorkflowNode subNode = new WorkflowNode();
            subNode.setId("sub1");
            subNode.setName("子节点");
            subNode.setType(NodeType.AGENT);

            loopNode.setConfigValue("subNodes", List.of(subNode));

            List<WorkflowNode> subNodes = loopNode.getSubNodes();
            assertThat(subNodes).hasSize(1);
            assertThat(subNodes.get(0)).isSameAs(subNode);
        }

        @Test
        @DisplayName("空子节点列表返回空列表")
        void shouldReturnEmptyListForEmptySubNodes() {
            LoopNode loopNode = new LoopNode();
            loopNode.setConfigValue("subNodes", List.of());
            assertThat(loopNode.getSubNodes()).isEmpty();
        }

        @Test
        @DisplayName("未配置子节点返回null")
        void shouldReturnNullWhenSubNodesNotConfigured() {
            LoopNode loopNode = new LoopNode();
            assertThat(loopNode.getSubNodes()).isNull();
        }

        @Test
        @DisplayName("转换后的子节点配置字段完整保留")
        void shouldPreserveAllFieldsAfterConversion() {
            LoopNode loopNode = new LoopNode();

            Map<String, Object> itemMap = new HashMap<>();
            itemMap.put("id", "sub_assign");
            itemMap.put("name", "循环内赋值");
            itemMap.put("type", "ASSIGN");
            itemMap.put("config", Map.of("assignments", Map.of("result", "${checkItem}")));
            itemMap.put("inputMappings", Map.of("text", "${rawText}"));
            itemMap.put("outputMappings", Map.of("output", "itemResult"));
            itemMap.put("timeoutSeconds", 30);
            itemMap.put("maxRetries", 2);

            loopNode.setConfigValue("subNodes", List.of(itemMap));

            WorkflowNode converted = loopNode.getSubNodes().get(0);
            assertThat(converted.getId()).isEqualTo("sub_assign");
            assertThat(converted.getConfig()).containsKey("assignments");
            assertThat(converted.getInputMappings()).containsEntry("text", "${rawText}");
            assertThat(converted.getOutputMappings()).containsEntry("output", "itemResult");
            assertThat(converted.getTimeoutSeconds()).isEqualTo(30);
            assertThat(converted.getMaxRetries()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("循环配置读取")
    class LoopConfigTests {

        @Test
        @DisplayName("数组遍历模式配置读取")
        void shouldReadIterateConfig() {
            LoopNode loopNode = new LoopNode();
            loopNode.setIterateOver("checkItems");
            loopNode.setCurrentItemVar("checkItem");
            loopNode.setCurrentIndexVar("itemIndex");
            loopNode.setMaxIterations(10);

            assertThat(loopNode.getIterateOver()).isEqualTo("checkItems");
            assertThat(loopNode.getCurrentItemVar()).isEqualTo("checkItem");
            assertThat(loopNode.getCurrentIndexVar()).isEqualTo("itemIndex");
            assertThat(loopNode.getMaxIterations()).isEqualTo(10);
        }

        @Test
        @DisplayName("当前项变量名默认值")
        void shouldUseDefaultItemVarNames() {
            LoopNode loopNode = new LoopNode();
            assertThat(loopNode.getCurrentItemVar()).isEqualTo("currentItem");
            assertThat(loopNode.getCurrentIndexVar()).isEqualTo("currentIndex");
        }

        @Test
        @DisplayName("条件循环模式配置读取")
        void shouldReadExitCondition() {
            LoopNode loopNode = new LoopNode();
            loopNode.setExitCondition("${itemIndex} >= 5");
            assertThat(loopNode.getExitCondition()).isEqualTo("${itemIndex} >= 5");
        }
    }
}
