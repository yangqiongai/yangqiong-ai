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
import com.yangqiongai.ai.agent.skill.binding.SkillBindingResolver;
import com.yangqiongai.ai.agent.skill.binding.SkillDependencyAssembler;
import com.yangqiongai.ai.agent.skill.binding.SkillKitAssembler;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.model.TrustLevel;
import com.yangqiongai.ai.agent.skill.repository.SkillRepository;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 技能箱提供者桥接测试
 * @author yangqiong
 */
class DelegatingSkillBoxProviderTest {

    @Test
    void shouldAdvertiseOnlyGovernedSkills() {
        SkillRepository repository = createRepository(
                createDefinition("task-planning", TrustLevel.BUILTIN),
                createDefinition("web-search", TrustLevel.BUILTIN),
                createDefinition("ui-demo-skill", TrustLevel.TRUSTED));
        DelegatingSkillBoxProvider provider = createProvider(repository);

        AgentMiddleware middleware = provider.resolveSkillMiddleware(createRequest(new HashMap<>()));
        assertNotNull(middleware);

        String prompt = middleware.onSystemPrompt("BASE", null);
        assertTrue(prompt.contains("- task-planning"));
        assertTrue(prompt.contains("- web-search"));
        // 未挂载的非内置技能不广告，与load_skill可加载范围保持一致
        assertFalse(prompt.contains("ui-demo-skill"));
    }

    @Test
    void shouldAdvertiseManifestSkillsAfterBinding() {
        SkillRepository repository = createRepository(
                createDefinition("task-planning", TrustLevel.BUILTIN),
                createDefinition("ui-demo-skill", TrustLevel.TRUSTED));
        DelegatingSkillBoxProvider provider = createProvider(repository);

        Map<String, Object> body = new HashMap<>();
        body.put("skillIds", List.of("ui-demo-skill"));
        AgentMiddleware middleware = provider.resolveSkillMiddleware(createRequest(body));
        assertNotNull(middleware);

        String prompt = middleware.onSystemPrompt("BASE", null);
        assertTrue(prompt.contains("- task-planning"));
        assertTrue(prompt.contains("ui-demo-skill"));
    }

    @Test
    void shouldKeepBoxAndAdvertisementConsistent() {
        SkillRepository repository = createRepository(
                createDefinition("task-planning", TrustLevel.BUILTIN),
                createDefinition("code-assistant", TrustLevel.BUILTIN),
                createDefinition("ui-demo-skill", TrustLevel.TRUSTED));
        DelegatingSkillBoxProvider provider = createProvider(repository);
        AgentRequest request = createRequest(new HashMap<>());

        AgentSkillBox box = provider.resolveSkillBox(request);
        assertNotNull(box);
        AgentMiddleware middleware = provider.resolveSkillMiddleware(request);
        assertNotNull(middleware);

        String prompt = middleware.onSystemPrompt("BASE", null);
        for (AgentSkill skill : box.getSkills()) {
            assertTrue(prompt.contains("- " + skill.getName()));
        }
    }

    @Test
    void shouldApplyRequestLevelSkillFilter() {
        SkillRepository repository = createRepository(
                createDefinition("task-planning", TrustLevel.BUILTIN),
                createDefinition("web-search", TrustLevel.BUILTIN));
        DelegatingSkillBoxProvider provider = createProvider(repository);

        Map<String, Object> body = new HashMap<>();
        body.put(AgentRequest.BodyKeys.SKILL_FILTER,
                Map.of("mode", "except", "skills", List.of("web-search")));
        AgentMiddleware middleware = provider.resolveSkillMiddleware(createRequest(body));
        assertNotNull(middleware);

        String prompt = middleware.onSystemPrompt("BASE", null);
        assertTrue(prompt.contains("- task-planning"));
        assertFalse(prompt.contains("- web-search"));
    }

    @Test
    void shouldReturnNullWhenRepositoryMissing() {
        SkillRepository emptyRepository = createRepository();
        SkillBindingResolver bindingResolver = new SkillBindingResolver(emptyRepository, null);
        SkillKitAssembler assembler = new SkillKitAssembler(bindingResolver,
                new SkillDependencyAssembler(bindingResolver, emptyRepository, List.of()), List.of());
        DelegatingSkillBoxProvider provider = new DelegatingSkillBoxProvider(assembler, null, null);

        assertNull(provider.resolveSkillMiddleware(createRequest(new HashMap<>())));
    }

    /**
     * 构建提供者实例
     * @param repository
     * @return
     */
    private DelegatingSkillBoxProvider createProvider(SkillRepository repository) {
        SkillBindingResolver bindingResolver = new SkillBindingResolver(repository, null);
        SkillDependencyAssembler dependencyAssembler =
                new SkillDependencyAssembler(bindingResolver, repository, List.of());
        SkillKitAssembler assembler = new SkillKitAssembler(bindingResolver, dependencyAssembler, List.of());
        return new DelegatingSkillBoxProvider(assembler, null, repository);
    }

    /**
     * 构建内存技能仓库
     * @param definitions
     * @return
     */
    private SkillRepository createRepository(SkillDefinition... definitions) {
        Map<String, SkillDefinition> store = new LinkedHashMap<>();
        for (SkillDefinition def : definitions) {
            store.put(def.getSkillId(), def);
        }
        return new SkillRepository() {

            @Override
            public Optional<SkillDefinition> findById(String skillId) {
                return Optional.ofNullable(store.get(skillId));
            }

            @Override
            public List<SkillDefinition> findAll() {
                return new ArrayList<>(store.values());
            }

            @Override
            public List<SkillDefinition> listByTrustLevel(TrustLevel trustLevel) {
                List<SkillDefinition> result = new ArrayList<>();
                for (SkillDefinition def : store.values()) {
                    if (def.getTrustLevel() == trustLevel) {
                        result.add(def);
                    }
                }
                return result;
            }

            @Override
            public void save(SkillDefinition skill) {
                store.put(skill.getSkillId(), skill);
            }

            @Override
            public void deleteById(String skillId) {
                store.remove(skillId);
            }
        };
    }

    /**
     * 构建技能定义
     * @param skillId
     * @param trustLevel
     * @return
     */
    private SkillDefinition createDefinition(String skillId, TrustLevel trustLevel) {
        SkillDefinition def = new SkillDefinition();
        def.setSkillId(skillId);
        def.setSkillName(skillId);
        def.setSkillDescription(skillId + "描述");
        def.setSkillType("UPLOADED");
        def.setSkillContent("# " + skillId + "\n技能内容");
        def.setTrustLevel(trustLevel);
        return def;
    }

    /**
     * 构建Agent请求
     * @param body
     * @return
     */
    private AgentRequest createRequest(Map<String, Object> body) {
        AgentRequest request = new AgentRequest();
        request.setAgentCode("testAgent");
        request.setBody(body);
        return request;
    }
}
