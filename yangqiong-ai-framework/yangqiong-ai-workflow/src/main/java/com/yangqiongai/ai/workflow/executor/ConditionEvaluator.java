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

import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 条件表达式评估器
 * 支持: ==, !=, >, <, >=, <=, contains, startsWith, endsWith
 * 支持: ${variable} 变量引用
 * @author yangqiong
 */
@Component
public class ConditionEvaluator {

    private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{([\\w.]+)}");

    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "\\$\\{([\\w.]+)}\\s*(==|!=|>=|<=|>|<|contains|startsWith|endsWith)\\s*(.+)"
    );

    /**
     * 评估条件表达式，返回匹配的分支键
     * 比较表达式为真返回true、为假返回false（供true/false分支映射路由）
     * @param expression
     * @param state
     * @return
     */
    public String evaluate(String expression, WorkflowState state) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        String trimmed = expression.trim();

        // 尝试比较表达式
        Matcher compMatcher = COMPARISON_PATTERN.matcher(trimmed);
        if (compMatcher.matches()) {
            String varName = compMatcher.group(1);
            String operator = compMatcher.group(2);
            String expectedValue = stripQuotes(compMatcher.group(3).trim());
            Object actualValue = resolveVariable(state, varName);

            return evaluateComparison(actualValue, operator, expectedValue) ? "true" : "false";
        }

        // 简单变量引用
        Matcher varMatcher = VARIABLE_PATTERN.matcher(trimmed);
        if (varMatcher.matches()) {
            String varName = varMatcher.group(1);
            Object value = resolveVariable(state, varName);
            return value != null ? value.toString() : null;
        }

        // 纯文本（如分支标签）原样返回
        return expression;
    }

    /**
     * 评估退出条件（返回boolean）
     */
    public boolean evaluateExitCondition(String exitCondition, WorkflowState state) {
        if (exitCondition == null || exitCondition.isEmpty()) {
            return false;
        }

        String trimmed = exitCondition.trim();

        Matcher compMatcher = COMPARISON_PATTERN.matcher(trimmed);
        if (compMatcher.matches()) {
            String varName = compMatcher.group(1);
            String operator = compMatcher.group(2);
            String expectedValue = stripQuotes(compMatcher.group(3).trim());
            Object actualValue = resolveVariable(state, varName);
            return evaluateComparison(actualValue, operator, expectedValue);
        }

        Matcher varMatcher = VARIABLE_PATTERN.matcher(trimmed);
        if (varMatcher.matches()) {
            String varName = varMatcher.group(1);
            Object value = resolveVariable(state, varName);
            if (value == null) return false;
            if (value instanceof Boolean boolVal) return boolVal;
            return !"false".equalsIgnoreCase(value.toString());
        }

        return false;
    }

    /**
     * 解析变量引用：优先按完整变量名取扁平变量，未命中且含.时逐级取嵌套属性（如input.length取字符串长度）
     * @param state
     * @param varName
     * @return
     */
    private Object resolveVariable(WorkflowState state, String varName) {
        Object value = state.getVariable(varName);
        if (value != null || !varName.contains(".")) {
            return value;
        }
        String[] parts = varName.split("\\.");
        Object current = state.getVariable(parts[0]);
        for (int i = 1; i < parts.length && current != null; i++) {
            current = extractProperty(current, parts[i]);
        }
        return current;
    }

    /**
     * 提取嵌套属性（字符串取length/size，集合取size，Map按键取值）
     * @param target
     * @param name
     * @return
     */
    private Object extractProperty(Object target, String name) {
        if (target instanceof String str) {
            if ("length".equals(name) || "size".equals(name)) {
                return str.length();
            }
            return null;
        }
        if (target instanceof java.util.Collection<?> collection) {
            if ("length".equals(name) || "size".equals(name)) {
                return collection.size();
            }
            return null;
        }
        if (target instanceof Map<?, ?> map) {
            return map.get(name);
        }
        return null;
    }

    /**
     * 去除期望值首尾引号（支持 ${name} == "VIP" 写法）
     * @param value
     * @return
     */
    private String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private boolean evaluateComparison(Object actualValue, String operator, String expectedValue) {
        String actualStr = actualValue != null ? actualValue.toString() : null;

        return switch (operator) {
            case "==" -> actualStr != null && actualStr.equals(expectedValue);
            case "!=" -> actualStr == null || !actualStr.equals(expectedValue);
            case ">" -> compareNumbers(actualStr, expectedValue) > 0;
            case "<" -> compareNumbers(actualStr, expectedValue) < 0;
            case ">=" -> compareNumbers(actualStr, expectedValue) >= 0;
            case "<=" -> compareNumbers(actualStr, expectedValue) <= 0;
            case "contains" -> actualStr != null && actualStr.contains(expectedValue);
            case "startsWith" -> actualStr != null && actualStr.startsWith(expectedValue);
            case "endsWith" -> actualStr != null && actualStr.endsWith(expectedValue);
            default -> {
                log.warn("不支持的比较运算符: {}", operator);
                yield false;
            }
        };
    }

    private int compareNumbers(String actual, String expected) {
        if (actual == null) {
            return -1;
        }
        try {
            double a = Double.parseDouble(actual);
            double b = Double.parseDouble(expected);
            return Double.compare(a, b);
        } catch (NumberFormatException e) {
            // 如果不是数字，按字符串比较
            return actual.compareTo(expected);
        }
    }
}
