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
package com.yangqiongai.ai.open.capability.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 开放能力层配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.agent.open.capability")
public class OpenCapabilityProperties {

    /**
     * 是否启用开放能力层
     */
    private boolean enabled = false;

    /**
     * 去重缓存有效期（秒）
     */
    private long dedupTtlSeconds = 86400;

    /**
     * 数据上下文默认过期时间（秒）
     */
    private long dataContextDefaultTtlSeconds = 3600;

    /**
     * URL附件拉取配置
     */
    private FileUrlFetch fileUrlFetch = new FileUrlFetch();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getDedupTtlSeconds() {
        return dedupTtlSeconds;
    }

    public void setDedupTtlSeconds(long dedupTtlSeconds) {
        this.dedupTtlSeconds = dedupTtlSeconds;
    }

    public long getDataContextDefaultTtlSeconds() {
        return dataContextDefaultTtlSeconds;
    }

    public void setDataContextDefaultTtlSeconds(long dataContextDefaultTtlSeconds) {
        this.dataContextDefaultTtlSeconds = dataContextDefaultTtlSeconds;
    }

    public FileUrlFetch getFileUrlFetch() {
        return fileUrlFetch;
    }

    public void setFileUrlFetch(FileUrlFetch fileUrlFetch) {
        this.fileUrlFetch = fileUrlFetch;
    }

    /**
     * URL附件拉取配置
     */
    public static class FileUrlFetch {

        /**
         * 单文件大小上限（MB）
         */
        private long maxSizeMb = 50;

        /**
         * 连接超时（毫秒）
         */
        private int connectTimeoutMs = 5000;

        /**
         * 读取超时（毫秒）
         */
        private int readTimeoutMs = 30000;

        /**
         * 最大重定向跳数
         */
        private int maxRedirects = 3;

        /**
         * 允许拉取的内网地址白名单（精确IP/主机名或CIDR网段，用于放行内网MinIO等受信地址）
         */
        private java.util.List<String> allowedInternalHosts = new java.util.ArrayList<>();

        public long getMaxSizeMb() {
            return maxSizeMb;
        }

        public void setMaxSizeMb(long maxSizeMb) {
            this.maxSizeMb = maxSizeMb;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }

        public int getMaxRedirects() {
            return maxRedirects;
        }

        public void setMaxRedirects(int maxRedirects) {
            this.maxRedirects = maxRedirects;
        }

        public java.util.List<String> getAllowedInternalHosts() {
            return allowedInternalHosts;
        }

        public void setAllowedInternalHosts(java.util.List<String> allowedInternalHosts) {
            this.allowedInternalHosts = allowedInternalHosts;
        }
    }
}