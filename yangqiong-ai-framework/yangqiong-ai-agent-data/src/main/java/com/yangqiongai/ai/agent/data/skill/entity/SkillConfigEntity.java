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
 * 技能配置
 * @author yangqiong
 */
@TableName("ai_agent_skill_config")
public class SkillConfigEntity extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

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
     * 技能类型：BUILTIN/UPLOADED/GENERATED
     */
    private String skillType;

    /**
     * Markdown内容
     */
    private String skillContent;

    /**
     * 绑定工具列表JSON
     */
    private String boundTools;

    /**
     * 资源文件JSON（路径→内容映射）
     */
    private String resources;

    /**
     * 执行配置JSON
     */
    private String execution;

    /**
     * 依赖配置JSON
     */
    private String dependencies;

    /**
     * 预置参数JSON
     */
    private String presetParameters;

    /**
     * 条件配置JSON
     */
    private String conditions;

    /**
     * 状态：0-禁用 1-启用
     */
    private Integer skillStatus;

    /**
     * 版本号
     */
    private Integer skillVersion;

    /**
     * 备注
     */
    private String remark;

    /**
     * 信任等级（BUILTIN/TRUSTED/COMMUNITY/AGENT_CREATED）
     */
    private String trustLevel;

    /**
     * 所属分类编码（空为未分类）
     */
    private String category;

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

    public String getBoundTools() {
        return boundTools;
    }

    public void setBoundTools(String boundTools) {
        this.boundTools = boundTools;
    }

    public String getResources() {
        return resources;
    }

    public void setResources(String resources) {
        this.resources = resources;
    }

    public String getExecution() {
        return execution;
    }

    public void setExecution(String execution) {
        this.execution = execution;
    }

    public String getDependencies() {
        return dependencies;
    }

    public void setDependencies(String dependencies) {
        this.dependencies = dependencies;
    }

    public String getPresetParameters() {
        return presetParameters;
    }

    public void setPresetParameters(String presetParameters) {
        this.presetParameters = presetParameters;
    }

    public String getConditions() {
        return conditions;
    }

    public void setConditions(String conditions) {
        this.conditions = conditions;
    }

    public Integer getSkillStatus() {
        return skillStatus;
    }

    public void setSkillStatus(Integer skillStatus) {
        this.skillStatus = skillStatus;
    }

    public Integer getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(Integer skillVersion) {
        this.skillVersion = skillVersion;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public String getTrustLevel() {
        return trustLevel;
    }

    public void setTrustLevel(String trustLevel) {
        this.trustLevel = trustLevel;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
