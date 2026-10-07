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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.core.skill.AgentSkill;
import com.yangqiongai.agent.harness.core.skill.AgentSkillBox;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent技能箱适配器
 * @author yangqiong
 */
public class AgentSkillBoxAdapter implements AgentSkillBox {

    /**
     * 框架技能箱委托
     */
    private final com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox delegate;

    public AgentSkillBoxAdapter(com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取技能列表
     * @return
     */
    @Override
    public List<AgentSkill> getSkills() {
        List<com.yangqiongai.ai.agent.runtime.skill.AgentSkill> skills = delegate.getSkills();
        if (skills == null || skills.isEmpty()) {
            return List.of();
        }
        List<AgentSkill> out = new ArrayList<>(skills.size());
        for (com.yangqiongai.ai.agent.runtime.skill.AgentSkill skill : skills) {
            out.add(SpiConverters.toHarnessSkill(skill));
        }
        return out;
    }

    /**
     * 技能箱是否为空
     * @return
     */
    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * 添加技能
     * @param skill
     */
    @Override
    public void addSkill(AgentSkill skill) {
        delegate.addSkill(SpiConverters.toRuntimeSkill(skill));
    }

    /**
     * 获取被包装的框架技能箱
     * @return
     */
    com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox getDelegate() {
        return delegate;
    }
}
