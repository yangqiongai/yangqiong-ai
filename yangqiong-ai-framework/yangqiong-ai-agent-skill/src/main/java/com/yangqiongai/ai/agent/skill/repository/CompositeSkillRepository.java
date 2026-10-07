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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 组合技能仓库
 * <p>
 * 合并 classpath 内置技能和数据库自定义技能，数据库技能优先。
 * </p>
 * @author yangqiong
 */
public class CompositeSkillRepository implements SkillRepository {

    private static final Logger log = LoggerFactory.getLogger(CompositeSkillRepository.class);

    private final SkillRepository classpathRepository;
    private final SkillConfigRepository skillConfigRepository;

    public CompositeSkillRepository(SkillRepository classpathRepository, SkillConfigRepository skillConfigRepository) {
        this.classpathRepository = classpathRepository;
        this.skillConfigRepository = skillConfigRepository;
    }

    @Override
    public Optional<SkillDefinition> findById(String skillId) {
        // 数据库优先，但仅启用状态有效
        SkillDefinition dbSkill = skillConfigRepository.getBySkillId(skillId);
        if (dbSkill != null) {
            return Optional.of(dbSkill);
        }
        // 数据库无启用记录 → fallback到内置技能（数据库禁用不影响同ID内置技能）
        return classpathRepository.findById(skillId);
    }

    @Override
    public List<SkillDefinition> findAll() {
        return mergeSkills(classpathRepository.findAll(), skillConfigRepository.listEnabled());
    }

    @Override
    public List<SkillDefinition> listByTrustLevel(TrustLevel trustLevel) {
        // 合并后按信任等级过滤，覆盖同ID的数据库技能需与内置技能同等级才保留
        return mergeSkills(classpathRepository.listByTrustLevel(trustLevel),
                skillConfigRepository.listByTrustLevel(trustLevel)).stream()
                .filter(s -> s.getTrustLevel() == trustLevel)
                .toList();
    }

    /**
     * 合并classpath和数据库技能，数据库技能优先覆盖
     * @param classpathSkills
     * @param dbSkills
     * @return
     */
    private List<SkillDefinition> mergeSkills(List<SkillDefinition> classpathSkills, List<SkillDefinition> dbSkills) {
        Map<String, SkillDefinition> merged = new LinkedHashMap<>();
        for (SkillDefinition s : classpathSkills) {
            merged.put(s.getSkillId(), s);
        }
        for (SkillDefinition s : dbSkills) {
            merged.put(s.getSkillId(), s);
        }
        return new ArrayList<>(merged.values());
    }

    @Override
    public void save(SkillDefinition skill) {
        // 变更说明随定义透传至版本快照changeLog
        skillConfigRepository.saveSkill(skill, null, skill.getRemark());
        log.info("保存技能到数据库: skillId={}", skill.getSkillId());
    }

    @Override
    public void deleteById(String skillId) {
        skillConfigRepository.deleteBySkillId(skillId);
        log.info("从数据库删除技能: skillId={}", skillId);
    }
}
