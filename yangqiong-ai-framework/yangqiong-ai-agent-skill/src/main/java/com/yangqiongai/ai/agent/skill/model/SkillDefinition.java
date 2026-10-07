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
package com.yangqiongai.ai.agent.skill.model;

import java.util.List;
import java.util.Map;

/**
 * 技能定义
 * @author yangqiong
 */
public class SkillDefinition {

    /**
     * 技能ID
     */
    private String skillId;

    /**
     * 技能名称
     */
    private String skillName;

    /**
     * 技能描述
     */
    private String skillDescription;

    /**
     * 技能类型
     */
    private String skillType;

    /**
     * 技能内容(Markdown)
     */
    private String skillContent;

    /**
     * 绑定工具列表
     */
    private List<String> boundTools;

    /**
     * 资源文件（路径→内容）
     */
    private Map<String, String> resources;

    /**
     * 执行配置
     */
    private Map<String, Object> execution;

    /**
     * 依赖配置
     */
    private Map<String, Object> dependencies;

    /**
     * 预设参数
     */
    private Map<String, Object> presetParameters;

    /**
     * 技能版本
     */
    private int skillVersion;

    /**
     * 条件配置（包含任务白名单和请求数据体匹配规则）
     */
    private Map<String, Object> conditions;

    /**
     * 信任等级（BUILTIN/TRUSTED/COMMUNITY/AGENT_CREATED）
     */
    private TrustLevel trustLevel;

    /**
     * 所属分类编码（空为未分类）
     */
    private String category;

    /**
     * 变更说明（仅随保存请求透传至版本快照changeLog，不落技能配置表）
     */
    private String remark;

    /**
     * 当前版本内容质量总分(0-100，来自版本快照最新行，透传展示用)
     */
    private java.math.BigDecimal qualityScore;

    /**
     * 当前版本评测时间（透传展示用）
     */
    private java.time.LocalDateTime evaluatedTime;

    public String getSkillId() {
        return skillId;
    }

    public void setSkillId(String skillId) {
        this.skillId = skillId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillDescription() {
        return skillDescription;
    }

    public void setSkillDescription(String skillDescription) {
        this.skillDescription = skillDescription;
    }

    public String getSkillType() {
        return skillType;
    }

    public void setSkillType(String skillType) {
        this.skillType = skillType;
    }

    public String getSkillContent() {
        return skillContent;
    }

    public void setSkillContent(String skillContent) {
        this.skillContent = skillContent;
    }

    public List<String> getBoundTools() {
        return boundTools;
    }

    public void setBoundTools(List<String> boundTools) {
        this.boundTools = boundTools;
    }

    public Map<String, String> getResources() {
        return resources;
    }

    public void setResources(Map<String, String> resources) {
        this.resources = resources;
    }

    public Map<String, Object> getExecution() {
        return execution;
    }

    public void setExecution(Map<String, Object> execution) {
        this.execution = execution;
    }

    public Map<String, Object> getDependencies() {
        return dependencies;
    }

    public void setDependencies(Map<String, Object> dependencies) {
        this.dependencies = dependencies;
    }

    public Map<String, Object> getPresetParameters() {
        return presetParameters;
    }

    public void setPresetParameters(Map<String, Object> presetParameters) {
        this.presetParameters = presetParameters;
    }

    public int getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(int skillVersion) {
        this.skillVersion = skillVersion;
    }

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
    }

    public TrustLevel getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(TrustLevel trustLevel) {
        this.trustLevel = trustLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public java.math.BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(java.math.BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public java.time.LocalDateTime getEvaluatedTime() {
        return evaluatedTime;
    }

    public void setEvaluatedTime(java.time.LocalDateTime evaluatedTime) {
        this.evaluatedTime = evaluatedTime;
    }
}
