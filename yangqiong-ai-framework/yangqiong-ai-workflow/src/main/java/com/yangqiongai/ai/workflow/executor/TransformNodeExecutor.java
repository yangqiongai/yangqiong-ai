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

import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.model.TransformNode;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据变换节点执行
 * @author yangqiong
 */
@Service
public class TransformNodeExecutor {

    private static final Logger log = LoggerFactory.getLogger(TransformNodeExecutor.class);

    private static final ExpressionParser spelParser = new SpelExpressionParser();

    private final WorkflowStateService stateService;

    public TransformNodeExecutor(WorkflowStateService stateService) {
        this.stateService = stateService;
    }

    /**
     * 执行数据变换节点
     * 支持：SUBSTRING, REVERSE, UPPER, LOWER, TRIM, REPLACE, CONCAT, TEMPLATE, LENGTH, MATH
     * @param state
     * @param node
     * @return
     */
    public AgentResult executeTransformNode(WorkflowState state, WorkflowNode node) {
        TransformNode transformNode = stateService.castNode(node, TransformNode.class);
        String transformType = transformNode.getTransformType();
        if (transformType == null || transformType.isBlank()) {
            return AgentResult.failure("变换节点缺少transformType配置: " + node.getId());
        }

        // 获取输入值：优先从inputMappings取，否则从inputVar取，否则从变量"input"取
        Object inputValue = resolveTransformInput(transformNode, state);
        Map<String, Object> transformConfig = transformNode.getTransformConfig();

        try {
            Object result;
            switch (transformType.toUpperCase()) {
                case "SUBSTRING" -> result = transformSubstring(inputValue, transformConfig);
                case "REVERSE" -> result = transformReverse(inputValue);
                case "UPPER" -> result = transformUpper(inputValue);
                case "LOWER" -> result = transformLower(inputValue);
                case "TRIM" -> result = transformTrim(inputValue);
                case "REPLACE" -> result = transformReplace(inputValue, transformConfig);
                case "CONCAT" -> result = transformConcat(inputValue, transformConfig);
                case "TEMPLATE" -> result = transformTemplate(transformConfig, state);
                case "LENGTH" -> result = transformLength(inputValue);
                case "MATH" -> result = transformMath(transformConfig, state);
                default -> {
                    return AgentResult.failure("不支持的变换类型: " + transformType);
                }
            }

            String resultStr = result != null ? result.toString() : "";
            // 写入输出变量：优先outputMappings（由resolveNodeOutput处理），否则写入outputVar或{nodeId}.output
            String outputVar = transformNode.getOutputVar();
            if (outputVar != null && !outputVar.isBlank()) {
                state.setVariable(outputVar, result);
            }
            // 同时写入 nodeId.output 和 nodeId.result 供下游引用
            state.setVariable(node.getId() + ".output", resultStr);
            state.setVariable(node.getId() + ".result", result);

            return AgentResult.success(resultStr);
        } catch (Exception e) {
            return AgentResult.failure("变换执行失败[" + transformType + "]: " + e.getMessage());
        }
    }

    /**
     * 解析变换节点的输入值
     * @param node
     * @param state
     * @return
     */
    private Object resolveTransformInput(TransformNode node, WorkflowState state) {
        // 优先从inputMappings取第一个值
        if (node.getInputMappings() != null && !node.getInputMappings().isEmpty()) {
            String firstMapping = node.getInputMappings().values().iterator().next();
            return stateService.resolveVariableReference(firstMapping, state);
        }
        // 其次从inputVar取
        String inputVar = node.getInputVar();
        if (inputVar != null && !inputVar.isBlank()) {
            return state.getVariable(inputVar);
        }
        // 默认从变量"input"取
        return state.getVariable("input");
    }

    private String transformSubstring(Object input, Map<String, Object> config) {
        String str = input != null ? input.toString() : "";
        int start = config != null && config.get("start") != null
                ? ((Number) config.get("start")).intValue() : 0;
        Integer end = config != null && config.get("end") != null
                ? ((Number) config.get("end")).intValue() : null;
        if (end != null) {
            return str.substring(start, Math.min(end, str.length()));
        }
        return str.substring(Math.min(start, str.length()));
    }

    private String transformReverse(Object input) {
        String str = input != null ? input.toString() : "";
        return new StringBuilder(str).reverse().toString();
    }

    private String transformUpper(Object input) {
        return input != null ? input.toString().toUpperCase() : "";
    }

    private String transformLower(Object input) {
        return input != null ? input.toString().toLowerCase() : "";
    }

    private String transformTrim(Object input) {
        return input != null ? input.toString().trim() : "";
    }

    private String transformReplace(Object input, Map<String, Object> config) {
        String str = input != null ? input.toString() : "";
        if (config == null) return str;
        String pattern = config.get("pattern") != null ? config.get("pattern").toString() : "";
        String replacement = config.get("replacement") != null ? config.get("replacement").toString() : "";
        return str.replace(pattern, replacement);
    }

    private String transformConcat(Object input, Map<String, Object> config) {
        String str = input != null ? input.toString() : "";
        if (config == null) return str;
        String prefix = config.get("prefix") != null ? config.get("prefix").toString() : "";
        String suffix = config.get("suffix") != null ? config.get("suffix").toString() : "";
        return prefix + str + suffix;
    }

    /**
     * 模板渲染：将 ${varName} 替换为变量值
     * @param config
     * @param state
     * @return
     */
    private String transformTemplate(Map<String, Object> config, WorkflowState state) {
        if (config == null || config.get("template") == null) {
            return "";
        }
        String template = config.get("template").toString();
        // 替换 ${varName} 为变量值
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            if (i + 1 < template.length() && template.charAt(i) == '$' && template.charAt(i + 1) == '{') {
                int end = template.indexOf('}', i + 2);
                if (end > 0) {
                    String varName = template.substring(i + 2, end);
                    Object value = state.getVariable(varName);
                    result.append(value != null ? value.toString() : "");
                    i = end + 1;
                    continue;
                }
            }
            result.append(template.charAt(i));
            i++;
        }
        return result.toString();
    }

    private int transformLength(Object input) {
        return input != null ? input.toString().length() : 0;
    }

    /**
     * 数学运算：支持 +, -, *, /, %
     * expression 中 ${var} 替换为变量值后求值
     * @param config
     * @param state
     * @return
     */
    private Object transformMath(Map<String, Object> config, WorkflowState state) {
        if (config == null || config.get("expression") == null) {
            return 0;
        }
        String expression = config.get("expression").toString();
        // 替换变量引用
        String resolved = expression;
        Matcher matcher = Pattern.compile("\\$\\{(\\w[\\w.]*)}").matcher(expression);
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = state.getVariable(varName);
            resolved = resolved.replace("${" + varName + "}", value != null ? value.toString() : "0");
        }
        // 简单安全过滤：只允许数字和运算符
        resolved = resolved.replaceAll("[^0-9+\\-*/.%\\s]", "");
        // 使用SpEL求值
        try {
            Object evalResult = spelParser.parseExpression(resolved).getValue();
            if (evalResult instanceof Number n) {
                double d = n.doubleValue();
                if (d == Math.floor(d) && !Double.isInfinite(d)) {
                    return (long) d;
                }
                return d;
            }
            return evalResult;
        } catch (Exception e) {
            log.warn("数学表达式求值失败: expression={}, error={}", resolved, e.getMessage());
        }
        // 最简fallback：尝试解析为数字
        try {
            return Double.parseDouble(resolved.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
