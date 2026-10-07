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
}