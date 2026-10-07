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
package com.yangqiongai.ai.platform.api.governance;

import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Trace保留期清理调度测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class TraceCleanupSchedulerTest {

    @Mock
    private TraceSpanRepository traceSpanRepository;

    private TraceCleanupScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        scheduler = new TraceCleanupScheduler();
        Field field = TraceCleanupScheduler.class.getDeclaredField("traceSpanRepository");
        field.setAccessible(true);
        field.set(scheduler, traceSpanRepository);
        Field retention = TraceCleanupScheduler.class.getDeclaredField("retentionDays");
        retention.setAccessible(true);
        retention.set(scheduler, 30);
    }

    @Test
    void 清理不足一批时单批完成() {
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), eq(5000))).thenReturn(100);

        scheduler.cleanupExpiredSpans();

        verify(traceSpanRepository, times(1)).deleteCreatedBefore(any(LocalDateTime.class), eq(5000));
    }

    @Test
    void 清理满批时分批循环直至不足一批() {
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), eq(5000))).thenReturn(5000, 3000);

        scheduler.cleanupExpiredSpans();

        verify(traceSpanRepository, times(2)).deleteCreatedBefore(any(LocalDateTime.class), eq(5000));
    }

    @Test
    void 清理阈值为保留期之前() {
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        when(traceSpanRepository.deleteCreatedBefore(captor.capture(), eq(5000))).thenReturn(0);

        scheduler.cleanupExpiredSpans();

        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(29));
    }

    @Test
    void 清理异常时不抛出() {
        when(traceSpanRepository.deleteCreatedBefore(any(LocalDateTime.class), eq(5000)))
                .thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> scheduler.cleanupExpiredSpans()).doesNotThrowAnyException();
    }
}
