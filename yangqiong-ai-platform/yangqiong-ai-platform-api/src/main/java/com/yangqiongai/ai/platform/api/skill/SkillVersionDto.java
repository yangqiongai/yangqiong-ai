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
package com.yangqiongai.ai.platform.api.skill;

import java.time.LocalDateTime;

/**
 * 技能版本信息
 * @author yangqiong
 */
public class SkillVersionDto {

    /**
     * 版本号
     */
    private Integer version;

    /**
     * 变更说明
     */
    private String changeLog;

    /**
     * 技能内容SHA-256指纹（前8位）
     */
    private String fingerprint;

    /**
     * 创建人
     */
    private String createUser;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 内容质量总分(0-100，未评测时为null)
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
    private LocalDateTime evaluatedTime;

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
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

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
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

    public LocalDateTime getEvaluatedTime() {
        return evaluatedTime;
    }

    public void setEvaluatedTime(LocalDateTime evaluatedTime) {
        this.evaluatedTime = evaluatedTime;
    }
}
