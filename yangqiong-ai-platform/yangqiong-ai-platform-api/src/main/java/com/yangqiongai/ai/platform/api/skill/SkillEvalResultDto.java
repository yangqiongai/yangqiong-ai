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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 技能内容质量评测结果
 * @author yangqiong
 */
public class SkillEvalResultDto {

    /**
     * 技能ID
     */
    private String skillId;

    /**
     * 评测版本号
     */
    private Integer version;

    /**
     * 质量总分(0-100)
     */
    private BigDecimal qualityScore;

    /**
     * 分维度得分(structure结构完整性/clarity指令清晰度/security安全合规/toolMatch工具匹配度)
     */
    private Map<String, BigDecimal> dimensions;

    /**
     * 改进建议
     */
    private String suggestions;

    /**
     * 评测模型
     */
    private String evalModel;

    /**
     * 评测时间
     */
    private LocalDateTime evaluatedTime;

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

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Map<String, BigDecimal> getDimensions() {
        return dimensions;
    }

    public void setDimensions(Map<String, BigDecimal> dimensions) {
        this.dimensions = dimensions;
    }

    public String getSuggestions() {
        return suggestions;
    }

    public void setSuggestions(String suggestions) {
        this.suggestions = suggestions;
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
