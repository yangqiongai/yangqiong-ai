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

import com.yangqiongai.ai.agent.skill.model.SkillBox;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import com.yangqiongai.ai.agent.tool.Tool;
import com.yangqiongai.ai.agent.tool.Toolkit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 运行时依赖装配器
 * @author yangqiong
 */
public class SkillDependencyAssembler {

    private static final Logger log = LoggerFactory.getLogger(SkillDependencyAssembler.class);

    private final SkillBindingResolver bindingResolver;

    private final SkillRepository skillRepository;

    /**
     * 项目工具提供者列表
     */
    private final List<Tool> toolProviders;

    /**
     * 已启用的工具编码集合（来自全局工具配置表，用于过滤技能绑定的工具）
     */
    private final Set<String> enabledToolCodes;

    public SkillDependencyAssembler(SkillBindingResolver bindingResolver, SkillRepository skillRepository, List<Tool> toolProviders) {
        this(bindingResolver, skillRepository, toolProviders, null);
    }

    public SkillDependencyAssembler(SkillBindingResolver bindingResolver, SkillRepository skillRepository,
                                    List<Tool> toolProviders, Set<String> enabledToolCodes) {
        this.bindingResolver = bindingResolver;
        this.skillRepository = skillRepository;
        this.toolProviders = toolProviders != null ? toolProviders : List.of();
        this.enabledToolCodes = enabledToolCodes;
    }

    /**
     * 从绑定列表装配技能箱
     * @param bindings
     * @return
     */
    public SkillBox assemble(List<SkillDefinition> bindings) {
        return doAssemble(bindings).skillBox;
    }

    /**
     * 从绑定列表装配技能箱并注册关联工具到工具箱
     * @param bindings
     * @return
     */
    public AssemblyResult assembleWithToolkit(List<SkillDefinition> bindings) {
        return doAssemble(bindings);
    }

    /**
     * 统一装配逻辑
     * @param bindings
     * @return
     */
    private AssemblyResult doAssemble(List<SkillDefinition> bindings) {
        SkillBox skillBox = new SkillBox();
        if (bindings == null || bindings.isEmpty()) {
            return new AssemblyResult(skillBox, new Toolkit(), List.of());
        }

        List<String> boundToolNames = new ArrayList<>();
        bindings.forEach(skill -> {
            skillBox.register(skill);
            skillBox.activate(skill.getSkillId(), skill.getSkillContent());
            if (skill.getBoundTools() != null) {
                boundToolNames.addAll(skill.getBoundTools());
            }
        });

        List<String> distinctToolNames = boundToolNames.stream().distinct().collect(Collectors.toList());
        Toolkit toolkit = buildToolkit(distinctToolNames);

        log.info("装配技能箱及工具箱, skillCount={}, boundToolNames={}", bindings.size(), distinctToolNames);
        return new AssemblyResult(skillBox, toolkit, distinctToolNames);
    }

    /**
     * 根据工具名称列表构建工具箱
     * <p>
     * 过滤全局禁用的工具：如果工具在配置表中已被禁用，
     * 即使技能声明了 boundTools 也不会注册到技能绑定工具箱。
     * </p>
     * @param toolNames
     * @return
     */
    private Toolkit buildToolkit(List<String> toolNames) {
        Toolkit toolkit = new Toolkit();
        if (toolNames == null || toolNames.isEmpty()) {
            return toolkit;
        }

        for (Tool provider : toolProviders) {
            String providerName = provider.getToolCode() != null ? provider.getToolCode() : provider.getClass().getSimpleName();
            if (toolNames.contains(providerName)) {
                // 全局禁用检查：enabledToolCodes 非空时，仅注册已启用的工具
                if (enabledToolCodes != null && !enabledToolCodes.contains(providerName)) {
                    log.debug("技能绑定工具 {} 全局已禁用,跳过", providerName);
                    continue;
                }
                toolkit.addTool(provider);
            }
        }

        return toolkit;
    }

    /**
     * 装配结果
     */
    public static class AssemblyResult {

        /**
         * 技能箱
         */
        private final SkillBox skillBox;

        /**
         * 工具箱
         */
        private final Toolkit toolkit;

        /**
         * 绑定工具名称列表
         */
        private final List<String> boundToolNames;

        public AssemblyResult(SkillBox skillBox, Toolkit toolkit, List<String> boundToolNames) {
            this.skillBox = skillBox;
            this.toolkit = toolkit;
            this.boundToolNames = boundToolNames;
        }

        public SkillBox getSkillBox() {
            return skillBox;
        }

        public Toolkit getToolkit() {
            return toolkit;
        }

        public List<String> getBoundToolNames() {
            return boundToolNames;
        }
    }
}
