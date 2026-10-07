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

import java.util.List;
import java.util.Map;

/**
 * 数据变换节点（无需Agent，支持字符串/数值变换）
 * <p>
 * 支持的变换类型（transformType）：
 * - SUBSTRING: 截取子串，config: {"start": 1, "end": 5}（end可选，不传则到末尾）
 * - REVERSE: 字符串逆序
 * - UPPER: 转大写
 * - LOWER: 转小写
 * - TRIM: 去首尾空白
 * - REPLACE: 替换，config: {"pattern": "old", "replacement": "new"}
 * - CONCAT: 拼接，config: {"prefix": "前缀", "suffix": "后缀"}（prefix/suffix至少一个）
 * - TEMPLATE: 模板渲染，config: {"template": "Hello ${name}, score=${score}"}
 * - LENGTH: 取字符串长度
 * - MATH: 数学运算，config: {"expression": "${x} + ${y}"}（支持+,-,*,/,%）
 * <p>
 * 输入源通过 inputMappings 指定，输出通过 outputMappings 写入变量。
 * 如果没有 inputMappings，默认读取变量 "input"。
 *
 * @author yangqiong
 */
public class TransformNode extends WorkflowNode {

    /**
     * 获取变换类型
     */
    public String getTransformType() {
        return getConfigString("transformType");
    }

    /**
     * 设置变换类型
     */
    public void setTransformType(String transformType) {
        setConfigValue("transformType", transformType);
    }

    /**
     * 获取变换配置参数
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getTransformConfig() {
        Object value = getConfigValue("transformConfig");
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return null;
    }

    /**
     * 设置变换配置参数
     */
    public void setTransformConfig(Map<String, Object> config) {
        setConfigValue("transformConfig", config);
    }

    /**
     * 获取输入变量名（兼容旧配置，优先使用inputMappings）
     */
    public String getInputVar() {
        return getConfigString("inputVar");
    }

    /**
     * 设置输入变量名
     */
    public void setInputVar(String inputVar) {
        setConfigValue("inputVar", inputVar);
    }

    /**
     * 获取输出变量名（兼容旧配置，优先使用outputMappings）
     */
    public String getOutputVar() {
        return getConfigString("outputVar");
    }

    /**
     * 设置输出变量名
     */
    public void setOutputVar(String outputVar) {
        setConfigValue("outputVar", outputVar);
    }
}
