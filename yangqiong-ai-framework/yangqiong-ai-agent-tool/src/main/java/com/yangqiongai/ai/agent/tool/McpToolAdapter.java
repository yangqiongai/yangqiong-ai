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
import com.yangqiongai.ai.agent.mcp.client.McpClientWrapper;
import com.yangqiongai.ai.agent.mcp.client.McpHealthChecker;
import com.yangqiongai.ai.agent.mcp.model.McpToolInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * MCP工具适配器
 * <p>
 * 工具名策略：对外暴露带服务前缀的唯一名（如 baidu_search），
 * 避免不同MCP服务暴露同名工具时LLM混淆；内部调用时仍使用原始工具名。
 * 调用超时保护：支持可配置的工具调用超时，超时后自动中断并记录故障。
 * 故障自动下线：调用失败时记录到健康检查器，连续失败超过阈值后自动下线。
 * </p>
 * @author yangqiong
 */
public class McpToolAdapter implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(McpToolAdapter.class);

    /**
     * 服务前缀与原始工具名之间的分隔符
     */
    private static final String PREFIX_SEPARATOR = "_";

    /**
     * 默认工具调用超时时间（秒）
     */
    private static final int DEFAULT_TOOL_CALL_TIMEOUT_SECONDS = 30;

    private final McpToolInfo toolInfo;

    private final McpClientWrapper clientWrapper;

    /**
     * 带服务前缀的唯一工具名（供LLM识别）
     */
    private final String prefixedName;

    /**
     * 工具调用超时时间
     */
    private final Duration toolCallTimeout;

    /**
     * 健康检查器（可选，用于故障记录和自动下线）
     */
    private McpHealthChecker healthChecker;

    public McpToolAdapter(McpToolInfo toolInfo, McpClientWrapper clientWrapper) {
        this(toolInfo, clientWrapper, DEFAULT_TOOL_CALL_TIMEOUT_SECONDS);
    }

    public McpToolAdapter(McpToolInfo toolInfo, McpClientWrapper clientWrapper, int toolCallTimeoutSeconds) {
        if (toolInfo == null) {
            throw new IllegalArgumentException("toolInfo不允许为空");
        }
        if (clientWrapper == null) {
            throw new IllegalArgumentException("clientWrapper不允许为空");
        }
        this.toolInfo = toolInfo;
        this.clientWrapper = clientWrapper;
        this.prefixedName = buildPrefixedName(toolInfo.getServerCode(), toolInfo.getName());
        this.toolCallTimeout = Duration.ofSeconds(
                toolCallTimeoutSeconds > 0 ? toolCallTimeoutSeconds : DEFAULT_TOOL_CALL_TIMEOUT_SECONDS);
    }

    /**
     * 设置健康检查器
     * @param healthChecker
     */
    public void setHealthChecker(McpHealthChecker healthChecker) {
        this.healthChecker = healthChecker;
    }

    /**
     * 构建带服务前缀的工具名
     * <p>
     * 将serverCode中的横线去除，与原始工具名拼接为唯一名。
     * 例如：serverCode=baidu-search, name=search → baidu_search
     * </p>
     * @param serverCode
     * @param originalName
     * @return
     */
    private String buildPrefixedName(String serverCode, String originalName) {
        if (serverCode == null || serverCode.isBlank()) {
            return originalName;
        }
        String prefix = serverCode.replace("-", "_");
        return prefix + PREFIX_SEPARATOR + originalName;
    }

    /**
     * 返回带服务前缀的唯一工具名（供LLM识别和选择）
     * @return
     */
    @Override
    public String getName() {
        return prefixedName;
    }

    /**
     * 返回增强描述，在原始描述前附加服务来源信息
     * @return
     */
    @Override
    public String getDescription() {
        String original = toolInfo.getDescription();
        if (original == null || original.isBlank()) {
            return "[" + toolInfo.getServerCode() + "]";
        }
        return "[" + toolInfo.getServerCode() + "] " + original;
    }

    /**
     * 解析工具参数schema为Map
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        String schema = toolInfo.getInputSchema();
        if (schema == null || schema.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return JSON.parseObject(schema, Map.class);
        } catch (Exception e) {
            log.warn("解析MCP工具参数schema失败: {}", prefixedName, e);
            return Collections.emptyMap();
        }
    }

    /**
     * 常见MCP工具错误返回模式（不区分大小写匹配）
     */
    private static final String[] ERROR_PATTERNS = {
            "unexpected error",
            "error:",
            "exception:",
            "traceback",
            "failed to",
            "not installed",
            "does not exist",
            "executable doesn't exist",
            "connection refused",
            "timed out"
    };

    /**
     * 异步调用MCP工具（带超时保护和故障记录）
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        String args = toArgumentsJson(param.getInput());
        // 使用原始工具名调用MCP Server（非带前缀名）
        String originalName = toolInfo.getName();
        String serverCode = toolInfo.getServerCode();
        return Mono.defer(() -> Mono.fromFuture(
                        clientWrapper.callToolAsync(originalName, args)
                                .orTimeout(toolCallTimeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
                ))
                .flatMap(result -> {
                    // 内容级别错误检测：部分MCP Server不设置isError标记，
                    // 但返回的文本中包含错误信息（如Playwright未安装、连接失败等）
                    if (isErrorLikeContent(result)) {
                        recordToolCallResult(serverCode, false);
                        log.warn("MCP工具返回错误内容: prefixedName={}, result={}", prefixedName,
                                result.length() > 200 ? result.substring(0, 200) + "..." : result);
                        return Mono.just(AgentToolResultBlock.error("MCP工具执行错误: " + result));
                    }
                    // 空结果检测：部分MCP工具因环境问题返回空结果而非错误
                    // （如baidu_search因Playwright未安装返回[]），
                    // 需要单独追踪连续空结果次数，达到阈值后触发下线
                    if (isEmptyResult(result)) {
                        recordEmptyResult(serverCode);
                        log.warn("MCP工具返回空结果: prefixedName={}, result={}", prefixedName,
                                result.length() > 100 ? result.substring(0, 100) + "..." : result);
                        return Mono.just(toTextResult(result));
                    }
                    recordToolCallResult(serverCode, true);
                    log.debug("MCP工具调用完成: {}, 耗时限制={}s", prefixedName, toolCallTimeout.getSeconds());
                    return Mono.just(toTextResult(result));
                })
                .onErrorResume(e -> {
                    boolean isTimeout = e instanceof TimeoutException
                            || (e.getCause() instanceof TimeoutException);
                    recordToolCallResult(serverCode, false);
                    if (isTimeout) {
                        log.error("MCP工具调用超时: prefixedName={}, timeout={}s", prefixedName, toolCallTimeout.getSeconds());
                        return Mono.just(AgentToolResultBlock.error(
                                "MCP工具调用超时(" + toolCallTimeout.getSeconds() + "s): " + prefixedName));
                    }
                    log.error("MCP工具调用失败: prefixedName={}, originalName={}", prefixedName, originalName, e);
                    return Mono.just(AgentToolResultBlock.error("MCP工具调用失败: " + e.getMessage()));
                });
    }

    /**
     * 将文本内容包装为工具结果
     * @param text
     * @return
     */
    private AgentToolResultBlock toTextResult(String text) {
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(text).build()));
    }

    /**
     * 检测返回内容是否像错误信息
     * <p>
     * 部分MCP Server实现不规范，未设置isError=true标记，
     * 但在content中返回了明确的错误文本（如"Unexpected error: ..."）。
     * 此方法通过模式匹配检测这类内容。
     * </p>
     * @param content
     * @return
     */
    private boolean isErrorLikeContent(String content) {
        if (content == null || content.length() > 5000) {
            // 正常结果通常较长，错误信息较短（一般<500字符）
            return false;
        }
        String lowerContent = content.toLowerCase();
        for (String pattern : ERROR_PATTERNS) {
            if (lowerContent.contains(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 空结果判定模式（匹配常见空结果格式）
     */
    private static final String[] EMPTY_RESULT_PATTERNS = {
            "[]", "{}", "null", "", "no results", "no results found",
            "没有找到", "未找到", "无结果"
    };

    /**
     * 检测返回内容是否为空结果
     * <p>
     * 部分MCP工具因环境问题（如Playwright未安装、浏览器启动失败等）
     * 返回空结果而非错误信息，如返回[]、空字符串、no results等。
     * 此方法检测这类空结果内容，用于累计无效调用次数。
     * </p>
     * @param content
     * @return
     */
    private boolean isEmptyResult(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String trimmed = content.trim();
        if (trimmed.length() > 50) {
            // 超过50字符的内容不可能是空结果
            return false;
        }
        String lowerTrimmed = trimmed.toLowerCase();
        for (String pattern : EMPTY_RESULT_PATTERNS) {
            if (lowerTrimmed.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 记录工具调用结果到健康检查器
     * @param serverCode
     * @param success
     */
    private void recordToolCallResult(String serverCode, boolean success) {
        if (healthChecker != null && serverCode != null && !serverCode.isBlank()) {
            try {
                healthChecker.recordToolCallResult(serverCode, success);
            } catch (Exception e) {
                log.debug("记录MCP工具调用结果失败: serverCode={}", serverCode, e);
            }
        }
    }

    /**
     * 记录空结果到健康检查器
     * @param serverCode
     */
    private void recordEmptyResult(String serverCode) {
        if (healthChecker != null && serverCode != null && !serverCode.isBlank()) {
            try {
                healthChecker.recordEmptyResult(serverCode);
            } catch (Exception e) {
                log.debug("记录MCP工具空结果失败: serverCode={}", serverCode, e);
            }
        }
    }

    /**
     * 将参数Map转换为JSON字符串
     * @param input
     * @return
     */
    private String toArgumentsJson(Map<String, Object> input) {
        if (input == null || input.isEmpty()) {
            return "{}";
        }
        try {
            return JSON.toJSONString(input);
        } catch (Exception e) {
            log.warn("序列化MCP工具参数失败: {}", prefixedName, e);
            return "{}";
        }
    }
}
