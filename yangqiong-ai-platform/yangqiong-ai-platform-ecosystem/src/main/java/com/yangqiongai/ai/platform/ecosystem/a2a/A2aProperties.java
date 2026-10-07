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
package com.yangqiongai.ai.platform.ecosystem.a2a;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * A2A出口配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.ecosystem.a2a")
public class A2aProperties {

    /**
     * 是否启用A2A出口(独立于ecosystem总开关)
     */
    private boolean enabled = false;

    /**
     * 协议版本声明
     */
    private String protocolVersion = "1.0";

    /**
     * 卡片签名开关(签名卡响应头X-A2A-Card-Signature)
     */
    private boolean cardSignatureEnabled = true;

    /**
     * 平台默认对外Agent编码(well-known无agentCode参数时使用)
     */
    private String defaultAgentCode;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(String protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public boolean isCardSignatureEnabled() {
        return cardSignatureEnabled;
    }

    public void setCardSignatureEnabled(boolean cardSignatureEnabled) {
        this.cardSignatureEnabled = cardSignatureEnabled;
    }

    public String getDefaultAgentCode() {
        return defaultAgentCode;
    }

    public void setDefaultAgentCode(String defaultAgentCode) {
        this.defaultAgentCode = defaultAgentCode;
    }
}
