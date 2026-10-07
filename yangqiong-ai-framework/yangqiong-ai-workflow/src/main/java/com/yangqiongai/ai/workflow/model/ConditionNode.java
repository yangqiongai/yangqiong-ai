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
 * 条件分支节点
 * @author yangqiong
 */
public class ConditionNode extends WorkflowNode {

    /**
     * 获取条件表达式
     * @return
     */
    public String getConditionExpression() {
        return getConfigString("conditionExpression");
    }

    /**
     * 设置条件表达式
     * @param conditionExpression
     */
    public void setConditionExpression(String conditionExpression) {
        setConfigValue("conditionExpression", conditionExpression);
    }

    /**
     * 获取分支映射（条件值 -> 目标节点ID，兼容值为JSON字符串的存量数据）
     * @return
     */
    public Map<String, String> getBranches() {
        return getConfigStringMap("branches");
    }

    /**
     * 设置分支映射
     * @param branches
     */
    public void setBranches(Map<String, String> branches) {
        setConfigValue("branches", branches);
    }
}
