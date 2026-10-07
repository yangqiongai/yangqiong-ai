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
package com.yangqiongai.ai.agent.mcp.model;

/**
 * MCP连接测试结果
 * @author yangqiong
 */
public class McpConnectionTestResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 结果消息
     */
    private String message;

    /**
     * 可用工具数量
     */
    private int toolCount;

    /**
     * 响应延迟(毫秒)
     */
    private long latencyMs;

    /**
     * 错误详情
     */
    private String errorDetail;

    /**
     * 原始工具数量（策略过滤前）
     */
    private int rawToolCount;

    /**
     * 过滤后工具数量（策略过滤后）
     */
    private int filteredToolCount;

    public McpConnectionTestResult() {
    }

    public McpConnectionTestResult(boolean success, String message, int toolCount, long latencyMs, String errorDetail) {
        this.success = success;
        this.message = message;
        this.toolCount = toolCount;
        this.latencyMs = latencyMs;
        this.errorDetail = errorDetail;
    }

    /**
     * 构建成功结果
     * @param toolCount
     * @param latencyMs
     * @return
     */
    public static McpConnectionTestResult ok(int toolCount, long latencyMs) {
        McpConnectionTestResult result = new McpConnectionTestResult(true, "MCP连接测试成功", toolCount, latencyMs, null);
        result.rawToolCount = toolCount;
        result.filteredToolCount = toolCount;
        return result;
    }

    /**
     * 构建成功结果（含原始和过滤后工具数量）
     * @param rawToolCount
     * @param filteredToolCount
     * @param latencyMs
     * @return
     */
    public static McpConnectionTestResult ok(int rawToolCount, int filteredToolCount, long latencyMs) {
        McpConnectionTestResult result = new McpConnectionTestResult(true, "MCP连接测试成功", filteredToolCount, latencyMs, null);
        result.rawToolCount = rawToolCount;
        result.filteredToolCount = filteredToolCount;
        return result;
    }

    /**
     * 构建失败结果
     * @param errorDetail
     * @return
     */
    public static McpConnectionTestResult fail(String errorDetail) {
        return new McpConnectionTestResult(false, "MCP连接测试失败", 0, 0, errorDetail);
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public int getToolCount() {
        return toolCount;
    }

    public void setToolCount(int toolCount) {
        this.toolCount = toolCount;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public void setLatencyMs(long latencyMs) {
        this.latencyMs = latencyMs;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }

    public int getRawToolCount() {
        return rawToolCount;
    }

    public void setRawToolCount(int rawToolCount) {
        this.rawToolCount = rawToolCount;
    }

    public int getFilteredToolCount() {
        return filteredToolCount;
    }

    public void setFilteredToolCount(int filteredToolCount) {
        this.filteredToolCount = filteredToolCount;
    }
}
