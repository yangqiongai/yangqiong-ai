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
package com.yangqiongai.ai.trust.identity;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Agent身份配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.trust.identity")
public class AgentIdentityProperties {

    /**
     * 凭证签名主密钥(至少32字节,生产环境必须外部化)
     */
    private String masterKey;

    /**
     * 签发凭证有效期(秒,轮换后旧凭证在此窗口内自然过期)
     */
    private long credentialTtlSeconds = 300L;

    /**
     * 默认轮换周期(天)
     */
    private int defaultRotateDays = 90;

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public long getCredentialTtlSeconds() {
        return credentialTtlSeconds;
    }

    public void setCredentialTtlSeconds(long credentialTtlSeconds) {
        this.credentialTtlSeconds = credentialTtlSeconds;
    }

    public int getDefaultRotateDays() {
        return defaultRotateDays;
    }

    public void setDefaultRotateDays(int defaultRotateDays) {
        this.defaultRotateDays = defaultRotateDays;
    }
}
