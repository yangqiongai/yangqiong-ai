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
package com.yangqiongai.ai.platform.connector.entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * 连接器凭证
 * @author yangqiong
 */
@TableName("ai_connector_credential")
public class ConnectorCredential extends ScopeEntity {

    /**
     * 主键ID
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 提供商编码(dingtalk/wecom/feishu/database/docparser)
     */
    private String providerCode;

    /**
     * 凭证名称
     */
    private String name;

    /**
     * AES加密后的凭证JSON
     */
    private String credentialJson;

    /**
     * 敏感字段尾号掩码(JSON: 字段名->掩码值)
     */
    private String maskedJson;

    /**
     * 状态(ENABLED/DISABLED)
     */
    private String status;

    public Long getDbId() {
        return id;
    }

    public void setDbId(Long id) {
        this.id = id;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public void setProviderCode(String providerCode) {
        this.providerCode = providerCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCredentialJson() {
        return credentialJson;
    }

    public void setCredentialJson(String credentialJson) {
        this.credentialJson = credentialJson;
    }

    public String getMaskedJson() {
        return maskedJson;
    }

    public void setMaskedJson(String maskedJson) {
        this.maskedJson = maskedJson;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
