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
package com.yangqiongai.ai.platform.api.governance.dashboard;

import com.yangqiongai.ai.agent.data.core.mapper.AgentMapper;
import com.yangqiongai.ai.agent.data.core.mapper.AgentTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 治理驾驶舱聚合测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class GovernanceDashboardServiceTest {

    @Mock
    private AgentMapper agentMapper;

    @Mock
    private AgentTaskMapper agentTaskMapper;

    private GovernanceDashboardService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new GovernanceDashboardService();
        inject("agentMapper", agentMapper);
        inject("agentTaskMapper", agentTaskMapper);
        inject("properties", new GovernanceDashboardProperties());
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = GovernanceDashboardService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private void stubEmptyBasics() {
        when(agentMapper.selectDashboardAgents()).thenReturn(List.of());
        when(agentTaskMapper.selectAgentRunAggSince(any())).thenReturn(List.of());
        when(agentTaskMapper.selectRunTrendSince(any())).thenReturn(List.of());
    }

    @Test
    void 空数据返回零信号与七位补零趋势() {
        stubEmptyBasics();

        GovernanceDashboardSummaryResponse response = service.summary();

        assertThat(response.getAgents()).isEmpty();
        assertThat(response.getSignals()).containsEntry("slaDegraded", 0L);
        assertThat(response.getSignalTrend7d()).isEmpty();
        assertThat(response.getTrend().getRuns7d()).hasSize(7).containsOnly(0L);
        assertThat(response.getRecent()).isEmpty();
        assertThat(response.getGeneratedAt()).isNotBlank();
    }

    @Test
    void SLA退化信号由运行失败率推导() {
        stubEmptyBasics();
        when(agentMapper.selectDashboardAgents()).thenReturn(List.of(
                row("agent_code", "agent-a", "agent_name", "AgentA", "status", 1),
                row("agent_code", "agent-b", "agent_name", "AgentB", "status", 1)));
        when(agentTaskMapper.selectAgentRunAggSince(any())).thenReturn(List.of(
                row("agent_code", "agent-a", "totalRuns", 10L, "failedRuns", 5L),
                row("agent_code", "agent-b", "totalRuns", 8L, "failedRuns", 0L)));

        GovernanceDashboardSummaryResponse response = service.summary();

        assertThat(response.getSignals()).containsEntry("slaDegraded", 1L);
        assertThat(response.getRecent()).extracting(GovernanceRecentItem::getType)
                .contains("slaDegraded");
        assertThat(response.getRecent()).extracting(GovernanceRecentItem::getAgentCode)
                .containsOnly("agent-a");
    }

    @Test
    void 企业贡献者追加信号趋势事件与徽标() throws Exception {
        stubEmptyBasics();
        when(agentMapper.selectDashboardAgents()).thenReturn(List.of(
                row("agent_code", "agent-a", "agent_name", "AgentA", "status", 1)));

        GovernanceSignalContributor contributor = org.mockito.Mockito.mock(GovernanceSignalContributor.class);
        doAnswer(invocation -> {
            Map<String, Long> signals = invocation.getArgument(0);
            signals.put("triggerFailures", 3L);
            return null;
        }).when(contributor).contributeSignals(any());
        doAnswer(invocation -> {
            Map<String, Map<String, Long>> agentFlags = invocation.getArgument(0);
            agentFlags.computeIfAbsent("agent-a", key -> new HashMap<>())
                    .put("triggerFailures", 1L);
            return null;
        }).when(contributor).contributeAgentFlags(any());
        inject("contributors", List.of(contributor));

        GovernanceDashboardSummaryResponse response = service.summary();

        verify(contributor).contributeSignals(any());
        verify(contributor).contributeSignalTrends(any());
        verify(contributor).contributeRecent(any());
        verify(contributor).contributeAgentFlags(any());
        assertThat(response.getSignals()).containsEntry("triggerFailures", 3L);
        assertThat(response.getAgents().get(0).getSignalFlags()).containsEntry("triggerFailures", 1L);
    }

    @Test
    void 社区端无贡献者时跳过企业信号拼装() {
        stubEmptyBasics();

        GovernanceDashboardSummaryResponse response = service.summary();

        assertThat(response.getSignals()).doesNotContainKey("triggerFailures");
        assertThat(response.getSignals()).doesNotContainKey("pendingProposals");
    }
}
