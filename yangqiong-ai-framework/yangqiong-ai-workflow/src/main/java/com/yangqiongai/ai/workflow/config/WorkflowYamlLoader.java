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
package com.yangqiongai.ai.workflow.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.yangqiongai.ai.workflow.model.EdgeType;
import com.yangqiongai.ai.workflow.model.ErrorStrategy;
import com.yangqiongai.ai.workflow.model.NodeApprovalConfig;
import com.yangqiongai.ai.workflow.model.NodePosition;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.StateConfig;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowEdge;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流YAML配置加载器
 * @author yangqiong
 */
@Slf4j
@Component
public class WorkflowYamlLoader {

    private final ObjectMapper yamlMapper;

    public WorkflowYamlLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * 从classpath资源加载工作流定义
     * @param yamlPath
     * @return
     */
    public WorkflowDefinition load(String yamlPath) {
        if (yamlPath.contains("..")) {
            throw new IllegalArgumentException("classpath路径不允许包含路径遍历字符: " + yamlPath);
        }
        try {
            ClassPathResource resource = new ClassPathResource(yamlPath);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String yamlContent = reader.lines().collect(Collectors.joining("\n"));
                return loadFromString(yamlContent);
            }
        } catch (Exception e) {
            log.error("加载classpath工作流定义失败: resourcePath={}", yamlPath, e);
            throw new IllegalArgumentException("classpath资源加载失败: " + yamlPath, e);
        }
    }

    /**
     * 从文件路径加载工作流定义
     * @param filePath
     * @return
     */
    public WorkflowDefinition loadFromFile(Path filePath) {
        // 防止路径遍历攻击
        Path normalized = filePath.normalize();
        if (normalized.toString().contains("..")) {
            throw new IllegalArgumentException("文件路径不允许包含路径遍历字符: " + filePath);
        }
        try {
            String yamlContent = Files.readString(normalized, StandardCharsets.UTF_8);
            return loadFromString(yamlContent);
        } catch (Exception e) {
            log.error("加载文件工作流定义失败: filePath={}", filePath, e);
            throw new IllegalArgumentException("文件加载失败: " + filePath, e);
        }
    }

    /**
     * 从YAML字符串加载工作流定义
     * @param yamlContent
     * @return
     */
    public WorkflowDefinition loadFromString(String yamlContent) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> root = yamlMapper.readValue(yamlContent, Map.class);
            return parseDefinition(root);
        } catch (Exception e) {
            log.error("解析YAML工作流定义失败", e);
            throw new IllegalArgumentException("YAML工作流定义解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从classpath资源加载工作流定义（兼容旧接口）
     * @param resourcePath
     * @return
     */
    public WorkflowDefinition loadFromResource(String resourcePath) {
        return load(resourcePath);
    }

    @SuppressWarnings("unchecked")
    private WorkflowDefinition parseDefinition(Map<String, Object> root) {
        WorkflowDefinition.WorkflowDefinitionBuilder builder = WorkflowDefinition.builder();
        builder.name((String) root.get("name"));
        builder.description((String) root.get("description"));

        if (root.containsKey("errorStrategy")) {
            String strategyName = (String) root.get("errorStrategy");
            try {
                builder.errorStrategy(ErrorStrategy.valueOf(strategyName.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("无效的错误策略: {}, 使用默认STOP", strategyName);
                builder.errorStrategy(ErrorStrategy.STOP);
            }
        }

        if (root.containsKey("maxRetries")) {
            builder.maxRetries(((Number) root.get("maxRetries")).intValue());
        }

        if (root.containsKey("nodeTimeoutSeconds")) {
            builder.nodeTimeoutSeconds(((Number) root.get("nodeTimeoutSeconds")).intValue());
        }

        if (root.containsKey("stateConfig")) {
            Map<String, Object> stateConfigRaw = (Map<String, Object>) root.get("stateConfig");
            StateConfig.StateConfigBuilder stateBuilder = StateConfig.builder();
            if (stateConfigRaw.containsKey("persistEnabled")) {
                stateBuilder.persistEnabled((Boolean) stateConfigRaw.get("persistEnabled"));
            }
            if (stateConfigRaw.containsKey("ttlHours")) {
                stateBuilder.ttlHours(((Number) stateConfigRaw.get("ttlHours")).intValue());
            }
            builder.stateConfig(stateBuilder.build());
        }

        if (root.containsKey("nodes")) {
            List<Map<String, Object>> nodesRaw = (List<Map<String, Object>>) root.get("nodes");
            List<WorkflowNode> nodes = new ArrayList<>();
            for (Map<String, Object> nodeRaw : nodesRaw) {
                nodes.add(parseNode(nodeRaw));
            }
            builder.nodes(nodes);
        }

        if (root.containsKey("edges")) {
            List<Map<String, Object>> edgesRaw = (List<Map<String, Object>>) root.get("edges");
            List<WorkflowEdge> edges = new ArrayList<>();
            for (Map<String, Object> edgeRaw : edgesRaw) {
                edges.add(parseEdge(edgeRaw));
            }
            builder.edges(edges);
        }

        WorkflowDefinition definition = builder.build();
        log.info("加载工作流定义: name={}, nodes={}, edges={}",
                definition.getName(),
                definition.getNodes() != null ? definition.getNodes().size() : 0,
                definition.getEdges() != null ? definition.getEdges().size() : 0);
        return definition;
    }

    /**
     * 解析节点配置
     * @param nodeRaw
     * @return
     */
    @SuppressWarnings("unchecked")
    public WorkflowNode parseNode(Map<String, Object> nodeRaw) {
        String typeStr = (String) nodeRaw.get("type");
        NodeType nodeType;
        try {
            nodeType = NodeType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("无效的节点类型: {}, 使用默认AGENT", typeStr);
            nodeType = NodeType.AGENT;
        }

        WorkflowNode node = createNodeByType(nodeType);
        node.setId((String) nodeRaw.get("id"));
        node.setName((String) nodeRaw.get("name"));
        node.setType(nodeType);

        if (nodeRaw.containsKey("config")) {
            Map<String, Object> config = (Map<String, Object>) nodeRaw.get("config");
            node.setConfig(new HashMap<>(config));
        }

        if (nodeRaw.containsKey("position")) {
            Map<String, Object> posRaw = (Map<String, Object>) nodeRaw.get("position");
            NodePosition position = new NodePosition();
            if (posRaw.containsKey("x")) {
                position.setX(((Number) posRaw.get("x")).doubleValue());
            }
            if (posRaw.containsKey("y")) {
                position.setY(((Number) posRaw.get("y")).doubleValue());
            }
            node.setPosition(position);
        }

        if (nodeRaw.containsKey("approvalConfig")) {
            node.setApprovalConfig(parseApprovalConfig((Map<String, Object>) nodeRaw.get("approvalConfig")));
        }

        return node;
    }

    /**
     * 解析审批配置
     * @param raw
     * @return
     */
    @SuppressWarnings("unchecked")
    private NodeApprovalConfig parseApprovalConfig(Map<String, Object> raw) {
        NodeApprovalConfig config = new NodeApprovalConfig();
        config.setReason((String) raw.get("reason"));
        if (raw.containsKey("options")) {
            config.setOptions((List<String>) raw.get("options"));
        }
        if (raw.containsKey("inputFields")) {
            config.setInputFields((List<String>) raw.get("inputFields"));
        }
        if (raw.containsKey("timeoutSeconds")) {
            config.setTimeoutSeconds(((Number) raw.get("timeoutSeconds")).intValue());
        }
        if (raw.containsKey("rejectBehavior")) {
            try {
                config.setRejectBehavior(NodeApprovalConfig.RejectBehavior.valueOf(
                        ((String) raw.get("rejectBehavior")).toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("无效的rejectBehavior: {}, 使用默认FAIL", raw.get("rejectBehavior"));
            }
        }
        return config;
    }

    /**
     * 解析边配置
     * @param edgeRaw
     * @return
     */
    public WorkflowEdge parseEdge(Map<String, Object> edgeRaw) {
        WorkflowEdge edge = new WorkflowEdge();
        edge.setId((String) edgeRaw.get("id"));
        edge.setSourceId((String) edgeRaw.get("sourceId"));
        edge.setTargetId((String) edgeRaw.get("targetId"));

        if (edgeRaw.containsKey("type")) {
            String typeStr = (String) edgeRaw.get("type");
            try {
                edge.setType(EdgeType.valueOf(typeStr.toUpperCase()));
            } catch (IllegalArgumentException e) {
                log.warn("无效的边类型: {}, 使用默认NORMAL", typeStr);
                edge.setType(EdgeType.NORMAL);
            }
        }

        edge.setConditionExpression((String) edgeRaw.get("conditionExpression"));
        edge.setConditionLabel((String) edgeRaw.get("conditionLabel"));

        return edge;
    }

    /**
     * 根据节点类型创建对应的节点实例
     * @param nodeType
     * @return
     */
    private WorkflowNode createNodeByType(NodeType nodeType) {
        return switch (nodeType) {
            case AGENT -> new com.yangqiongai.ai.workflow.model.AgentNode();
            case CONDITION -> new com.yangqiongai.ai.workflow.model.ConditionNode();
            case PARALLEL -> new com.yangqiongai.ai.workflow.model.ParallelNode();
            case LOOP -> new com.yangqiongai.ai.workflow.model.LoopNode();
            case SUBGRAPH -> new com.yangqiongai.ai.workflow.model.SubgraphNode();
            default -> new WorkflowNode();
        };
    }
}
