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
package com.yangqiongai.ai.agent.registry.resolve;

import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentGrayRule;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentDefinitionMapper;
import com.yangqiongai.ai.agent.data.registry.mapper.AgentVersionMapper;
import com.yangqiongai.ai.agent.registry.config.AgentRegistryProperties;
import com.yangqiongai.ai.agent.registry.gray.GrayDecision;
import com.yangqiongai.ai.agent.registry.gray.GrayRouter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 注册中心Agent定义解析器单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RegistryAgentDefinitionResolver 单元测试")
class RegistryAgentDefinitionResolverTest {

    private static final String CONFIG_JSON = """
            {"model":"deepseek-v3","systemPrompt":"灰度配置","tools":["reagent_query"]}
            """;

    @Mock
    private GrayRouter grayRouter;

    @Mock
    private AgentDefinitionMapper definitionMapper;

    @Mock
    private AgentVersionMapper versionMapper;

    private RegistryAgentDefinitionResolver resolver;

    private AgentRegistryProperties properties;

    @BeforeEach
    void setUp() {
        resolver = new RegistryAgentDefinitionResolver();
        ReflectionTestUtils.setField(resolver, "grayRouter", grayRouter);
        ReflectionTestUtils.setField(resolver, "definitionMapper", definitionMapper);
        ReflectionTestUtils.setField(resolver, "versionMapper", versionMapper);
        properties = new AgentRegistryProperties();
        ReflectionTestUtils.setField(resolver, "properties", properties);
        resolver.init();
    }

    /**
     * 构造请求
     * @param agentCode
     * @param userId
     * @param scopeId
     * @return
     */
    private AgentRequest request(String agentCode, String userId, String scopeId) {
        return new AgentRequest().agentCode(agentCode).userId(userId).scopeId(scopeId);
    }

    /**
     * 打桩灰度命中
     */
    private void stubGrayHit() {
        AgentGrayRule rule = new AgentGrayRule();
        rule.setAgentCode("agent-a");
        rule.setTargetVersionId(2L);
        when(grayRouter.route(eq("agent-a"), anyString(), any())).thenReturn(GrayDecision.hit(rule));

        AgentVersion version = new AgentVersion();
        version.setId(2L);
        version.setAgentCode("agent-a");
        version.setVersionNo("v2");
        version.setStatus("PUBLISHED");
        version.setConfigJson(CONFIG_JSON);
        when(versionMapper.selectById(2L)).thenReturn(version);

        AgentDefinition definition = new AgentDefinition();
        definition.setAgentCode("agent-a");
        definition.setAgentName("选矿顾问");
        definition.setDescription("描述");
        definition.setCategory("CONSULT");
        when(definitionMapper.selectOne(any())).thenReturn(definition);
    }

    @Test
    @DisplayName("请求为null或agentCode为空时返回null")
    void nullRequestReturnsNull() {
        assertThat(resolver.resolve(null)).isNull();
        assertThat(resolver.resolve(request("", "u1", "s1"))).isNull();
        verifyNoInteractions(grayRouter);
    }

    @Test
    @DisplayName("灰度命中返回目标版本配置装配的Agent")
    void grayHitAssemblesAgent() {
        stubGrayHit();

        Agent agent = resolver.resolve(request("agent-a", "u1", "s1"));

        assertThat(agent).isNotNull();
        assertThat(agent.getAgentCode()).isEqualTo("agent-a");
        assertThat(agent.getAgentName()).isEqualTo("选矿顾问");
        assertThat(agent.getAgentConfig()).isEqualTo(CONFIG_JSON);
    }

    @Test
    @DisplayName("灰度未命中返回null走默认路径")
    void grayMissReturnsNull() {
        when(grayRouter.route(eq("agent-a"), eq("u1"), any())).thenReturn(GrayDecision.miss());

        assertThat(resolver.resolve(request("agent-a", "u1", "s1"))).isNull();
        verifyNoInteractions(versionMapper);
        verifyNoInteractions(definitionMapper);
    }

    @Test
    @DisplayName("目标版本非PUBLISHED视为未命中")
    void targetNotPublishedMiss() {
        AgentGrayRule rule = new AgentGrayRule();
        rule.setAgentCode("agent-a");
        rule.setTargetVersionId(2L);
        when(grayRouter.route(eq("agent-a"), eq("u1"), any())).thenReturn(GrayDecision.hit(rule));

        AgentVersion version = new AgentVersion();
        version.setId(2L);
        version.setStatus("DEPRECATED");
        when(versionMapper.selectById(2L)).thenReturn(version);

        assertThat(resolver.resolve(request("agent-a", "u1", "s1"))).isNull();
        verifyNoInteractions(definitionMapper);
    }

    @Test
    @DisplayName("缓存命中时不重复查灰度规则")
    void cacheHitAvoidsSecondLookup() {
        stubGrayHit();

        resolver.resolve(request("agent-a", "u1", "s1"));
        resolver.resolve(request("agent-a", "u1", "s1"));

        verify(grayRouter, times(1)).route(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("evict后缓存失效重新加载")
    void evictReloads() {
        stubGrayHit();

        resolver.resolve(request("agent-a", "u1", "s1"));
        resolver.evict("agent-a");
        resolver.resolve(request("agent-a", "u1", "s1"));

        verify(grayRouter, times(2)).route(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("不同用户缓存key不同各自加载")
    void differentUserSeparateCache() {
        stubGrayHit();

        resolver.resolve(request("agent-a", "u1", "s1"));
        resolver.resolve(request("agent-a", "u2", "s1"));

        verify(grayRouter, times(2)).route(eq("agent-a"), anyString(), anyString());
    }

    @Test
    @DisplayName("灰度路由异常时整体降级返回null")
    void routerExceptionDegrades() {
        when(grayRouter.route(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("DB挂了"));

        assertThat(resolver.resolve(request("agent-a", "u1", "s1"))).isNull();
    }
}
