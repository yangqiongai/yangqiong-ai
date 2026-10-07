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
package com.yangqiongai.ai.platform.connector.spi;

import java.util.Map;

/**
 * 连接器入站回调请求
 * <p>
 * 渠道回调的原始HTTP信息（方法/头/查询参数/原始报文），由网关入口构造。
 * </p>
 * @author yangqiong
 */
public class ConnectorCallbackRequest {

    /**
     * HTTP方法(GET/POST)
     */
    private final String method;

    /**
     * 请求头（小写键）
     */
    private final Map<String, String> headers;

    /**
     * 查询参数
     */
    private final Map<String, String> query;

    /**
     * 原始报文
     */
    private final String body;

    public ConnectorCallbackRequest(String method, Map<String, String> headers,
                                    Map<String, String> query, String body) {
        this.method = method;
        this.headers = headers == null ? Map.of() : headers;
        this.query = query == null ? Map.of() : query;
        this.body = body;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQuery() {
        return query;
    }

    public String getBody() {
        return body;
    }

    /**
     * 读取请求头（大小写不敏感）
     * @param name 头名
     * @return 值，不存在返回null
     */
    public String getHeader(String name) {
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
