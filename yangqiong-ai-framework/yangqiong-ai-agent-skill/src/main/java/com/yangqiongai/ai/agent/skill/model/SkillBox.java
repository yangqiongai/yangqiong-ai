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

import java.util.*;

/**
 * 技能箱，管理已加载的技能
 * @author yangqiong
 */
public class SkillBox {

    /**
     * 已注册技能（skillId -> SkillDefinition）
     */
    private final Map<String, SkillDefinition> registeredSkills = new LinkedHashMap<>();

    /**
     * 已激活技能（skillId -> 完整内容）
     */
    private final Map<String, String> activatedSkills = new LinkedHashMap<>();

    /**
     * 注册技能
     * @param skill
     */
    public void register(SkillDefinition skill) {
        registeredSkills.put(skill.getSkillId(), skill);
    }

    /**
     * 激活技能（加载完整内容）
     * @param skillId
     * @param content
     */
    public void activate(String skillId, String content) {
        activatedSkills.put(skillId, content);
    }

    /**
     * 获取已注册技能列表
     * @return
     */
    public List<SkillDefinition> getRegisteredSkills() {
        return new ArrayList<>(registeredSkills.values());
    }

    /**
     * 获取技能名称和描述的摘要（供LLM查看）
     * @return
     */
    public String getSkillSummary() {
        StringBuilder sb = new StringBuilder();
        for (SkillDefinition skill : registeredSkills.values()) {
            sb.append("- ").append(skill.getSkillId())
              .append(": ").append(skill.getSkillDescription()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 获取已激活技能的完整内容
     * @return
     */
    public String getActivatedContent() {
        return String.join("\n\n---\n\n", activatedSkills.values());
    }

    /**
     * 获取所有绑定工具
     * @return
     */
    public List<String> getAllBoundTools() {
        return registeredSkills.values().stream()
                .filter(s -> s.getBoundTools() != null)
                .flatMap(s -> s.getBoundTools().stream())
                .distinct()
                .toList();
    }

    /**
     * 是否已激活
     * @param skillId
     * @return
     */
    public boolean isActivated(String skillId) {
        return activatedSkills.containsKey(skillId);
    }
}
