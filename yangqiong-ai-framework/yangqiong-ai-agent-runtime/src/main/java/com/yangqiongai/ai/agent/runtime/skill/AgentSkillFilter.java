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
package com.yangqiongai.ai.agent.runtime.skill;

import java.util.List;

/**
 * Agent技能过滤配置
 * @author yangqiong
 */
public final class AgentSkillFilter {

    /**
     * 过滤模式
     */
    private final AgentSkillFilterMode mode;

    /**
     * 技能名称列表
     */
    private final List<String> skills;

    private AgentSkillFilter(AgentSkillFilterMode mode, List<String> skills) {
        this.mode = mode;
        this.skills = skills != null ? List.copyOf(skills) : List.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取过滤模式
     * @return
     */
    public AgentSkillFilterMode getMode() {
        return mode;
    }

    /**
     * 获取技能名称列表
     * @return
     */
    public List<String> getSkills() {
        return skills;
    }

    /**
     * 技能过滤构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 过滤模式
         */
        private AgentSkillFilterMode mode;

        /**
         * 技能名称列表
         */
        private List<String> skills;

        public Builder mode(AgentSkillFilterMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder skills(List<String> skills) {
            this.skills = skills;
            return this;
        }

        public AgentSkillFilter build() {
            return new AgentSkillFilter(mode, skills);
        }
    }
}
