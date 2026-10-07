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
package com.yangqiongai.ai.agent.data.skill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * 技能版本
 * @author yangqiong
 */
@TableName("ai_agent_skill_version")
public class SkillVersionEntity extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 技能ID
     */
    private String skillId;

    /**
     * 版本号
     */
    private Integer version;

    /**
     * Markdown内容快照
     */
    private String skillContent;

    /**
     * 变更说明
     */
    private String changeLog;

    /**
     * 技能内容SHA-256指纹
     */
    private String fingerprint;

    /**
     * 内容质量总分(0-100)
     */
    private java.math.BigDecimal qualityScore;

    /**
     * 分维度得分与改进建议(JSON原文)
     */
    private String evalDimensions;

    /**
     * 评测模型
     */
    private String evalModel;

    /**
     * 评测时间
     */
    private java.time.LocalDateTime evaluatedTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getSkillContent() {
        return skillContent;
    }

    public void setSkillContent(String skillContent) {
        this.skillContent = skillContent;
    }

    public String getChangeLog() {
        return changeLog;
    }

    public void setChangeLog(String changeLog) {
        this.changeLog = changeLog;
    }

    public String getFingerprint() {
        return fingerprint;
    }

    public void setFingerprint(String fingerprint) {
        this.fingerprint = fingerprint;
    }

    public java.math.BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(java.math.BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public String getEvalDimensions() {
        return evalDimensions;
    }

    public void setEvalDimensions(String evalDimensions) {
        this.evalDimensions = evalDimensions;
    }

    public String getEvalModel() {
        return evalModel;
    }

    public void setEvalModel(String evalModel) {
        this.evalModel = evalModel;
    }

    public java.time.LocalDateTime getEvaluatedTime() {
        return evaluatedTime;
    }

    public void setEvaluatedTime(java.time.LocalDateTime evaluatedTime) {
        this.evaluatedTime = evaluatedTime;
    }
}
