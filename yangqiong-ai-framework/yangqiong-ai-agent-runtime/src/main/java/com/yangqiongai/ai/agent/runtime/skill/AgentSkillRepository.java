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
 * Agent技能仓库
 * @author yangqiong
 */
public interface AgentSkillRepository {

    /**
     * 查询全部技能
     * @return
     */
    List<AgentSkill> findAll();

    /**
     * 按来源查询技能
     * @param source
     * @return
     */
    List<AgentSkill> findBySource(String source);

    /**
     * 按名称查询技能
     * @param name
     * @return
     */
    AgentSkill findByName(String name);
}
