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
package com.yangqiongai.ai.trust.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * Agent动作审计日志
 * @author yangqiong
 */
@TableName("ai_agent_action_log")
public class AgentActionLog extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 身份唯一标识(未绑定身份时为空)
     */
    private String identityUid;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 动作类型(TOOL_CALL工具调用/GOVERNANCE治理动作等)
     */
    private String actionType;

    /**
     * 动作对象(工具名/资源标识)
     */
    private String resource;

    /**
     * 判定结果(ALLOW放行/DENY拒绝)
     */
    private String decision;

    /**
     * 动作摘要(已脱敏截断)
     */
    private String summary;

    /**
     * 证据哈希(SHA-256,企业版哈希链)
     */
    private String evidenceHash;

    /**
     * 前链哈希(企业版哈希链)
     */
    private String prevHash;

    /**
     * 链内序号(企业版哈希链)
     */
    private Long seqNo;

    /**
     * 执行耗时毫秒(工具调用)
     */
    private Long durationMillis;

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

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getResource() {
        return resource;
    }

    public void setResource(String resource) {
        this.resource = resource;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public void setEvidenceHash(String evidenceHash) {
        this.evidenceHash = evidenceHash;
    }

    public String getPrevHash() {
        return prevHash;
    }

    public void setPrevHash(String prevHash) {
        this.prevHash = prevHash;
    }

    public Long getSeqNo() {
        return seqNo;
    }

    public void setSeqNo(Long seqNo) {
        this.seqNo = seqNo;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }
}
