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
package com.yangqiongai.ai.agent.data.registry.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.math.BigDecimal;

/**
 * Agent定义
 * @author yangqiong
 */
@TableName("ai_agent_definition")
public class AgentDefinition extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Agent编码(唯一标识，≡ai_agent.type_code)
     */
    private String agentCode;

    /**
     * Agent名称(中文显示名)
     */
    private String agentName;

    /**
     * Agent描述
     */
    private String description;

    /**
     * Agent分类
     */
    private String category;

    /**
     * 当前生效版本ID
     */
    private Long currentVersionId;

    /**
     * 状态(DRAFT/ENABLED/DISABLED)
     */
    private String status;

    /**
     * 发布是否需要审批(0-否 1-是)
     */
    private Integer requireApproval;

    /**
     * 是否启用评测门禁(0-否 1-是)
     */
    private Integer evalEnabled;

    /**
     * 评测通过阈值(均分)
     */
    private BigDecimal evalPassThreshold;

    /**
     * A2A卡片对外启用(0-禁用 1-启用)
     */
    private Integer cardEnabled;

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

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getCurrentVersionId() {
        return currentVersionId;
    }

    public void setCurrentVersionId(Long currentVersionId) {
        this.currentVersionId = currentVersionId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getRequireApproval() {
        return requireApproval;
    }

    public void setRequireApproval(Integer requireApproval) {
        this.requireApproval = requireApproval;
    }

    public Integer getEvalEnabled() {
        return evalEnabled;
    }

    public void setEvalEnabled(Integer evalEnabled) {
        this.evalEnabled = evalEnabled;
    }

    public BigDecimal getEvalPassThreshold() {
        return evalPassThreshold;
    }

    public void setEvalPassThreshold(BigDecimal evalPassThreshold) {
        this.evalPassThreshold = evalPassThreshold;
    }

    public Integer getCardEnabled() {
        return cardEnabled;
    }

    public void setCardEnabled(Integer cardEnabled) {
        this.cardEnabled = cardEnabled;
    }
}
