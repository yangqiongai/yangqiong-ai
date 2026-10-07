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

import java.time.LocalDateTime;

/**
 * Agent版本
 * @author yangqiong
 */
@TableName("ai_agent_version")
public class AgentVersion extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 版本号
     */
    private String versionNo;

    /**
     * 装配清单JSON(model/systemPrompt/maxIterations/temperature/tools/skills/knowledgeBase/bindingMode)
     */
    private String configJson;

    /**
     * 配置哈希(SHA-256，防重复发布)
     */
    private String configHash;

    /**
     * 变更说明
     */
    private String changelog;

    /**
     * 状态(DRAFT/PENDING_REVIEW/PUBLISHED/REJECTED/DEPRECATED)
     */
    private String status;

    /**
     * 评测数据集位置(JSON数组)
     */
    private String evalDatasetLocations;

    /**
     * 评测报告ID
     */
    private String evalReportId;

    /**
     * 评测是否通过(0-否 1-是)
     */
    private Integer evalPassed;

    /**
     * 引用的评测运行ID(发布门禁离线报告)
     */
    private Long evalRunId;

    /**
     * 提交人
     */
    private String submitUser;

    /**
     * 提交时间
     */
    private LocalDateTime submitTime;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

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

    public String getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(String versionNo) {
        this.versionNo = versionNo;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public String getConfigHash() {
        return configHash;
    }

    public void setConfigHash(String configHash) {
        this.configHash = configHash;
    }

    public String getChangelog() {
        return changelog;
    }

    public void setChangelog(String changelog) {
        this.changelog = changelog;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getEvalDatasetLocations() {
        return evalDatasetLocations;
    }

    public void setEvalDatasetLocations(String evalDatasetLocations) {
        this.evalDatasetLocations = evalDatasetLocations;
    }

    public String getEvalReportId() {
        return evalReportId;
    }

    public void setEvalReportId(String evalReportId) {
        this.evalReportId = evalReportId;
    }

    public Integer getEvalPassed() {
        return evalPassed;
    }

    public void setEvalPassed(Integer evalPassed) {
        this.evalPassed = evalPassed;
    }

    public String getSubmitUser() {
        return submitUser;
    }

    public void setSubmitUser(String submitUser) {
        this.submitUser = submitUser;
    }

    public LocalDateTime getSubmitTime() {
        return submitTime;
    }

    public void setSubmitTime(LocalDateTime submitTime) {
        this.submitTime = submitTime;
    }

    public LocalDateTime getPublishTime() {
        return publishTime;
    }

    public void setPublishTime(LocalDateTime publishTime) {
        this.publishTime = publishTime;
    }

    public Long getEvalRunId() {
        return evalRunId;
    }

    public void setEvalRunId(Long evalRunId) {
        this.evalRunId = evalRunId;
    }
}
