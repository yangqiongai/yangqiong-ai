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
package com.yangqiongai.ai.storage.qdrant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Qdrant配置属性
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.storage.qdrant")
public class QdrantProperties {

    /**
     * 主机地址
     */
    private String host = "localhost";

    /**
     * 端口
     */
    private int port = 6334;

    /**
     * 是否使用TLS
     */
    private boolean useTls = false;

    /**
     * API密钥
     */
    private String apiKey;

    /**
     * 同步调用超时秒数（超时快速失败并降级，避免Qdrant不可达时对话链路长时间挂起）
     */
    private int timeoutSeconds = 5;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isUseTls() {
        return useTls;
    }

    public void setUseTls(boolean useTls) {
        this.useTls = useTls;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }
}
