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
package com.yangqiongai.ai.platform.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 认证配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.auth")
public class AuthProperties {

    /**
     * 是否启用认证模块
     */
    private boolean enabled = false;

    /**
     * JWT 配置
     */
    private JwtConfig jwt = new JwtConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public JwtConfig getJwt() {
        return jwt;
    }

    public void setJwt(JwtConfig jwt) {
        this.jwt = jwt;
    }

    /**
     * JWT 配置
     */
    public static class JwtConfig {

        /**
         * 签名密钥，生产环境必须外部化配置
         */
        private String secret;

        /**
         * 有效期（毫秒），默认24小时
         */
        private long expirationMs = 24L * 60 * 60 * 1000;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public long getExpirationMs() {
            return expirationMs;
        }

        public void setExpirationMs(long expirationMs) {
            this.expirationMs = expirationMs;
        }
    }
}
