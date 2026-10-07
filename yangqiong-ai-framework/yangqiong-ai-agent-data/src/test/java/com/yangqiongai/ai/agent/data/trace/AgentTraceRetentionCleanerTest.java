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
package com.yangqiongai.ai.agent.data.trace;

import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Span保留期清理任务测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentTraceRetentionCleanerTest {

    @Mock
    private TraceSpanRepository traceSpanRepository;

    private AgentTraceProperties properties;

    private AgentTraceRetentionCleaner cleaner;

    @BeforeEach
    void setUp() {
        properties = new AgentTraceProperties();
        cleaner = new AgentTraceRetentionCleaner(traceSpanRepository, properties);
    }

    @Test
    void 保留期非正数时跳过清理() {
        properties.setRetentionDays(0);

        assertThat(cleaner.cleanupOnce()).isZero();
        verify(traceSpanRepository, never()).deleteCreatedBefore(any(), anyInt());

        properties.setRetentionDays(-5);
        assertThat(cleaner.cleanupOnce()).isZero();
        verify(traceSpanRepository, never()).deleteCreatedBefore(any(), anyInt());
    }

    @Test
    void 删除阈值按保留天数回推() {
        properties.setRetentionDays(7);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt())).thenReturn(0);

        cleaner.cleanupOnce();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(traceSpanRepository).deleteCreatedBefore(captor.capture(), anyInt());
        assertThat(captor.getValue()).isCloseTo(LocalDateTime.now().minusDays(7), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 单批未满时一次删除即停() {
        properties.setRetentionDays(7);
        properties.setRetentionBatchSize(5000);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt())).thenReturn(100);

        assertThat(cleaner.cleanupOnce()).isEqualTo(100);
        verify(traceSpanRepository, times(1)).deleteCreatedBefore(any(LocalDateTime.class), anyInt());
    }

    @Test
    void 单批删满时继续下一批() {
        properties.setRetentionDays(7);
        properties.setRetentionBatchSize(5000);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt()))
                .thenReturn(5000, 5000, 300);

        assertThat(cleaner.cleanupOnce()).isEqualTo(10300);
        verify(traceSpanRepository, times(3)).deleteCreatedBefore(any(LocalDateTime.class), anyInt());
    }

    @Test
    void 达到单次轮数上限后停止() {
        properties.setRetentionDays(7);
        properties.setRetentionBatchSize(5000);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt())).thenReturn(5000);

        assertThat(cleaner.cleanupOnce()).isEqualTo(200 * 5000L);
        verify(traceSpanRepository, times(200)).deleteCreatedBefore(any(LocalDateTime.class), anyInt());
    }

    @Test
    void 单批上限最小为1() {
        properties.setRetentionDays(7);
        properties.setRetentionBatchSize(0);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt())).thenReturn(1, 0);

        assertThat(cleaner.cleanupOnce()).isEqualTo(1);
        verify(traceSpanRepository, times(2)).deleteCreatedBefore(any(LocalDateTime.class), eq(1));
    }

    @Test
    void 清理异常不中断调度() {
        properties.setRetentionDays(7);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt()))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> cleaner.cleanup()).doesNotThrowAnyException();
    }

    @Test
    void 策略为空或无覆盖时走全局清理路径() {
        properties.setRetentionDays(7);
        AgentTraceRetentionCleaner nullPolicyCleaner =
                new AgentTraceRetentionCleaner(traceSpanRepository, properties, null);
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), anyInt())).thenReturn(0);

        assertThat(nullPolicyCleaner.cleanupOnce()).isZero();
        verify(traceSpanRepository).deleteCreatedBefore(any(LocalDateTime.class), anyInt());
        verify(traceSpanRepository, never()).deleteCreatedBeforeExcludingScopes(any(), anyInt(), any());

        AgentTraceRetentionCleaner emptyPolicyCleaner = new AgentTraceRetentionCleaner(
                traceSpanRepository, properties, Map::of);
        assertThat(emptyPolicyCleaner.cleanupOnce()).isZero();
        verify(traceSpanRepository, times(2)).deleteCreatedBefore(any(LocalDateTime.class), anyInt());
    }

    @Test
    void scope覆盖按覆盖保留期单独清理且全局排除覆盖scope() {
        properties.setRetentionDays(30);
        // LinkedHashMap保证遍历顺序稳定
        Map<String, Integer> overrides = new java.util.LinkedHashMap<>();
        overrides.put("scope-a", 7);
        overrides.put("scope-b", 90);
        AgentTraceRetentionCleaner scopedCleaner =
                new AgentTraceRetentionCleaner(traceSpanRepository, properties, () -> overrides);
        when(traceSpanRepository.deleteCreatedBeforeByScope(anyString(), any(LocalDateTime.class), anyInt()))
                .thenReturn(0);
        when(traceSpanRepository.deleteCreatedBeforeExcludingScopes(
                any(LocalDateTime.class), anyInt(), any())).thenReturn(0);

        assertThat(scopedCleaner.cleanupOnce()).isZero();

        ArgumentCaptor<String> scopeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<LocalDateTime> scopeThresholdCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(traceSpanRepository, times(2)).deleteCreatedBeforeByScope(
                scopeCaptor.capture(), scopeThresholdCaptor.capture(), anyInt());
        assertThat(scopeCaptor.getAllValues()).containsExactly("scope-a", "scope-b");
        // 两个覆盖阈值分别按7天/90天回推
        assertThat(scopeThresholdCaptor.getAllValues().get(0))
                .isCloseTo(LocalDateTime.now().minusDays(7), within(1, ChronoUnit.MINUTES));
        assertThat(scopeThresholdCaptor.getAllValues().get(1))
                .isCloseTo(LocalDateTime.now().minusDays(90), within(1, ChronoUnit.MINUTES));

        ArgumentCaptor<java.util.Collection<String>> excludeCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(traceSpanRepository).deleteCreatedBeforeExcludingScopes(
                any(LocalDateTime.class), anyInt(), excludeCaptor.capture());
        assertThat(excludeCaptor.getValue()).containsExactlyInAnyOrder("scope-a", "scope-b");
        verify(traceSpanRepository, never()).deleteCreatedBefore(any(LocalDateTime.class), anyInt());
    }

    @Test
    void 非正数覆盖跳过scope清理但仍全局排除() {
        properties.setRetentionDays(30);
        Map<String, Integer> overrides = new java.util.HashMap<>();
        overrides.put("scope-keep", 0);
        AgentTraceRetentionCleaner scopedCleaner =
                new AgentTraceRetentionCleaner(traceSpanRepository, properties, () -> overrides);
        when(traceSpanRepository.deleteCreatedBeforeExcludingScopes(
                any(LocalDateTime.class), anyInt(), any())).thenReturn(0);

        assertThat(scopedCleaner.cleanupOnce()).isZero();

        verify(traceSpanRepository, never()).deleteCreatedBeforeByScope(anyString(), any(), anyInt());
        ArgumentCaptor<java.util.Collection<String>> excludeCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
        verify(traceSpanRepository).deleteCreatedBeforeExcludingScopes(
                any(LocalDateTime.class), anyInt(), excludeCaptor.capture());
        assertThat(excludeCaptor.getValue()).containsExactly("scope-keep");
    }

    @Test
    void scope覆盖与全局清理共享轮数预算() {
        properties.setRetentionDays(30);
        properties.setRetentionBatchSize(5000);
        Map<String, Integer> overrides = Map.of("scope-a", 7, "scope-b", 7);
        AgentTraceRetentionCleaner scopedCleaner =
                new AgentTraceRetentionCleaner(traceSpanRepository, properties, () -> overrides);
        when(traceSpanRepository.deleteCreatedBeforeByScope(anyString(), any(LocalDateTime.class), anyInt()))
                .thenReturn(5000);

        // 每个scope最多消耗剩余预算：200轮预算被两个覆盖scope各消费一部分后全局仍有余量，但总量受限
        assertThat(scopedCleaner.cleanupOnce()).isEqualTo(200 * 5000L);
        verify(traceSpanRepository, times(200)).deleteCreatedBeforeByScope(
                anyString(), any(LocalDateTime.class), anyInt());
        verify(traceSpanRepository, never()).deleteCreatedBeforeExcludingScopes(any(), anyInt(), any());
    }
}
