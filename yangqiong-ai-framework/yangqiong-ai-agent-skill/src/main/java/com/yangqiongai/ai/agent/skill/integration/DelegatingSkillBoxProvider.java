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
package com.yangqiongai.ai.agent.skill.integration;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkill;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillRepository;
import com.yangqiongai.ai.agent.skill.DefaultAgentSkillBox;
import com.yangqiongai.ai.agent.core.provider.SkillBoxProvider;
import com.yangqiongai.ai.agent.core.provider.SkillMiddlewareProvider;
import com.yangqiongai.ai.agent.skill.binding.SkillDependencyAssembler;
import com.yangqiongai.ai.agent.skill.binding.SkillKitAssembler;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import com.yangqiongai.ai.agent.skill.security.SkillSecurityScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 技能箱提供者桥接实现
 * @author yangqiong
 */
public class DelegatingSkillBoxProvider implements SkillBoxProvider, SkillMiddlewareProvider {

    private static final Logger log = LoggerFactory.getLogger(DelegatingSkillBoxProvider.class);

    private final SkillKitAssembler skillKitAssembler;

    /**
     * 安全扫描器（可选依赖）
     */
    private final SkillSecurityScanner securityScanner;

    /**
     * 技能仓库（CompositeSkillRepository，包含内置 + 数据库技能）
     */
    private final SkillRepository skillRepository;

    public DelegatingSkillBoxProvider(SkillKitAssembler skillKitAssembler, SkillSecurityScanner securityScanner) {
        this(skillKitAssembler, securityScanner, null);
    }

    public DelegatingSkillBoxProvider(SkillKitAssembler skillKitAssembler, SkillSecurityScanner securityScanner,
                                      SkillRepository skillRepository) {
        this.skillKitAssembler = skillKitAssembler;
        this.securityScanner = securityScanner;
        this.skillRepository = skillRepository;
    }

    /**
     * 按请求装配技能箱
     * @param request Agent请求
     * @return 框架层技能箱，无技能时返回null
     */
    @Override
    public AgentSkillBox resolveSkillBox(AgentRequest request) {
        if (skillKitAssembler == null) {
            return null;
        }

        try {
            SkillDependencyAssembler.AssemblyResult result = skillKitAssembler.assembleWithToolkit(request);
            com.yangqiongai.ai.agent.skill.model.SkillBox customBox = result.getSkillBox();

            if (customBox == null || customBox.getRegisteredSkills().isEmpty()) {
                log.debug("无绑定技能，跳过技能箱装配: agentCode={}", request.getAgentCode());
                return null;
            }

            AgentSkillBox skillBox = new DefaultAgentSkillBox();

            // 转换并注册技能
            for (SkillDefinition def : customBox.getRegisteredSkills()) {
                AgentSkill agentSkill = convertToAgentSkill(def);
                if (agentSkill != null) {
                    // 安全扫描（仅对UPLOADED/GENERATED类型）
                    if (securityScanner != null) {
                        SkillSecurityScanner.SecurityScanResult scanResult = securityScanner.scan(agentSkill,
                                def.getTrustLevel() != null ? def.getTrustLevel().name() : null);
                        if (!scanResult.isAllowed()) {
                            log.warn("技能安全扫描未通过，跳过注册: skillId={}, verdict=blocked, warnings={}",
                                    def.getSkillId(), scanResult.getWarnings().size());
                            continue;
                        }
                        if (!scanResult.isSafe()) {
                            log.warn("技能安全扫描发现风险但允许注册: skillId={}, warnings={}, report={}",
                                    def.getSkillId(), scanResult.getWarnings().size(), scanResult.getReportText());
                        }
                    }
                    skillBox.addSkill(agentSkill);
                }
            }

            log.info("技能箱装配完成: agentCode={}, skillCount={}",
                    request.getAgentCode(), customBox.getRegisteredSkills().size());
            return skillBox;
        } catch (Exception e) {
            log.warn("技能箱装配异常: agentCode={}", request.getAgentCode(), e);
            return null;
        }
    }

    /**
     * 将SkillDefinition转换为框架层AgentSkill
     * @param def
     * @return
     */
    private AgentSkill convertToAgentSkill(SkillDefinition def) {
        if (def == null || def.getSkillId() == null) {
            return null;
        }
        String name = def.getSkillId();
        String source = def.getSkillType() != null ? def.getSkillType() : "custom";
        String description = def.getSkillDescription() != null ? def.getSkillDescription() : "";
        String skillContent = def.getSkillContent() != null ? def.getSkillContent() : "";
        Map<String, String> resources = def.getResources();
        return new AgentSkill(name, source, description, skillContent, resources);
    }

    /**
     * 创建技能中间件，用于动态重建模式
     * <p>
     * 广告源与装配路径同源（BUILTIN自动装配 + agentConfig清单绑定 + 安全校验），
     * 确保提示词展示的技能与load_skill可加载范围一致，非内置技能经agentConfig.skills挂载后生效。
     * </p>
     * @param request Agent请求
     * @return AgentMiddleware实例，无法创建时返回null
     */
    @Override
    public AgentMiddleware resolveSkillMiddleware(AgentRequest request) {
        if (skillRepository == null) {
            log.warn("SkillRepository未注入，无法创建技能中间件");
            return null;
        }
        try {
            // 与装配路径同源取技能集，避免中间件绕过绑定治理广告未挂载技能
            AgentSkillBox governedBox = resolveSkillBox(request);
            if (governedBox == null || governedBox.isEmpty()) {
                return null;
            }
            List<AgentSkillRepository> repositories = List.of(new BoxedSkillRepository(governedBox.getSkills()));
            // 动态重建路径同样应用请求级skillFilter，与装配路径语义一致
            return SkillMiddlewareFactory.create(repositories,
                    request != null ? request.getSkillFilter() : null);
        } catch (Exception e) {
            log.warn("创建技能中间件失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 技能箱静态仓库视图，将已装配技能集适配为框架层技能仓库
     */
    private static class BoxedSkillRepository implements AgentSkillRepository {

        private final List<AgentSkill> skills;

        BoxedSkillRepository(List<AgentSkill> skills) {
            this.skills = skills != null ? List.copyOf(skills) : List.of();
        }

        @Override
        public List<AgentSkill> findAll() {
            return skills;
        }

        @Override
        public List<AgentSkill> findBySource(String source) {
            List<AgentSkill> result = new ArrayList<>();
            for (AgentSkill skill : skills) {
                if (source != null && source.equals(skill.getSource())) {
                    result.add(skill);
                }
            }
            return result;
        }

        @Override
        public AgentSkill findByName(String name) {
            if (name == null) {
                return null;
            }
            for (AgentSkill skill : skills) {
                if (name.equals(skill.getName())) {
                    return skill;
                }
            }
            return null;
        }
    }
}
