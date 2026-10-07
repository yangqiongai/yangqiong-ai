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

/**
 * 技能生成草稿信息
 * @author yangqiong
 */
public class SkillDraftInfo {

    /**
     * 草稿ID
     */
    private String draftId;

    /**
     * 技能名称
     */
    private String skillName;

    /**
     * 技能描述（LLM提取的规范）
     */
    private String skillDescription;

    /**
     * Markdown内容
     */
    private String skillContent;

    /**
     * 绑定工具列表JSON
     */
    private String boundTools;

    /**
     * 生成状态
     */
    private String generateStatus;

    public String getDraftId() {
        return draftId;
    }

    public void setDraftId(String draftId) {
        this.draftId = draftId;
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

    public String getGenerateStatus() {
        return generateStatus;
    }

    public void setGenerateStatus(String generateStatus) {
        this.generateStatus = generateStatus;
    }

}
