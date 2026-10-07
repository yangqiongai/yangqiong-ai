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

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 循环节点
 * <p>
 * 支持两种模式：
 * 1. 条件循环：通过 exitCondition 控制退出
 * 2. 数组遍历：通过 iterateOver 指定数组变量名，每次迭代将当前元素写入 currentItemVar
 * <p>
 * 数组遍历配置：
 * - iterateOver: 要遍历的变量名（该变量应为List/数组，或逗号分隔的字符串）
 * - currentItemVar: 当前元素的变量名，默认 "currentItem"
 * - currentIndexVar: 当前索引的变量名，默认 "currentIndex"
 *
 * @author yangqiong
 */
public class LoopNode extends WorkflowNode {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 获取退出条件表达式
     */
    public String getExitCondition() {
        return getConfigString("exitCondition");
    }

    /**
     * 设置退出条件表达式
     */
    public void setExitCondition(String exitCondition) {
        setConfigValue("exitCondition", exitCondition);
    }

    /**
     * 获取最大迭代次数
     */
    public Integer getMaxIterations() {
        return getConfigInt("maxIterations");
    }

    /**
     * 设置最大迭代次数
     */
    public void setMaxIterations(Integer maxIterations) {
        setConfigValue("maxIterations", maxIterations);
    }

    /**
     * 获取子节点列表（兼容JSON反序列化产生的Map结构，自动转换为WorkflowNode）
     */
    @SuppressWarnings("unchecked")
    public List<WorkflowNode> getSubNodes() {
        Object value = getConfigValue("subNodes");
        if (value instanceof List<?> list) {
            if (list.isEmpty() || list.get(0) instanceof WorkflowNode) {
                return (List<WorkflowNode>) list;
            }
            // 定义JSON中的子节点为Map结构，转换为WorkflowNode
            return list.stream()
                    .map(item -> OBJECT_MAPPER.convertValue(item, WorkflowNode.class))
                    .toList();
        }
        return null;
    }

    /**
     * 设置子节点列表
     */
    public void setSubNodes(List<WorkflowNode> subNodes) {
        setConfigValue("subNodes", subNodes);
    }

    /**
     * 获取要遍历的数组变量名（数组遍历模式）
     */
    public String getIterateOver() {
        return getConfigString("iterateOver");
    }

    /**
     * 设置要遍历的数组变量名
     */
    public void setIterateOver(String iterateOver) {
        setConfigValue("iterateOver", iterateOver);
    }

    /**
     * 获取当前元素的变量名，默认 "currentItem"
     */
    public String getCurrentItemVar() {
        String var = getConfigString("currentItemVar");
        return var != null ? var : "currentItem";
    }

    /**
     * 设置当前元素的变量名
     */
    public void setCurrentItemVar(String currentItemVar) {
        setConfigValue("currentItemVar", currentItemVar);
    }

    /**
     * 获取当前索引的变量名，默认 "currentIndex"
     */
    public String getCurrentIndexVar() {
        String var = getConfigString("currentIndexVar");
        return var != null ? var : "currentIndex";
    }

    /**
     * 设置当前索引的变量名
     */
    public void setCurrentIndexVar(String currentIndexVar) {
        setConfigValue("currentIndexVar", currentIndexVar);
    }
}
