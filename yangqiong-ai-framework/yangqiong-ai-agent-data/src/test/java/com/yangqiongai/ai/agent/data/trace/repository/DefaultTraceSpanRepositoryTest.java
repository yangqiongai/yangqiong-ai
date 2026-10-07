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
package com.yangqiongai.ai.agent.data.trace.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.yangqiongai.ai.agent.data.trace.mapper.TraceSpanMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent运行Span存储测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultTraceSpanRepositoryTest {

    @Mock
    private TraceSpanMapper traceSpanMapper;

    private DefaultTraceSpanRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        repository = new DefaultTraceSpanRepository();
        java.lang.reflect.Field field = DefaultTraceSpanRepository.class.getDeclaredField("traceSpanMapper");
        field.setAccessible(true);
        field.set(repository, traceSpanMapper);
    }

    private TraceSpanEntity span(Long id, String traceId, String spanId) {
        TraceSpanEntity entity = new TraceSpanEntity();
        entity.setId(id);
        entity.setTraceId(traceId);
        entity.setSpanId(spanId);
        entity.setOperation("agent_run");
        return entity;
    }

    @Test
    void 批量保存逐条插入() {
        when(traceSpanMapper.insert(any(TraceSpanEntity.class))).thenReturn(1);

        repository.batchSave(List.of(span(1L, "t1", "s1"), span(2L, "t1", "s2")));

        verify(traceSpanMapper, times(2)).insert(any(TraceSpanEntity.class));
    }

    @Test
    void 批量保存单条失败不中断其余() {
        when(traceSpanMapper.insert(any(TraceSpanEntity.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("dup"))
                .thenReturn(1);

        repository.batchSave(List.of(span(1L, "t1", "s1"), span(2L, "t1", "s2"), span(3L, "t1", "s3")));

        verify(traceSpanMapper, times(3)).insert(any(TraceSpanEntity.class));
    }

    @Test
    void 空列表批量保存不触发插入() {
        repository.batchSave(List.of());
        repository.batchSave(null);

        verify(traceSpanMapper, never()).insert(any(TraceSpanEntity.class));
    }

    @Test
    void 按追踪ID查询返回列表() {
        List<TraceSpanEntity> expected = List.of(span(1L, "t1", "s1"));
        when(traceSpanMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(expected);

        List<TraceSpanEntity> result = repository.findByTraceId("t1");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void 分页查询根Span附带LIMIT与OFFSET() {
        when(traceSpanMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        repository.findRootSpans("default", "task-1", "ERROR",
                LocalDateTime.now().minusDays(1), LocalDateTime.now(), 20, 10);

        verify(traceSpanMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void 统计根Span数量() {
        when(traceSpanMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        long count = repository.countRootSpans(null, null, null, null, null);

        assertThat(count).isEqualTo(5L);
    }

    @Test
    void 按追踪ID列表统计Span数解析分组结果() {
        when(traceSpanMapper.selectMaps(any(QueryWrapper.class))).thenReturn(List.of(
                Map.of("trace_id", "t1", "span_count", 12L),
                Map.of("trace_id", "t2", "span_count", 3L)));

        Map<String, Long> result = repository.countByTraceIds(List.of("t1", "t2"));

        assertThat(result).containsEntry("t1", 12L).containsEntry("t2", 3L);
    }

    @Test
    void 空追踪ID列表不触发分组查询() {
        Map<String, Long> result = repository.countByTraceIds(List.of());

        assertThat(result).isEmpty();
        verify(traceSpanMapper, never()).selectMaps(any(QueryWrapper.class));
    }

    @Test
    void 按追踪ID删除() {
        when(traceSpanMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(3);

        int deleted = repository.deleteByTraceId("t1");

        assertThat(deleted).isEqualTo(3);
    }

    @Test
    void 保留期清理走跨scope专用删除() {
        when(traceSpanMapper.deleteCreatedBefore(any(LocalDateTime.class), eq(5000))).thenReturn(2);

        int deleted = repository.deleteCreatedBefore(LocalDateTime.now().minusDays(30), 5000);

        assertThat(deleted).isEqualTo(2);
        verify(traceSpanMapper).deleteCreatedBefore(any(LocalDateTime.class), eq(5000));
    }

    @Test
    void 保留期清理无数据时返回0() {
        when(traceSpanMapper.deleteCreatedBefore(any(LocalDateTime.class), eq(5000))).thenReturn(0);

        int deleted = repository.deleteCreatedBefore(LocalDateTime.now().minusDays(30), 5000);

        assertThat(deleted).isZero();
    }

    @Test
    void 保留期清理删除上限最小为1() {
        when(traceSpanMapper.deleteCreatedBefore(any(LocalDateTime.class), eq(1))).thenReturn(0);

        repository.deleteCreatedBefore(LocalDateTime.now().minusDays(30), 0);

        verify(traceSpanMapper).deleteCreatedBefore(any(LocalDateTime.class), eq(1));
    }

    @Test
    void 批量保存中实体字段异常安全兜底() {
        when(traceSpanMapper.insert(any(TraceSpanEntity.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> repository.batchSave(List.of(span(1L, "t1", "s1"))))
                .doesNotThrowAnyException();
    }
}
