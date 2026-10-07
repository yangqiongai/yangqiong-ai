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
import com.baomidou.mybatisplus.annotation.Version;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * 连接器实例
 * @author yangqiong
 */
@TableName("ai_connector_instance")
public class ConnectorInstance extends ScopeEntity {

    /**
     * 主键ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 实例编码（入站回调路由键）
     */
    private String instanceCode;

    /**
     * 提供商编码
     */
    private String providerCode;

    /**
     * 实例名称
     */
    private String name;

    /**
     * 非敏感配置JSON(robotCode/agentCode绑定/数据库参数/解析参数等)
     */
    private String configJson;

    /**
     * 关联凭证ID(可空=免凭证提供商)
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long credentialId;

    /**
     * 入站消息目标Agent编码(可空=仅出站工具)
     */
    private String agentCode;

    /**
     * 状态(ENABLED/DISABLED)
     */
    private String status;

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;

    public Long getDbId() {
        return id;
    }

    public void setDbId(Long id) {
        this.id = id;
    }

    public String getInstanceCode() {
        return instanceCode;
    }

    public void setInstanceCode(String instanceCode) {
        this.instanceCode = instanceCode;
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

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public Long getCredentialId() {
        return credentialId;
    }

    public void setCredentialId(Long credentialId) {
        this.credentialId = credentialId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }
}
