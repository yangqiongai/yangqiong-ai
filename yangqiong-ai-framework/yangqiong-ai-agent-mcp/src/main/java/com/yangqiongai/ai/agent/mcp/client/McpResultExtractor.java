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
package com.yangqiongai.ai.agent.mcp.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

/**
 * MCP协议响应解析器
 * @author yangqiong
 */
public class McpResultExtractor {

    private static final Logger log = LoggerFactory.getLogger(McpResultExtractor.class);

    private static final Set<String> JSON_RPC_ENVELOPE_FIELDS = Set.of(
            "jsonrpc", "id", "method", "params", "result", "error"
    );

    private final ObjectMapper objectMapper;

    public McpResultExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 提取工具调用结果
     * @param response
     * @return
     */
    public Object extractToolResult(Object response) {
        if (response == null) {
            return null;
        }
        if (response instanceof JsonNode node) {
            return resolveJsonNode(node);
        }
        if (response instanceof String text) {
            return parseRawBody(text);
        }
        return response;
    }

    /**
     * 解析SSE事件文本
     * @param sseText
     * @return
     */
    public List<JsonNode> parseSseEvents(String sseText) {
        if (sseText == null || sseText.trim().isEmpty()) {
            return List.of();
        }
        List<String> payloads = collectSsePayloads(sseText);
        List<JsonNode> documents = new ArrayList<>();
        for (String payload : payloads) {
            String candidate = payload.trim();
            if (candidate.isEmpty() || "[DONE]".equals(candidate)) {
                continue;
            }
            try {
                documents.add(objectMapper.readTree(candidate));
            } catch (Exception e) {
                log.debug("忽略非JSON的SSE数据: {}", abbreviate(candidate, 256));
            }
        }
        return documents;
    }

    /**
     * 判断响应是否完整
     * @param response
     * @return
     */
    public boolean isCompleteResponse(Object response) {
        if (!(response instanceof JsonNode node)) {
            return response != null;
        }
        if (node.isObject()) {
            // 包含error字段视为终态
            JsonNode errorNode = node.get("error");
            if (errorNode != null && !errorNode.isNull() && !errorNode.isEmpty()) {
                return true;
            }
            // 包含result字段视为终态
            JsonNode resultNode = node.get("result");
            return resultNode != null && !resultNode.isNull();
        }
        return true;
    }

    /**
     * 从JSON-RPC信封中提取result
     * @param document
     * @return
     */
    public JsonNode unwrapJsonRpcEnvelope(JsonNode document) {
        if (document == null || !document.isObject()) {
            return document;
        }
        // 优先提取error
        JsonNode errorNode = document.get("error");
        if (errorNode != null && !errorNode.isNull() && !errorNode.isEmpty()) {
            throw new RuntimeException("MCP请求失败: " + buildErrorMessage(errorNode));
        }
        // 提取result
        JsonNode resultNode = document.get("result");
        if (resultNode != null && !resultNode.isNull()) {
            return resultNode;
        }
        // 通知类消息忽略
        if (document.has("method") || document.has("params")) {
            return null;
        }
        // 去除JSON-RPC请求数据体后返回
        return stripEnvelopeMetadata(document);
    }

    /**
     * 归一化工具调用结果为文本
     * @param resultNode
     * @return
     */
    public String normalizeToText(JsonNode resultNode) {
        if (resultNode == null || resultNode.isNull() || resultNode.isMissingNode()) {
            return "";
        }
        if (resultNode.isTextual() || resultNode.isNumber() || resultNode.isBoolean()) {
            return resultNode.asText();
        }
        if (resultNode.isObject()) {
            JsonNode textNode = resultNode.get("text");
            if (textNode != null && textNode.isTextual() && !textNode.asText().isBlank()) {
                return textNode.asText();
            }
            JsonNode messageNode = resultNode.get("message");
            if (messageNode != null && messageNode.isTextual() && !messageNode.asText().isBlank()) {
                return messageNode.asText();
            }
        }
        return resultNode.toString();
    }

    /**
     * 从tools/list响应中解析工具列表
     * @param resultNode
     * @param serverCode
     * @return
     */
    public List<McpToolInfo> parseToolList(JsonNode resultNode, String serverCode) {
        if (resultNode == null || !resultNode.has("tools")) {
            return List.of();
        }
        JsonNode toolsArray = resultNode.get("tools");
        if (!toolsArray.isArray()) {
            return List.of();
        }
        return StreamSupport.stream(toolsArray.spliterator(), false)
                .filter(node -> node.has("name") && !node.get("name").asText().isBlank())
                .map(node -> new McpToolInfo(
                        node.get("name").asText(),
                        node.has("description") ? node.get("description").asText("") : "",
                        node.has("inputSchema") ? node.get("inputSchema").toString() : "",
                        serverCode
                ))
                .toList();
    }

    private Object resolveJsonNode(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isInt()) {
            return node.asInt();
        }
        if (node.isLong()) {
            return node.asLong();
        }
        if (node.isDouble()) {
            return node.asDouble();
        }
        return node;
    }

    private Object parseRawBody(String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(trimmed);
            return resolveJsonNode(node);
        } catch (Exception e) {
            return trimmed;
        }
    }

    private List<String> collectSsePayloads(String body) {
        List<String> payloads = new ArrayList<>();
        StringBuilder currentData = new StringBuilder();
        boolean hasData = false;
        for (String line : body.split("\\r?\\n", -1)) {
            if (line.isEmpty()) {
                if (hasData) {
                    payloads.add(currentData.toString());
                    currentData.setLength(0);
                    hasData = false;
                }
                continue;
            }
            // 注释行跳过
            if (line.startsWith(":")) {
                continue;
            }
            if (line.startsWith("data:")) {
                if (hasData) {
                    currentData.append('\n');
                }
                currentData.append(line.substring("data:".length()).stripLeading());
                hasData = true;
            }
        }
        if (hasData) {
            payloads.add(currentData.toString());
        }
        return payloads;
    }

    private JsonNode stripEnvelopeMetadata(JsonNode document) {
        if (!document.isObject()) {
            return document;
        }
        com.fasterxml.jackson.databind.node.ObjectNode stripped = objectMapper.createObjectNode();
        document.fields().forEachRemaining(entry -> {
            if (!JSON_RPC_ENVELOPE_FIELDS.contains(entry.getKey())) {
                stripped.set(entry.getKey(), entry.getValue());
            }
        });
        return stripped.size() > 0 ? stripped : null;
    }

    private String buildErrorMessage(JsonNode errorNode) {
        String code = errorNode.path("code").isMissingNode() ? "" : errorNode.path("code").asText("");
        String message = errorNode.path("message").asText("");
        String data = errorNode.path("data").isMissingNode() || errorNode.path("data").isNull()
                ? "" : abbreviate(errorNode.path("data").toString(), 256);
        StringBuilder builder = new StringBuilder("MCP请求失败");
        if (!code.isBlank()) {
            builder.append(" [").append(code).append(']');
        }
        if (!message.isBlank()) {
            builder.append(": ").append(message);
        }
        if (!data.isBlank()) {
            builder.append(" data=").append(data);
        }
        return builder.toString();
    }

    private static String abbreviate(String value, int maxLen) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLen - 3)) + "...";
    }
}
