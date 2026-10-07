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
package com.yangqiongai.ai.agent.tool.builtin;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 通用HTTP请求工具
 * <p>
 * 提供GET/POST/PUT/DELETE等HTTP方法调用能力，让LLM能调用任意REST API。
 * 使用JDK内置HttpClient，无需额外依赖。
 * 默认超时30秒，支持自定义请求头和请求体。
 * </p>
 * @author yangqiong
 */
@Component
public class HttpRequestTool implements Tool {

    private static final Logger log = LoggerFactory.getLogger(HttpRequestTool.class);

    /**
     * 默认超时时间（秒）
     */
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * 最大响应体大小（1MB）
     */
    private static final int MAX_RESPONSE_SIZE = 1024 * 1024;

    /**
     * HTTP客户端（复用）
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            // API域名迁移后返回301，需跟随重定向
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 是否启用SSRF防护（禁止访问内网地址）
     */
    @Value("${ai.tool.http-request.ssrf-protection-enabled:true}")
    private boolean ssrfProtectionEnabled;

    /**
     * 是否启用HTTP请求工具
     */
    @Value("${ai.tool.http-request.enabled:true}")
    private boolean enabled;

    /**
     * 发送GET请求
     * @param url 请求URL
     * @return
     */
    @AgentTool("发送HTTP GET请求。输入URL，返回响应状态码、响应头和响应体。适用于获取REST API数据。")
    public String httpGet(@AgentToolParam("请求URL") String url) {
        return execute("GET", url, null, null, null);
    }

    /**
     * 发送带请求头的GET请求
     * @param url 请求URL
     * @param headersJson 请求头JSON（如 {"Authorization":"Bearer xxx","Content-Type":"application/json"}）
     * @return
     */
    @AgentTool("发送带自定义请求头的HTTP GET请求。headersJson为JSON格式字符串，如 {\"Authorization\":\"Bearer xxx\"}。")
    public String httpGetWithHeaders(@AgentToolParam("请求URL") String url, @AgentToolParam("请求头JSON，如 {\"Authorization\":\"Bearer xxx\"}") String headersJson) {
        return execute("GET", url, null, headersJson, null);
    }

    /**
     * 发送POST请求
     * @param url 请求URL
     * @param body 请求体
     * @param contentType 内容类型（如 application/json、application/x-www-form-urlencoded）
     * @return
     */
    @AgentTool("发送HTTP POST请求。输入URL、请求体和内容类型（如application/json），返回响应结果。适用于提交数据到REST API。")
    public String httpPost(@AgentToolParam("请求URL") String url, @AgentToolParam("请求体内容") String body, @AgentToolParam("内容类型，如 application/json") String contentType) {
        return execute("POST", url, body, null, contentType);
    }

    /**
     * 发送PUT请求
     * @param url 请求URL
     * @param body 请求体
     * @param contentType 内容类型
     * @return
     */
    @AgentTool("发送HTTP PUT请求。输入URL、请求体和内容类型，返回响应结果。适用于更新REST API资源。")
    public String httpPut(@AgentToolParam("请求URL") String url, @AgentToolParam("请求体内容") String body, @AgentToolParam("内容类型，如 application/json") String contentType) {
        return execute("PUT", url, body, null, contentType);
    }

    /**
     * 发送DELETE请求
     * @param url 请求URL
     * @return
     */
    @AgentTool("发送HTTP DELETE请求。输入URL，返回响应结果。适用于删除REST API资源。")
    public String httpDelete(@AgentToolParam("请求URL") String url) {
        return execute("DELETE", url, null, null, null);
    }

    /**
     * 发送带请求头的POST请求
     * @param url 请求URL
     * @param body 请求体
     * @param headersJson 请求头JSON
     * @param contentType 内容类型
     * @return
     */
    @AgentTool("发送带自定义请求头的HTTP POST请求。headersJson为JSON格式字符串。适用于需要认证的API调用。")
    public String httpPostWithHeaders(@AgentToolParam("请求URL") String url, @AgentToolParam("请求体内容") String body, @AgentToolParam("请求头JSON，如 {\"Authorization\":\"Bearer xxx\"}") String headersJson, @AgentToolParam("内容类型，如 application/json") String contentType) {
        return execute("POST", url, body, headersJson, contentType);
    }

    /**
     * 执行HTTP请求
     * @param method HTTP方法
     * @param url URL
     * @param body 请求体
     * @param headersJson 请求头JSON
     * @param contentType 内容类型
     * @return
     */
    private String execute(String method, String url, String body, String headersJson, String contentType) {
        if (!enabled) {
            return "HTTP请求工具已被禁用（ai.tool.http-request.enabled=false）";
        }

        try {
            // SSRF防护：检查URL
            if (ssrfProtectionEnabled) {
                String ssrfCheck = checkSsrf(url);
                if (ssrfCheck != null) {
                    return ssrfCheck;
                }
            }

            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS));

            // 添加请求头
            if (headersJson != null && !headersJson.trim().isEmpty()) {
                Map<String, String> headers = parseHeaders(headersJson);
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestBuilder.header(entry.getKey(), entry.getValue());
                }
            }

            // 设置方法和请求体
            String actualContentType = contentType == null ? "application/json" : contentType;
            switch (method) {
                case "GET":
                    requestBuilder.GET();
                    break;
                case "POST":
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                    requestBuilder.header("Content-Type", actualContentType);
                    break;
                case "PUT":
                    requestBuilder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
                    requestBuilder.header("Content-Type", actualContentType);
                    break;
                case "DELETE":
                    requestBuilder.DELETE();
                    break;
                default:
                    return "不支持的HTTP方法：" + method;
            }

            HttpRequest request = requestBuilder.build();
            log.debug("执行HTTP请求: {} {}", method, url);

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // 检查响应大小
            String responseBody = response.body();
            if (responseBody != null && responseBody.length() > MAX_RESPONSE_SIZE) {
                responseBody = responseBody.substring(0, MAX_RESPONSE_SIZE)
                        + "\n...[响应体过大，已截断，原始大小：" + responseBody.length() + " 字节]";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("状态码：").append(response.statusCode()).append("\n");
            sb.append("响应头：\n");
            response.headers().map().forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            sb.append("响应体：\n").append(responseBody);
            return sb.toString();
        } catch (java.net.ConnectException e) {
            return "连接失败：" + e.getMessage();
        } catch (java.net.SocketTimeoutException e) {
            return "请求超时：" + e.getMessage();
        } catch (IllegalArgumentException e) {
            return "URL格式错误：" + e.getMessage();
        } catch (Exception e) {
            log.warn("HTTP请求异常: {} {}", method, url, e);
            return "请求异常：" + e.getClass().getSimpleName() + " - " + e.getMessage();
        }
    }

    /**
     * SSRF防护：检查URL是否安全
     * @param url 待检查的URL
     * @return 错误信息，null表示通过
     */
    private String checkSsrf(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null) {
                return "URL格式错误：缺少host";
            }
            // 禁止访问内网地址
            if (host.equals("localhost") || host.equals("127.0.0.1") || host.startsWith("10.")
                    || host.startsWith("172.16.") || host.startsWith("172.17.")
                    || host.startsWith("172.18.") || host.startsWith("172.19.")
                    || host.startsWith("172.20.") || host.startsWith("172.21.")
                    || host.startsWith("172.22.") || host.startsWith("172.23.")
                    || host.startsWith("172.24.") || host.startsWith("172.25.")
                    || host.startsWith("172.26.") || host.startsWith("172.27.")
                    || host.startsWith("172.28.") || host.startsWith("172.29.")
                    || host.startsWith("172.30.") || host.startsWith("172.31.")
                    || host.startsWith("192.168.") || host.equals("0.0.0.0")) {
                return "SSRF防护：禁止访问内网地址 " + host;
            }
            return null;
        } catch (Exception e) {
            return "URL解析失败：" + e.getMessage();
        }
    }

    /**
     * 解析请求头JSON
     * <p>
     * 简单JSON解析，避免引入JSON库依赖。
     * 支持格式：{"key1":"value1","key2":"value2"}
     * </p>
     * @param headersJson 请求头JSON字符串
     * @return
     */
    private Map<String, String> parseHeaders(String headersJson) {
        Map<String, String> headers = new java.util.LinkedHashMap<>();
        // 移除首尾大括号
        String content = headersJson.trim();
        if (content.startsWith("{")) {
            content = content.substring(1);
        }
        if (content.endsWith("}")) {
            content = content.substring(0, content.length() - 1);
        }
        // 简单分割（不支持嵌套JSON和转义字符）
        if (content.trim().isEmpty()) {
            return headers;
        }
        String[] pairs = content.split(",");
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx < 0) {
                idx = pair.indexOf('=');
            }
            if (idx > 0) {
                String key = pair.substring(0, idx).trim().replace("\"", "");
                String value = pair.substring(idx + 1).trim().replace("\"", "");
                if (!key.isEmpty()) {
                    headers.put(key, value);
                }
            }
        }
        return headers;
    }
}
