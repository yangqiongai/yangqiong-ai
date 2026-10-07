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
package com.yangqiongai.ai.agent.tool;

import com.alibaba.fastjson.JSON;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.common.util.AiJsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.*;

/**
 * 反射式工具适配器
 * <p>
 * 将标注了自定义 @AgentTool 注解的方法通过反射适配为 SDK 的 AgentTool 接口，
 * 解决 SDK registerTool 不识别自定义注解导致工具未注册的问题。
 * </p>
 * @author yangqiong
 */
public class AgentToolAdapter implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(AgentToolAdapter.class);

    private final Object toolObject;
    private final Method method;
    private final String toolName;
    private final String toolDescription;
    private final Map<String, Object> parametersSchema;

    public AgentToolAdapter(Object toolObject, Method method) {
        if (toolObject == null) {
            throw new IllegalArgumentException("toolObject不允许为空");
        }
        if (method == null) {
            throw new IllegalArgumentException("method不允许为空");
        }
        this.toolObject = toolObject;
        this.method = method;
        this.method.setAccessible(true);

        com.yangqiongai.ai.agent.tool.AgentTool annotation =
                method.getAnnotation(com.yangqiongai.ai.agent.tool.AgentTool.class);
        if (annotation != null && !annotation.name().isBlank()) {
            this.toolName = annotation.name();
        } else {
            this.toolName = method.getName();
        }
        if (annotation != null && !annotation.value().isBlank()) {
            this.toolDescription = annotation.value();
        } else {
            this.toolDescription = method.getName();
        }
        this.parametersSchema = buildParametersSchema(method);
    }

    @Override
    public String getName() {
        return toolName;
    }

    @Override
    public String getDescription() {
        return toolDescription;
    }

    @Override
    public Map<String, Object> getParameters() {
        return parametersSchema;
    }

    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        return Mono.defer(() -> {
            try {
                Object[] args = resolveArguments(param.getInput());
                Object result = method.invoke(toolObject, args);
                String resultText = result != null ? AiJsonUtils.toJson(result) : "";
                return Mono.just(AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(resultText).build())));
            } catch (Exception e) {
                Throwable cause = e.getCause() != null ? e.getCause() : e;
                log.error("工具调用失败: tool={}, method={}", toolName, method.getName(), cause);
                return Mono.just(AgentToolResultBlock.error("工具调用失败: " + cause.getMessage()));
            }
        });
    }

    /**
     * 从输入参数映射解析方法调用参数
     * @param input
     * @return
     */
    private Object[] resolveArguments(Map<String, Object> input) {
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            return new Object[0];
        }
        Object[] args = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            String paramName = params[i].getName();
            Object value = input != null ? input.get(paramName) : null;
            args[i] = convertType(value, params[i].getType());
        }
        return args;
    }

    /**
     * 类型转换
     * @param value
     * @param targetType
     * @return
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) {
            return getDefaultValue(targetType);
        }
        if (targetType.isInstance(value)) {
            return value;
        }
        if (targetType == String.class) {
            return String.valueOf(value);
        }
        if (targetType == int.class || targetType == Integer.class) {
            if (value instanceof Number n) return n.intValue();
            return Integer.parseInt(String.valueOf(value));
        }
        if (targetType == long.class || targetType == Long.class) {
            if (value instanceof Number n) return n.longValue();
            return Long.parseLong(String.valueOf(value));
        }
        if (targetType == double.class || targetType == Double.class) {
            if (value instanceof Number n) return n.doubleValue();
            return Double.parseDouble(String.valueOf(value));
        }
        if (targetType == boolean.class || targetType == Boolean.class) {
            if (value instanceof Boolean b) return b;
            return Boolean.parseBoolean(String.valueOf(value));
        }
        if (targetType == float.class || targetType == Float.class) {
            if (value instanceof Number n) return n.floatValue();
            return Float.parseFloat(String.valueOf(value));
        }
        // 复杂类型通过JSON中转
        try {
            String json = JSON.toJSONString(value);
            return JSON.parseObject(json, targetType);
        } catch (Exception e) {
            log.warn("参数类型转换失败: targetType={}, value={}", targetType.getSimpleName(), value);
            return getDefaultValue(targetType);
        }
    }

    /**
     * 获取基本类型的默认值
     * @param type
     * @return
     */
    private Object getDefaultValue(Class<?> type) {
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == double.class) return 0.0;
        if (type == float.class) return 0.0f;
        if (type == boolean.class) return false;
        return null;
    }

    /**
     * 根据方法签名构建JSON Schema格式的参数描述
     * <p>
     * 优先使用 {@link AgentToolParam} 注解中的描述，无注解时回退到参数名。
     * </p>
     * @param method
     * @return
     */
    private Map<String, Object> buildParametersSchema(Method method) {
        Parameter[] params = method.getParameters();
        if (params.length == 0) {
            return Map.of("type", "object", "properties", Collections.emptyMap());
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();

        for (Parameter param : params) {
            String paramName = param.getName();
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", mapJavaTypeToJsonType(param.getType()));
            // 优先使用 @AgentToolParam 注解的描述
            AgentToolParam paramAnn = param.getAnnotation(AgentToolParam.class);
            if (paramAnn != null && !paramAnn.value().isBlank()) {
                prop.put("description", paramAnn.value());
            } else {
                prop.put("description", paramName);
            }
            properties.put(paramName, prop);
            // 非必填参数不加入 required 列表
            if (paramAnn == null || paramAnn.required()) {
                required.add(paramName);
            }
        }

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }
        return schema;
    }

    /**
     * Java类型映射到JSON Schema类型
     * @param javaType
     * @return
     */
    private String mapJavaTypeToJsonType(Class<?> javaType) {
        if (javaType == String.class) return "string";
        if (javaType == int.class || javaType == Integer.class) return "integer";
        if (javaType == long.class || javaType == Long.class) return "integer";
        if (javaType == double.class || javaType == Double.class) return "number";
        if (javaType == float.class || javaType == Float.class) return "number";
        if (javaType == boolean.class || javaType == Boolean.class) return "boolean";
        if (javaType == List.class || javaType.isArray()) return "array";
        if (javaType == Map.class) return "object";
        return "string";
    }
}
