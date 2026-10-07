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
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Streamable HTTP适配包装器
 * @author yangqiong
 */
public class StreamableHttpWrapper implements McpClientWrapper {

    private static final Logger log = LoggerFactory.getLogger(StreamableHttpWrapper.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);
    private static final String ACCEPT_HEADER = "application/json, text/event-stream";
    private static final String PROTOCOL_VERSION = "2025-06-18";

    private final String serverCode;
    private final String endpoint;
    private final Duration timeout;
    private final Map<String, String> authHeaders;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final McpResultExtractor resultExtractor;

    private volatile String sessionId;
    private volatile String negotiatedVersion = PROTOCOL_VERSION;
    private volatile long requestId = 1L;
    private volatile boolean initialized;
    private volatile List<McpToolInfo> cachedTools = List.of();

    public StreamableHttpWrapper(String serverCode,
                                 String endpoint,
                                 Duration timeout,
                                 Map<String, String> authHeaders,
                                 ObjectMapper objectMapper) {
        this.serverCode = serverCode == null ? "" : serverCode;
        this.endpoint = endpoint;
        this.timeout = timeout == null ? DEFAULT_TIMEOUT : timeout;
        this.authHeaders = authHeaders == null ? Map.of() : Map.copyOf(authHeaders);
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(this.timeout)
                .build();
        this.resultExtractor = new McpResultExtractor(objectMapper);
    }

    /**
     * 初始化连接并发现工具
     */
    public void initialize() {
        if (initialized) {
            return;
        }
        performHandshake();
        // 握手完成后立即获取工具列表
        this.cachedTools = doListTools();
        initialized = true;
        log.info("StreamableHttp客户端初始化完成: serverCode={}, endpoint={}", serverCode, endpoint);
    }

    /**
     * 列出可用工具
     * @return
     */
    @Override
    public List<McpToolInfo> listTools() {
        ensureInitialized();
        this.cachedTools = doListTools();
        return cachedTools;
    }

    /**
     * 调用工具
     * @param toolName
     * @param arguments
     * @return
     */
    @Override
    public String callTool(String toolName, String arguments) {
        ensureInitialized();
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("name", toolName);
        // arguments为JSON字符串，解析为对象
        if (arguments != null && !arguments.trim().isEmpty()) {
            try {
                params.put("arguments", objectMapper.readValue(arguments, Map.class));
            } catch (Exception e) {
                params.put("arguments", Map.of());
            }
        } else {
            params.put("arguments", Map.of());
        }
        JsonNode result = dispatchRequest("tools/call", objectMapper.valueToTree(params));
        return resultExtractor.normalizeToText(result);
    }

    @Override
    public CompletableFuture<List<McpToolInfo>> listToolsAsync() {
        return CompletableFuture.supplyAsync(this::listTools);
    }

    @Override
    public CompletableFuture<String> callToolAsync(String toolName, String arguments) {
        return CompletableFuture.supplyAsync(() -> callTool(toolName, arguments));
    }

    @Override
    public void close() {
        if (sessionId != null && !sessionId.isBlank()) {
            try {
                HttpRequest.Builder reqBuilder = createBaseRequestBuilder().DELETE();
                httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (Exception e) {
                log.debug("关闭StreamableHttp会话异常: endpoint={}", endpoint, e);
            }
        }
        sessionId = null;
        initialized = false;
    }

    @Override
    public String getServerCode() {
        return serverCode;
    }

    /**
     * 是否已初始化
     * @return
     */
    public boolean isInitialized() {
        return initialized;
    }

    private synchronized void performHandshake() {
        if (initialized) {
            return;
        }
        // 发送initialize请求
        ObjectNode initParams = objectMapper.createObjectNode();
        initParams.put("protocolVersion", negotiatedVersion);
        initParams.set("capabilities", objectMapper.createObjectNode());
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "maizi-ai-agent");
        clientInfo.put("version", "1.0.0");
        initParams.set("clientInfo", clientInfo);

        RpcOutcome initOutcome = executeRpc("initialize", initParams, true);
        JsonNode initResult = initOutcome.payload;
        // 协商协议版本
        JsonNode versionNode = initResult.path("protocolVersion");
        if (versionNode.isTextual() && !versionNode.asText().isBlank()) {
            negotiatedVersion = versionNode.asText().trim();
        }
        // 记录sessionId
        if (initOutcome.sessionId != null && !initOutcome.sessionId.isBlank()) {
            sessionId = initOutcome.sessionId.trim();
        }
        // 发送initialized通知
        executeRpc("notifications/initialized", objectMapper.createObjectNode(), false);
    }

    private List<McpToolInfo> doListTools() {
        JsonNode result = dispatchRequest("tools/list", objectMapper.createObjectNode());
        return resultExtractor.parseToolList(result, serverCode);
    }

    private JsonNode dispatchRequest(String method, JsonNode params) {
        return executeRpc(method, params, true).payload;
    }

    private RpcOutcome executeRpc(String method, JsonNode params, boolean expectResponse) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.put("jsonrpc", "2.0");
            if (expectResponse) {
                payload.put("id", nextRequestId());
            }
            payload.put("method", method);
            if (params != null && !params.isMissingNode()) {
                payload.set("params", params);
            }

            HttpRequest.Builder reqBuilder = createBaseRequestBuilder()
                    .POST(HttpRequest.BodyPublishers.ofString(
                            objectMapper.writeValueAsString(payload), StandardCharsets.UTF_8));

            HttpResponse<String> response = httpClient.send(reqBuilder.build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

            String contentType = extractHeader(response, "Content-Type").toLowerCase(Locale.ROOT);
            String responseSessionId = extractHeader(response, "MCP-Session-Id");
            if (!responseSessionId.isBlank()) {
                sessionId = responseSessionId.trim();
            }

            // 通知请求的兼容处理：202 + text/plain 视为成功
            if (!expectResponse) {
                if (response.statusCode() == 202) {
                    return RpcOutcome.empty(responseSessionId);
                }
                if (response.statusCode() >= 400) {
                    throw new RuntimeException("MCP通知请求失败: HTTP " + response.statusCode());
                }
                if (contentType.contains("text/plain") && (response.body() == null || response.body().isBlank())) {
                    return RpcOutcome.empty(responseSessionId);
                }
            }

            if (response.statusCode() >= 400) {
                throw new RuntimeException("MCP请求失败: HTTP " + response.statusCode()
                        + " " + (response.body() == null ? "" : response.body()));
            }

            String rawBody = response.body() == null ? "" : response.body();

            // 非通知请求的text/plain兼容
            if (!expectResponse && contentType.contains("text/plain")) {
                return RpcOutcome.empty(responseSessionId);
            }

            // 解析响应体
            JsonNode result;
            if (contentType.contains("text/event-stream")) {
                result = parseSseResponse(rawBody, method);
            } else if (contentType.contains("application/json")) {
                result = parseJsonResponse(rawBody, method, expectResponse);
            } else {
                throw new RuntimeException("不支持的响应媒体类型: " + contentType);
            }

            return new RpcOutcome(result, responseSessionId);
        } catch (RuntimeException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("MCP请求被中断", e);
        } catch (Exception e) {
            throw new RuntimeException("MCP请求解析失败", e);
        }
    }

    private JsonNode parseSseResponse(String rawBody, String method) {
        List<JsonNode> events = resultExtractor.parseSseEvents(rawBody);
        JsonNode terminalResult = null;
        for (JsonNode event : events) {
            JsonNode unwrapped = resultExtractor.unwrapJsonRpcEnvelope(event);
            if (unwrapped != null) {
                terminalResult = unwrapped;
            }
        }
        if (terminalResult == null) {
            throw new RuntimeException("SSE响应缺少有效结果: method=" + method);
        }
        return terminalResult;
    }

    private JsonNode parseJsonResponse(String rawBody, String method, boolean expectResponse) {
        if (rawBody.trim().isEmpty()) {
            if (!expectResponse) {
                return objectMapper.createObjectNode();
            }
            throw new RuntimeException("MCP响应体为空: method=" + method);
        }
        try {
            JsonNode document = objectMapper.readTree(rawBody);
            return resultExtractor.unwrapJsonRpcEnvelope(document);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("MCP JSON响应解析失败: method=" + method, e);
        }
    }

    private HttpRequest.Builder createBaseRequestBuilder() {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .timeout(timeout)
                .header("Accept", ACCEPT_HEADER)
                .header("Content-Type", "application/json")
                .header("Cache-Control", "no-cache")
                .header("MCP-Protocol-Version", negotiatedVersion);
        if (sessionId != null && !sessionId.isBlank()) {
            builder.header("MCP-Session-Id", sessionId);
        }
        authHeaders.forEach(builder::header);
        return builder;
    }

    private void ensureInitialized() {
        if (!initialized) {
            initialize();
        }
    }

    private synchronized long nextRequestId() {
        return requestId++;
    }

    private static String extractHeader(HttpResponse<?> response, String name) {
        if (response == null || name == null) {
            return "";
        }
        return response.headers().firstValue(name).orElse("");
    }

    private record RpcOutcome(JsonNode payload, String sessionId) {

        static RpcOutcome empty(String sessionId) {
            return new RpcOutcome(null, sessionId);
        }
    }
}
