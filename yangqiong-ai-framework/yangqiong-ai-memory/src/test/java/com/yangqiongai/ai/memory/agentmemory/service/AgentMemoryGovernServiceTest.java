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
package com.yangqiongai.ai.memory.agentmemory.service;

import com.yangqiongai.ai.memory.agentmemory.event.AgentMemoryQuarantinedEvent;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.vector.AgentMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent运行记忆治理单元测试
 * @author yangqiong
 */
@DisplayName("Agent运行记忆治理单元测试")
class AgentMemoryGovernServiceTest {

    @Mock
    private AgentMemoryEntryRepository entryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AgentMemoryVectorStore agentMemoryVectorStore;

    private AgentMemoryGovernService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new AgentMemoryGovernService();
        injectField("entryRepository", entryRepository);
        injectField("eventPublisher", eventPublisher);
        injectField("agentMemoryVectorStore", agentMemoryVectorStore);
        injectField("negativeFeedbackStep", 0.1);
        injectField("staleConfidenceThreshold", 0.2);
    }

    @Test
    @DisplayName("TTL扫描将过期条目转STALE并清理向量")
    void sweepExpiredTtl_marksStaleAndCleansVector() {
        AgentMemoryEntryInfo expiredOne = entry(1L, 0.8);
        AgentMemoryEntryInfo expiredTwo = entry(2L, 0.7);
        expiredOne.setTtlExpireTime(LocalDateTime.now().minusSeconds(60));
        expiredTwo.setTtlExpireTime(LocalDateTime.now().minusSeconds(120));
        when(entryRepository.findExpiredTtl(any(LocalDateTime.class), eq(200)))
                .thenReturn(List.of(expiredOne, expiredTwo));

        int swept = service.sweepExpiredTtl();

        assertThat(swept).isEqualTo(2);
        verify(entryRepository).updateStatus(1L, AgentMemoryEntryInfo.STATUS_STALE);
        verify(entryRepository).updateStatus(2L, AgentMemoryEntryInfo.STATUS_STALE);
        verify(agentMemoryVectorStore).deleteByMemoryIds(List.of(1L));
        verify(agentMemoryVectorStore).deleteByMemoryIds(List.of(2L));
    }

    @Test
    @DisplayName("TTL扫描无过期条目时不做任何更新")
    void sweepExpiredTtl_empty_noUpdate() {
        when(entryRepository.findExpiredTtl(any(LocalDateTime.class), eq(200))).thenReturn(List.of());

        int swept = service.sweepExpiredTtl();

        assertThat(swept).isZero();
        verify(entryRepository, never()).updateStatus(any(), anyString());
        verify(agentMemoryVectorStore, never()).deleteByMemoryIds(anyList());
    }

    @Test
    @DisplayName("负反馈降低置信度但未达归档阈值")
    void reportNegativeFeedback_reducesConfidence() {
        when(entryRepository.selectById(1L)).thenReturn(entry(1L, 0.9));

        AgentMemoryEntryInfo result = service.reportNegativeFeedback(1L);

        assertThat(result.getConfidence()).isEqualTo(0.8);
        assertThat(result.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_ACTIVE);
        verify(entryRepository).updateConfidence(1L, 0.8);
        verify(entryRepository, never()).updateStatus(any(), anyString());
    }

    @Test
    @DisplayName("负反馈低于阈值时归档为STALE")
    void reportNegativeFeedback_belowThreshold_marksStale() {
        when(entryRepository.selectById(1L)).thenReturn(entry(1L, 0.25));

        AgentMemoryEntryInfo result = service.reportNegativeFeedback(1L);

        assertThat(result.getConfidence()).isEqualTo(0.15);
        assertThat(result.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_STALE);
        verify(entryRepository).updateStatus(1L, AgentMemoryEntryInfo.STATUS_STALE);
        verify(agentMemoryVectorStore).deleteByMemoryIds(List.of(1L));
    }

    @Test
    @DisplayName("负反馈置信度下限为0")
    void reportNegativeFeedback_flooredAtZero() {
        when(entryRepository.selectById(1L)).thenReturn(entry(1L, 0.05));

        AgentMemoryEntryInfo result = service.reportNegativeFeedback(1L);

        assertThat(result.getConfidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("负反馈条目不存在时返回null")
    void reportNegativeFeedback_missing_returnsNull() {
        when(entryRepository.selectById(99L)).thenReturn(null);

        assertThat(service.reportNegativeFeedback(99L)).isNull();
    }

    @Test
    @DisplayName("隔离条目更新状态并发布隔离事件")
    void quarantine_updatesStatusAndPublishesEvent() {
        when(entryRepository.selectById(1L)).thenReturn(entry(1L, 0.9));

        AgentMemoryEntryInfo result = service.quarantine(1L, "治理处置");

        assertThat(result.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_QUARANTINED);
        ArgumentCaptor<AgentMemoryQuarantinedEvent> captor =
                ArgumentCaptor.forClass(AgentMemoryQuarantinedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEntryId()).isEqualTo(1L);
        assertThat(captor.getValue().getReason()).isEqualTo("治理处置");
    }

    @Test
    @DisplayName("隔离条目不存在时返回null")
    void quarantine_missing_returnsNull() {
        when(entryRepository.selectById(99L)).thenReturn(null);

        assertThat(service.quarantine(99L, "原因")).isNull();
    }

    @Test
    @DisplayName("解除隔离恢复生效状态")
    void release_restoresActive() {
        AgentMemoryEntryInfo quarantined = entry(1L, 0.9);
        quarantined.setStatus(AgentMemoryEntryInfo.STATUS_QUARANTINED);
        when(entryRepository.selectById(1L)).thenReturn(quarantined);

        AgentMemoryEntryInfo result = service.release(1L);

        assertThat(result.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_ACTIVE);
        verify(entryRepository).updateStatus(1L, AgentMemoryEntryInfo.STATUS_ACTIVE);
    }

    @Test
    @DisplayName("归档条目转STALE并清理向量")
    void archive_marksStaleAndCleansVector() {
        when(entryRepository.selectById(1L)).thenReturn(entry(1L, 0.9));

        AgentMemoryEntryInfo result = service.archive(1L);

        assertThat(result.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_STALE);
        verify(entryRepository).updateStatus(1L, AgentMemoryEntryInfo.STATUS_STALE);
        verify(agentMemoryVectorStore).deleteByMemoryIds(List.of(1L));
    }

    private AgentMemoryEntryInfo entry(Long id, double confidence) {
        AgentMemoryEntryInfo entry = new AgentMemoryEntryInfo();
        entry.setId(id);
        entry.setAgentCode("a1");
        entry.setUserAnchor("u1");
        entry.setMemoryType(AgentMemoryEntryInfo.TYPE_EPISODIC);
        entry.setContent("记忆内容");
        entry.setConfidence(confidence);
        entry.setStatus(AgentMemoryEntryInfo.STATUS_ACTIVE);
        return entry;
    }

    private void injectField(String name, Object value) {
        try {
            Field field = findField(service.getClass(), name);
            field.setAccessible(true);
            field.set(service, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("字段注入失败: " + name, e);
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
        }
        throw new NoSuchFieldException(name);
    }
}
