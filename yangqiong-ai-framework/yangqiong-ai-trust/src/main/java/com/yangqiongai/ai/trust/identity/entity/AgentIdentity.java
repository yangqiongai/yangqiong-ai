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
package com.yangqiongai.ai.trust.identity.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * Agent身份档案
 * @author yangqiong
 */
@TableName("ai_agent_identity")
public class AgentIdentity extends ScopeEntity {

    /**
     * 有效状态
     */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 已吊销状态
     */
    public static final String STATUS_REVOKED = "REVOKED";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 身份唯一标识(aid-前缀)
     */
    private String identityUid;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 身份显示名称
     */
    private String displayName;

    /**
     * 属主用户
     */
    private String ownerUser;

    /**
     * 凭证指纹(SHA-256前32位,不含明文)
     */
    private String credentialFingerprint;

    /**
     * DID标识(企业版导出用,国标后补映射)
     */
    private String did;

    /**
     * 状态(ACTIVE/REVOKED)
     */
    private String status;

    /**
     * 轮换周期(天,空为不轮换)
     */
    private Integer rotateDays;

    /**
     * 最近轮换时间
     */
    private LocalDateTime lastRotatedTime;

    /**
     * 轮换到期时间(不落库,列表查询时计算填充)
     */
    @TableField(exist = false)
    private LocalDateTime rotateDueTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIdentityUid() {
        return identityUid;
    }

    public void setIdentityUid(String identityUid) {
        this.identityUid = identityUid;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getOwnerUser() {
        return ownerUser;
    }

    public void setOwnerUser(String ownerUser) {
        this.ownerUser = ownerUser;
    }

    public String getCredentialFingerprint() {
        return credentialFingerprint;
    }

    public void setCredentialFingerprint(String credentialFingerprint) {
        this.credentialFingerprint = credentialFingerprint;
    }

    public String getDid() {
        return did;
    }

    public void setDid(String did) {
        this.did = did;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRotateDays() {
        return rotateDays;
    }

    public void setRotateDays(Integer rotateDays) {
        this.rotateDays = rotateDays;
    }

    public LocalDateTime getLastRotatedTime() {
        return lastRotatedTime;
    }

    public void setLastRotatedTime(LocalDateTime lastRotatedTime) {
        this.lastRotatedTime = lastRotatedTime;
    }

    public LocalDateTime getRotateDueTime() {
        return rotateDueTime;
    }

    public void setRotateDueTime(LocalDateTime rotateDueTime) {
        this.rotateDueTime = rotateDueTime;
    }
}
