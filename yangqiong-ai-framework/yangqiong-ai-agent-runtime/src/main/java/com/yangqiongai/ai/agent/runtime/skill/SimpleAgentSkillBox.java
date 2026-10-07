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

import java.util.ArrayList;
import java.util.List;

/**
 * 简单技能箱实现（过滤等衍生场景构造新箱使用）
 * @author yangqiong
 */
public class SimpleAgentSkillBox implements AgentSkillBox {

    /**
     * 技能列表
     */
    private final List<AgentSkill> skills = new ArrayList<>();

    @Override
    public void addSkill(AgentSkill skill) {
        if (skill != null) {
            skills.add(skill);
        }
    }

    @Override
    public List<AgentSkill> getSkills() {
        return skills;
    }

    @Override
    public boolean isEmpty() {
        return skills.isEmpty();
    }
}
