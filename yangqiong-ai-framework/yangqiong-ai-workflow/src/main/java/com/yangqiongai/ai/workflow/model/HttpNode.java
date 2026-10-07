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

import java.util.Map;

/**
 * HTTP请求节点（调用外部API）
 * <p>
 * config:
 * - url: 请求URL，支持 ${var} 变量替换
 * - method: GET/POST/PUT/DELETE，默认GET
 * - headers: 请求头Map，值支持 ${var} 变量替换
 * - body: 请求体（JSON字符串），支持 ${var} 变量替换
 * - timeout: 超时时间（毫秒），默认10000
 * - responseVar: 响应体写入的变量名（默认写入 {nodeId}.response）
 * <p>
 * 输出：
 * - {nodeId}.response: 响应体文本
 * - {nodeId}.statusCode: HTTP状态码
 * - {nodeId}.responseHeaders: 响应头
 *
 * @author yangqiong
 */
public class HttpNode extends WorkflowNode {

    public String getUrl() {
        return getConfigString("url");
    }

    public void setUrl(String url) {
        setConfigValue("url", url);
    }

    public String getMethod() {
        String method = getConfigString("method");
        return method != null ? method.toUpperCase() : "GET";
    }

    public void setMethod(String method) {
        setConfigValue("method", method);
    }

    public Map<String, String> getHeaders() {
        return getConfigStringMap("headers");
    }

    public void setHeaders(Map<String, String> headers) {
        setConfigValue("headers", headers);
    }

    public String getBody() {
        return getConfigString("body");
    }

    public void setBody(String body) {
        setConfigValue("body", body);
    }

    public int getTimeout() {
        Integer timeout = getConfigInt("timeout");
        return timeout != null ? timeout : 10000;
    }

    public void setTimeout(int timeout) {
        setConfigValue("timeout", timeout);
    }

    public String getResponseVar() {
        return getConfigString("responseVar");
    }

    public void setResponseVar(String responseVar) {
        setConfigValue("responseVar", responseVar);
    }
}
