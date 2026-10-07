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

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.skill.model.SkillBox;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.provider.SkillSupplier;

import java.util.List;
import java.util.Map;

/**
 * 技能箱装配器
 * <p>
 * 根据请求装配技能箱，集成 SkillSupplier 实现技能内容定制（占位符替换、运行时上下文注入）。
 * 支持多 SkillSupplier 链式调用。
 * </p>
 *
 * @author yangqiong
 */
public class SkillKitAssembler {

    private final SkillBindingResolver bindingResolver;

    private final SkillDependencyAssembler dependencyAssembler;

    private final List<SkillSupplier> skillSuppliers;

    public SkillKitAssembler(SkillBindingResolver bindingResolver, SkillDependencyAssembler dependencyAssembler,
                              List<SkillSupplier> skillSuppliers) {
        this.bindingResolver = bindingResolver;
        this.dependencyAssembler = dependencyAssembler;
        this.skillSuppliers = skillSuppliers != null ? skillSuppliers : List.of();
    }

    /**
     * 根据请求装配技能箱
     * @param request Agent请求
     * @return 技能箱
     */
    public SkillBox assemble(AgentRequest request) {
        return assembleWithToolkit(request).getSkillBox();
    }

    /**
     * 根据请求装配技能箱并注册关联工具到工具箱
     * @param request Agent请求
     * @return 装配结果
     */
    public SkillDependencyAssembler.AssemblyResult assembleWithToolkit(AgentRequest request) {
        Map<String, Object> context = request.getBody() != null ? request.getBody() : Map.of();
        List<SkillDefinition> bindings = bindingResolver.resolveBindings(request.getAgentCode(), context);
        List<SkillDefinition> customized = customizeBindings(request, bindings);
        return dependencyAssembler.assembleWithToolkit(customized);
    }

    /**
     * 对绑定技能列表进行链式定制（遍历所有 SkillSupplier）
     * @param request Agent请求
     * @param bindings 原始绑定列表
     * @return 定制后的绑定列表
     */
    private List<SkillDefinition> customizeBindings(AgentRequest request, List<SkillDefinition> bindings) {
        if (skillSuppliers.isEmpty() || bindings == null || bindings.isEmpty()) {
            return bindings;
        }
        return bindings.stream()
                .map(skill -> applySupplyChain(request, skill))
                .toList();
    }

    /**
     * 链式应用所有 SkillSupplier
     * @param request
     * @param skill
     * @return
     */
    private SkillDefinition applySupplyChain(AgentRequest request, SkillDefinition skill) {
        SkillDefinition current = skill;
        for (SkillSupplier supplier : skillSuppliers) {
            if (supplier.canHandle(request, current.getSkillId())) {
                current = supplier.customize(request, current.getSkillId(), current);
            }
        }
        return current;
    }
}
