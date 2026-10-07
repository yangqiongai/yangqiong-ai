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
package com.yangqiongai.ai.agent.registry.materialize;

import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.repository.AgentRepository;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.data.registry.entity.AgentDefinition;
import com.yangqiongai.ai.agent.data.registry.entity.AgentVersion;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent配置物化器单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AgentConfigMaterializer 单元测试")
class AgentConfigMaterializerTest {

    private static final String CONFIG_WITH_TOOLS = """
            {"model":"deepseek-v3","systemPrompt":"你是选矿药剂技术顾问","tools":["reagent_query","calc_cost","apply_sample"]}
            """;

    @Mock
    private AgentManager agentManager;

    @Mock
    private AgentRepository agentRepository;

    private AgentConfigMaterializer materializer;

    private AgentDefinition definition;

    private AgentVersion version;

    @BeforeEach
    void setUp() {
        materializer = new AgentConfigMaterializer();
        org.springframework.test.util.ReflectionTestUtils.setField(materializer, "agentManager", agentManager);
        org.springframework.test.util.ReflectionTestUtils.setField(materializer, "agentRepository", agentRepository);
        definition = new AgentDefinition();
        definition.setAgentCode("agent-a");
        definition.setAgentName("选矿顾问");
        definition.setDescription("起泡剂技术顾问");
        definition.setCategory("CONSULT");
        version = new AgentVersion();
        version.setAgentCode("agent-a");
        version.setVersionNo("v1");
        version.setConfigJson(CONFIG_WITH_TOOLS);
    }

    @Test
    @DisplayName("运行时不存在时新增且status=1、sortOrder=99")
    void saveWhenNotExists() {
        when(agentManager.getByCode("agent-a")).thenReturn(null);

        materializer.materialize(definition, version);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentManager).save(captor.capture());
        Agent saved = captor.getValue();
        assertThat(saved.getAgentCode()).isEqualTo("agent-a");
        assertThat(saved.getAgentName()).isEqualTo("选矿顾问");
        assertThat(saved.getDescription()).isEqualTo("起泡剂技术顾问");
        assertThat(saved.getCategory()).isEqualTo("CONSULT");
        assertThat(saved.getAgentConfig()).isEqualTo(CONFIG_WITH_TOOLS);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getSortOrder()).isEqualTo(99);
        verify(agentManager, never()).updateById(any(Agent.class));
    }

    @Test
    @DisplayName("运行时已存在时经agentManager更新且仅覆盖治理字段")
    void updateWhenExists() {
        Agent existing = new Agent();
        existing.setId(9L);
        existing.setAgentCode("agent-a");
        existing.setAgentName("旧名称");
        existing.setSessionType("CHAT");
        existing.setIcon("old-icon");
        existing.setSortOrder(5);
        existing.setAgentConfig("old-config");
        when(agentManager.getByCode("agent-a")).thenReturn(existing);

        materializer.materialize(definition, version);

        ArgumentCaptor<Agent> captor = ArgumentCaptor.forClass(Agent.class);
        verify(agentManager).updateById(captor.capture());
        Agent updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(9L);
        assertThat(updated.getAgentConfig()).isEqualTo(CONFIG_WITH_TOOLS);
        assertThat(updated.getAgentName()).isEqualTo("选矿顾问");
        assertThat(updated.getDescription()).isEqualTo("起泡剂技术顾问");
        assertThat(updated.getCategory()).isEqualTo("CONSULT");
        // 非治理来源字段保持不变
        assertThat(updated.getSessionType()).isEqualTo("CHAT");
        assertThat(updated.getIcon()).isEqualTo("old-icon");
        assertThat(updated.getSortOrder()).isEqualTo(5);
        verify(agentManager, never()).save(any(Agent.class));
    }

    @Test
    @DisplayName("config_json非法时抛异常且不触碰运行时表")
    void invalidConfigJsonThrows() {
        version.setConfigJson("{bad-json");

        assertThatThrownBy(() -> materializer.materialize(definition, version))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("不是合法JSON");
        verify(agentManager, never()).getByCode(anyString());
    }

    @Test
    @DisplayName("tools非数组时抛异常")
    void toolsNonArrayThrows() {
        version.setConfigJson("{\"model\":\"m\",\"tools\":\"t1\"}");

        assertThatThrownBy(() -> materializer.materialize(definition, version))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("tools必须是字符串数组");
    }

    @Test
    @DisplayName("tools含非字符串元素时抛异常")
    void toolsNonStringItemThrows() {
        version.setConfigJson("{\"model\":\"m\",\"tools\":[1]}");

        assertThatThrownBy(() -> materializer.materialize(definition, version))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("tools必须是字符串数组");
    }

    @Test
    @DisplayName("disable将type_status置0")
    void disableSetsStatusZero() {
        materializer.disable("agent-a");
        verify(agentRepository).updateStatus(eq("agent-a"), eq(0));
    }

    @Test
    @DisplayName("enable将type_status置1")
    void enableSetsStatusOne() {
        materializer.enable("agent-a");
        verify(agentRepository).updateStatus(eq("agent-a"), eq(1));
    }
}
