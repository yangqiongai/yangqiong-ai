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
package com.yangqiongai.ai.agent.skill.repository;

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;

import java.util.List;
import java.util.Optional;

/**
 * 技能仓库
 * @author yangqiong
 */
public interface SkillRepository {

    /**
     * 根据ID查找技能
     * @param skillId
     * @return
     */
    Optional<SkillDefinition> findById(String skillId);

    /**
     * 查找所有技能
     * @return
     */
    List<SkillDefinition> findAll();

    /**
     * 按信任等级查找技能（支撑BUILTIN内置技能自动装配）
     * @param trustLevel
     * @return
     */
    List<SkillDefinition> listByTrustLevel(TrustLevel trustLevel);

    /**
     * 保存技能
     * @param skill
     */
    void save(SkillDefinition skill);

    /**
     * 删除技能
     * @param skillId
     */
    void deleteById(String skillId);
}
