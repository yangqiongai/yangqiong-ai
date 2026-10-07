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
package com.yangqiongai.ai.agent.skill.binding;

import com.yangqiongai.ai.agent.skill.config.SkillConditionalProperties;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 技能绑定解析器
 * <p>
 * 两态语义：BUILTIN内置技能始终自动装配不过滤；非内置技能由请求级清单
 * （agentConfig.skills与请求级覆盖合并后经body.skillIds传入）与叠加来源命中。
 * </p>
 * @author yangqiong
 */
public class SkillBindingResolver {

    private static final Logger log = LoggerFactory.getLogger(SkillBindingResolver.class);

    private final SkillRepository skillRepository;

    private final SkillConditionalProperties conditionalProperties;

    /**
     * 任务默认技能映射
     */
    private final Map<String, List<String>> defaultSkills = new java.util.concurrent.ConcurrentHashMap<>();

    public SkillBindingResolver(SkillRepository skillRepository, SkillConditionalProperties conditionalProperties) {
        this.skillRepository = skillRepository;
        this.conditionalProperties = conditionalProperties;
    }

    /**
     * 注册任务默认技能
     * @param agentCode
     * @param skillIds
     */
    public void registerSkills(String agentCode, List<String> skillIds) {
        defaultSkills.put(agentCode, skillIds);
    }

    /**
     * 解析技能绑定
     * <p>
     * BUILTIN内置技能自动装配（不受清单影响）；任务默认技能、条件配置映射为叠加来源；
     * 请求级skillIds为Agent正向持有的清单（agentConfig.skills与请求级覆盖合并后传入）。
     * LinkedHashSet自动去重，避免重复加载。
     * </p>
     * @param agentCode
     * @param context
     * @return
     */
    public List<SkillDefinition> resolveBindings(String agentCode, Map<String, Object> context) {
        LinkedHashSet<String> skillIds = new LinkedHashSet<>();

        // BUILTIN内置技能自动装配
        for (SkillDefinition skill : skillRepository.listByTrustLevel(TrustLevel.BUILTIN)) {
            if (skill.getSkillId() != null) {
                skillIds.add(skill.getSkillId());
            }
        }

        // 任务默认技能（叠加来源）
        skillIds.addAll(defaultSkills.getOrDefault(agentCode, Collections.emptyList()));

        // 条件配置映射（叠加来源）
        if (conditionalProperties != null && conditionalProperties.getConditionalMapping() != null) {
            String mappedSkillIds = conditionalProperties.getConditionalMapping().get(agentCode);
            if (mappedSkillIds != null) {
                skillIds.addAll(Arrays.stream(mappedSkillIds.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toList()));
            }
        }

        // 请求级技能清单（Agent正向持有，叠加来源）
        if (context != null) {
            @SuppressWarnings("unchecked")
            List<String> contextSkills = (List<String>) context.get("skillIds");
            if (contextSkills != null) {
                skillIds.addAll(contextSkills);
            }
        }

        if (skillIds.isEmpty()) {
            log.info("解析技能绑定 agentCode={}, count=0", agentCode);
            return Collections.emptyList();
        }

        List<SkillDefinition> resolved = skillIds.stream()
                .map(skillRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        log.info("解析技能绑定 agentCode={}, count={}", agentCode, resolved.size());
        return resolved;
    }
}
