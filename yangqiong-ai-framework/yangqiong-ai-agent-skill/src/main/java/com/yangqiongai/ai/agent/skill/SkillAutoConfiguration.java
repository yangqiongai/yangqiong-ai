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
package com.yangqiongai.ai.agent.skill;

import com.yangqiongai.ai.agent.skill.binding.SkillDependencyAssembler;
import com.yangqiongai.ai.agent.skill.binding.SkillBindingResolver;
import com.yangqiongai.ai.agent.skill.binding.SkillKitAssembler;
import com.yangqiongai.ai.agent.skill.config.SkillConditionalProperties;
import com.yangqiongai.ai.agent.skill.config.SkillProperties;
import com.yangqiongai.ai.agent.skill.generation.SkillContentValidator;
import com.yangqiongai.ai.agent.skill.integration.DelegatingSkillBoxProvider;
import com.yangqiongai.ai.agent.skill.repository.BuiltinSkillRepository;
import com.yangqiongai.ai.agent.skill.repository.CompositeSkillRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import com.yangqiongai.ai.agent.skill.provider.SkillSupplier;
import com.yangqiongai.ai.agent.skill.security.SkillSecurityScanner;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.model.ToolConfigInfo;
import com.yangqiongai.ai.agent.tool.ToolConfigManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 技能自动配置
 * @author yangqiong
 */
@Configuration
@ComponentScan
@EnableConfigurationProperties({SkillProperties.class, SkillConditionalProperties.class})
public class SkillAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(SkillAutoConfiguration.class);

    @Autowired(required = false)
    private List<Tool> toolProviders;

    @Autowired(required = false)
    private List<SkillSupplier> skillSuppliers;

    /**
     * 内置技能仓库（classpath技能常驻内存）
     * @return
     */
    @Bean
    public BuiltinSkillRepository builtinSkillRepository() {
        return new BuiltinSkillRepository();
    }

    /**
     * 技能仓库（组合classpath内置 + 数据库自定义）
     * @param skillConfigRepository
     * @param builtinSkillRepository
     * @return
     */
    @Bean
    public SkillRepository skillRepository(SkillConfigRepository skillConfigRepository, BuiltinSkillRepository builtinSkillRepository) {
        return new CompositeSkillRepository(builtinSkillRepository, skillConfigRepository);
    }

    /**
     * 内容校验器
     * @param properties
     * @return
     */
    @Bean
    public SkillContentValidator skillContentValidator(SkillProperties properties) {
        return new SkillContentValidator(properties);
    }

    /**
     * 技能绑定解析器
     * @param skillRepository
     * @param conditionalProperties
     * @return
     */
    @Bean
    public SkillBindingResolver skillBindingResolver(SkillRepository skillRepository, SkillConditionalProperties conditionalProperties) {
        return new SkillBindingResolver(skillRepository, conditionalProperties);
    }

    /**
     * 运行时依赖装配器
     * @param bindingResolver
     * @param skillRepository
     * @param aiToolConfigService
     * @return
     */
    @Bean
    public SkillDependencyAssembler skillDependencyAssembler(SkillBindingResolver bindingResolver, SkillRepository skillRepository,
                                                              @Autowired(required = false) ToolConfigManager toolConfigManager) {
        Set<String> enabledToolCodes = resolveEnabledToolCodes(toolConfigManager);
        return new SkillDependencyAssembler(bindingResolver, skillRepository, toolProviders, enabledToolCodes);
    }

    /**
     * 查询已启用的工具编码集合
     * @param aiToolConfigService
     * @return 已启用工具编码集合，查询失败时返回null（不做过滤）
     */
    private Set<String> resolveEnabledToolCodes(ToolConfigManager toolConfigManager) {
        if (toolConfigManager == null) {
            return null;
        }
        try {
            List<ToolConfigInfo> enabledConfigs = toolConfigManager.listByStatus(1);
            if (enabledConfigs == null || enabledConfigs.isEmpty()) {
                return null;
            }
            Set<String> codes = enabledConfigs.stream()
                    .map(ToolConfigInfo::getToolCode)
                    .collect(Collectors.toSet());
            log.debug("已启用工具编码: {}", codes);
            return codes;
        } catch (Exception e) {
            log.warn("查询已启用工具编码失败,技能绑定工具将不做启用过滤: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 技能箱装配器
     * @param bindingResolver
     * @param dependencyAssembler
     * @return
     */
    @Bean
    public SkillKitAssembler skillKitAssembler(SkillBindingResolver bindingResolver, SkillDependencyAssembler dependencyAssembler) {
        return new SkillKitAssembler(bindingResolver, dependencyAssembler, skillSuppliers);
    }

    /**
     * 技能箱提供者（桥接ai-agent-core与ai-agent-skill）
     * <p>
     * 实现ai-agent-core的SkillBoxProvider接口，由AbstractAgentProcessor自动注入。
     * 当ai-agent-skill未启用时，此Bean不存在，AbstractAgentProcessor降级为无技能模式。
     * </p>
     * @param skillKitAssembler
     * @param securityScanner
     * @param skillRepository
     * @return
     */
    @Bean
    public DelegatingSkillBoxProvider delegatingSkillBoxProvider(SkillKitAssembler skillKitAssembler,
                                                                  SkillSecurityScanner securityScanner,
                                                                  SkillRepository skillRepository) {
        return new DelegatingSkillBoxProvider(skillKitAssembler, securityScanner, skillRepository);
    }
}
