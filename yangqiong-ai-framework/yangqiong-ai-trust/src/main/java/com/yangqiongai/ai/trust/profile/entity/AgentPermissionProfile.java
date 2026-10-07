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
package com.yangqiongai.ai.trust.profile.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * Agent权限画像
 * @author yangqiong
 */
@TableName("ai_agent_permission_profile")
public class AgentPermissionProfile extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 工具白名单(JSON数组,空/缺失=不限制)
     */
    private String toolWhitelist;

    /**
     * 知识库范围(JSON数组,空/缺失=不限制)
     */
    private String knowledgeScope;

    /**
     * 数据脱敏级别(NONE不脱敏/BASIC基础/STRICT严格)
     */
    private String dataMaskLevel;

    /**
     * 网络出口白名单(JSON数组,支持*.example.com通配,空/缺失=不限制)
     */
    private String egressWhitelist;

    /**
     * 状态(ENABLED启用/DISABLED禁用,禁用=全放行)
     */
    private String status;

    /**
     * 备注
     */
    private String remark;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getToolWhitelist() {
        return toolWhitelist;
    }

    public void setToolWhitelist(String toolWhitelist) {
        this.toolWhitelist = toolWhitelist;
    }

    public String getKnowledgeScope() {
        return knowledgeScope;
    }

    public void setKnowledgeScope(String knowledgeScope) {
        this.knowledgeScope = knowledgeScope;
    }

    public String getDataMaskLevel() {
        return dataMaskLevel;
    }

    public void setDataMaskLevel(String dataMaskLevel) {
        this.dataMaskLevel = dataMaskLevel;
    }

    public String getEgressWhitelist() {
        return egressWhitelist;
    }

    public void setEgressWhitelist(String egressWhitelist) {
        this.egressWhitelist = egressWhitelist;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}
