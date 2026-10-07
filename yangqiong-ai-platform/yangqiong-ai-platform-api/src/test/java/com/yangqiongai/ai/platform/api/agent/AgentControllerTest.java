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
package com.yangqiongai.ai.platform.api.agent;

import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.spi.AgentScopeFilter;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent管理接口测试（agentCode查重与数据范围过滤接入）
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    /**
     * Agent配置管理
     */
    @Mock
    private AgentManager agentManager;

    /**
     * 数据范围过滤
     */
    @Mock
    private AgentScopeFilter scopeFilter;

    private AgentController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new AgentController();
        inject("agentService", agentManager);
        inject("scopeFilter", scopeFilter);
    }

    /**
     * 反射注入依赖
     * @param fieldName
     * @param value
     */
    private void inject(String fieldName, Object value) throws Exception {
        Field field = AgentController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }

    /**
     * 构建Agent
     * @param agentCode
     * @param scopeId
     * @return
     */
    private Agent buildAgent(String agentCode, String scopeId) {
        Agent agent = new Agent();
        agent.setAgentCode(agentCode);
        agent.setScopeId(scopeId);
        return agent;
    }

    @Test
    void listAppliesScopeFilter() {
        List<Agent> raw = List.of(buildAgent("a", "tenant-1"));
        List<Agent> filtered = List.of(buildAgent("a", "tenant-1"));
        when(agentManager.list()).thenReturn(raw);
        when(scopeFilter.filterList(raw)).thenReturn(filtered);

        ApiResult<List<Agent>> result = controller.list();

        assertThat(result.getData()).isSameAs(filtered);
    }

    @Test
    void getByCodeReturnsNotFoundWhenScopeFilterRejects() {
        when(agentManager.getByCode("other")).thenReturn(buildAgent("other", "tenant-2"));
        when(scopeFilter.filterOne(any(Agent.class))).thenReturn(null);

        ApiResult<Agent> result = controller.getByCode("other");

        assertThat(result.getData()).isNull();
        assertThat(result.getCode()).isNotZero();
    }

    @Test
    void createRejectsDuplicatedAgentCode() {
        when(agentManager.getByCode("dup")).thenReturn(buildAgent("dup", "default"));

        ApiResult<Agent> result = controller.create(buildAgent("dup", null));

        assertThat(result.getCode()).isNotZero();
        verify(agentManager, never()).save(any(Agent.class));
    }

    @Test
    void createSavesWhenAgentCodeAbsent() {
        when(agentManager.getByCode("new")).thenReturn(null);

        ApiResult<Agent> result = controller.create(buildAgent("new", null));

        assertThat(result.getData()).isNotNull();
        verify(agentManager).save(any(Agent.class));
    }

    @Test
    void updateChecksEditableBeforeSaving() {
        Agent existing = buildAgent("own", "tenant-1");
        when(agentManager.getByCode("own")).thenReturn(existing);

        ApiResult<Agent> result = controller.update("own", buildAgent("own", null));

        assertThat(result.getData()).isNotNull();
        verify(scopeFilter).checkEditable(same(existing));
        verify(agentManager).updateById(any(Agent.class));
    }

    @Test
    void updatePropagatesForbiddenFromScopeFilter() {
        Agent existing = buildAgent("shared", "default");
        when(agentManager.getByCode("shared")).thenReturn(existing);
        org.mockito.Mockito.doThrow(new AiException(10004, "仅可编辑本租户Agent"))
                .when(scopeFilter).checkEditable(same(existing));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> controller.update("shared", buildAgent("shared", null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("仅可编辑本租户Agent");
        verify(agentManager, never()).updateById(any(Agent.class));
    }

    @Test
    void toggleStatusChecksEditableBeforeToggling() {
        Agent existing = buildAgent("own", "tenant-1");
        when(agentManager.getByCode("own")).thenReturn(existing);

        controller.toggleStatus("own");

        verify(scopeFilter).checkEditable(same(existing));
        verify(agentManager).toggleStatus("own");
    }

    @Test
    void sortChecksEditablePerItem() {
        Agent existing = buildAgent("own", "tenant-1");
        when(agentManager.getByCode("own")).thenReturn(existing);

        ApiResult<Void> result = controller.sort(List.of(java.util.Map.of("agentCode", "own", "sortOrder", 3)));

        assertThat(result.getCode()).isZero();
        verify(scopeFilter).checkEditable(same(existing));
        verify(agentManager).updateById(any(Agent.class));
    }

    @Test
    void sortSkipsUnknownAgentWithoutFilterCall() {
        when(agentManager.getByCode("ghost")).thenReturn(null);

        controller.sort(List.of(java.util.Map.of("agentCode", "ghost", "sortOrder", 1)));

        verify(scopeFilter, never()).checkEditable(any(Agent.class));
        verify(agentManager, never()).updateById(any(Agent.class));
    }
}
