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
package com.yangqiongai.ai.platform.api.scheduling;

import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

/**
 * Agent任务入队背压守卫测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BackpressureGuardTest {

    @Mock
    private AgentTaskRepository agentTaskRepository;

    private QueueProperties properties;

    private BackpressureGuard guard;

    @BeforeEach
    void setUp() throws Exception {
        properties = new QueueProperties();
        QueueProperties.Backpressure bp = properties.getBackpressure();
        bp.setMaxQueueDepthPerScope(10);
        bp.setMaxQueueDepthPerAgent(5);
        bp.setMaxConcurrentPerScope(4);
        bp.setMaxConcurrentPerAgent(2);
        bp.setStrategy("REJECT");

        guard = new BackpressureGuard();
        guard.setAgentTaskRepository(agentTaskRepository);
        guard.setProperties(properties);
    }

    @Test
    void 未超限时按原优先级放行() {
        when(agentTaskRepository.countByStatus(anyString(), anyString(), isNull())).thenReturn(0L);
        when(agentTaskRepository.countByStatus(anyString(), isNull(), anyString())).thenReturn(0L);

        BackpressureGuard.CheckResult result = guard.check("scope-1", "agent-a", 5);

        assertThat(result.priority()).isEqualTo(5);
    }

    @Test
    void REJECT策略队列深度超限时抛配额异常() {
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull())).thenReturn(10L);
        when(agentTaskRepository.countByStatus(eq("QUEUED"), isNull(), eq("agent-a"))).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("RUNNING"), anyString(), isNull())).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("RUNNING"), isNull(), anyString())).thenReturn(0L);

        assertThatThrownBy(() -> guard.check("scope-1", "agent-a", 5))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("背压拒绝");
    }

    @Test
    void REJECT策略Agent队列深度超限时抛配额异常() {
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull())).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("QUEUED"), isNull(), eq("agent-a"))).thenReturn(6L);

        assertThatThrownBy(() -> guard.check("scope-1", "agent-a", 5))
                .isInstanceOf(AiException.class);
    }

    @Test
    void scope与Agent并发限额叠加判定() {
        // scope维度RUNNING未超限，agent维度RUNNING超限 → 拒绝
        when(agentTaskRepository.countByStatus(eq("QUEUED"), anyString(), isNull())).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("QUEUED"), isNull(), anyString())).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("RUNNING"), eq("scope-1"), isNull())).thenReturn(1L);
        when(agentTaskRepository.countByStatus(eq("RUNNING"), isNull(), eq("agent-a"))).thenReturn(2L);

        assertThatThrownBy(() -> guard.check("scope-1", "agent-a", 5))
                .isInstanceOf(AiException.class);
    }

    @Test
    void DEGRADE策略降优先级入队() {
        properties.getBackpressure().setStrategy("DEGRADE");
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull())).thenReturn(11L);

        BackpressureGuard.CheckResult result = guard.check("scope-1", "agent-a", 9);

        assertThat(result.priority()).isEqualTo(2);
    }

    @Test
    void DEGRADE策略原优先级低于降级阈值时保持不变() {
        properties.getBackpressure().setStrategy("DEGRADE");
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull())).thenReturn(11L);

        BackpressureGuard.CheckResult result = guard.check("scope-1", "agent-a", 1);

        assertThat(result.priority()).isEqualTo(1);
    }

    @Test
    void WAIT策略等待容量释放后放行() {
        properties.getBackpressure().setStrategy("WAIT");
        properties.getBackpressure().setWaitTimeoutMs(1000);
        // 第一次超限，等待后释放
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull()))
                .thenReturn(11L, 0L);
        when(agentTaskRepository.countByStatus(eq("QUEUED"), isNull(), eq("agent-a"))).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("RUNNING"), anyString(), isNull())).thenReturn(0L);
        when(agentTaskRepository.countByStatus(eq("RUNNING"), isNull(), anyString())).thenReturn(0L);

        BackpressureGuard.CheckResult result = guard.check("scope-1", "agent-a", 5);

        assertThat(result.priority()).isEqualTo(5);
    }

    @Test
    void WAIT策略等待超时后转REJECT() {
        properties.getBackpressure().setStrategy("WAIT");
        properties.getBackpressure().setWaitTimeoutMs(300);
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull())).thenReturn(11L);

        assertThatThrownBy(() -> guard.check("scope-1", "agent-a", 5))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("等待超时");
    }

    @Test
    void 异常错误码为配额超限() {
        when(agentTaskRepository.countByStatus(eq("QUEUED"), eq("scope-1"), isNull())).thenReturn(11L);

        assertThatThrownBy(() -> guard.check("scope-1", "agent-a", 5))
                .isInstanceOfSatisfying(AiException.class, e ->
                        assertThat(e.getCode()).isEqualTo(AiErrorCode.AGENT_QUOTA_EXCEEDED.getCode()));
    }
}
