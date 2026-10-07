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

import java.util.Map;

/**
 * 变量赋值/聚合节点
 * <p>
 * 用途：
 * 1. 将常量或变量引用赋值给新变量
 * 2. 聚合多个上游节点的输出到一个Map
 * 3. 初始化/重置变量
 * <p>
 * config:
 * - assignments: Map<String, String>，key=目标变量名，value=值或变量引用
 *   常量值：直接写值，如 "hello"
 *   变量引用：${varName}，如 "${step1.output}"
 *   SpEL表达式：#{expression}，如 "#{1 + 2}"
 * <p>
 * 示例：
 * {"assignments": {"name": "Alice", "score": "${step1.result}", "total": "#{${a} + ${b}"}}
 *
 * @author yangqiong
 */
public class AssignNode extends WorkflowNode {

    /**
     * 获取赋值映射
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getAssignments() {
        Object value = getConfigValue("assignments");
        if (value instanceof Map) {
            return (Map<String, String>) value;
        }
        return null;
    }

    /**
     * 设置赋值映射
     */
    public void setAssignments(Map<String, String> assignments) {
        setConfigValue("assignments", assignments);
    }
}
