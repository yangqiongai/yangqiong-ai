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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * 工作流节点
 * @author yangqiong
 */
@Data
public class WorkflowNode {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {
    };

    /**
     * 节点ID
     */
    private String id;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点类型
     */
    private NodeType type;

    /**
     * 节点配置
     */
    private Map<String, Object> config;

    /**
     * 位置信息（前端渲染用）
     */
    private NodePosition position;

    /**
     * 输入映射：定义该节点从工作流变量中读取哪些值作为输入
     * key=节点输入参数名, value=变量引用表达式（如 "${upstreamNodeId.output}" 或 "${score}"）
     * 示例：{"prompt": "${agent1.output}", "threshold": "${score}"}
     */
    private Map<String, String> inputMappings;

    /**
     * 输出映射：定义该节点的输出如何写入工作流变量
     * key=输出字段名, value=写入的工作流变量名
     * 示例：{"result": "analysisResult", "score": "finalScore"}
     * 如果不配置，默认将 output 写入 {nodeId}.output
     */
    private Map<String, String> outputMappings;

    /**
     * 节点审批配置，非空时节点执行前触发审批
     */
    private NodeApprovalConfig approvalConfig;

    /**
     * 时间控制配置，TIME_CONTROL节点的主配置
     */
    private NodeTimeControlConfig timeControlConfig;

    /**
     * 节点级超时时间（秒），非空时覆盖定义级nodeTimeoutSeconds
     */
    private Integer timeoutSeconds;

    /**
     * 节点级最大重试次数，非空时覆盖定义级maxRetries
     */
    private Integer maxRetries;

    /**
     * 获取配置值
     * @param key
     * @return
     */
    public Object getConfigValue(String key) {
        return config != null ? config.get(key) : null;
    }

    /**
     * 设置配置值
     * @param key
     * @param value
     */
    public void setConfigValue(String key, Object value) {
        if (config == null) {
            config = new HashMap<>();
        }
        config.put(key, value);
    }

    /**
     * 获取字符串配置值
     * @param key
     * @return
     */
    public String getConfigString(String key) {
        Object value = getConfigValue(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 获取整型配置值
     * @param key
     * @return
     */
    public Integer getConfigInt(String key) {
        Object value = getConfigValue(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    /**
     * 获取字符串Map配置值（值为JSON字符串的存量数据自动解析，解析失败返回null）
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getConfigStringMap(String key) {
        Object value = getConfigValue(key);
        if (value instanceof Map) {
            return (Map<String, String>) value;
        }
        if (value instanceof String && !((String) value).isBlank()) {
            try {
                return OBJECT_MAPPER.readValue((String) value, STRING_MAP_TYPE);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }
}
