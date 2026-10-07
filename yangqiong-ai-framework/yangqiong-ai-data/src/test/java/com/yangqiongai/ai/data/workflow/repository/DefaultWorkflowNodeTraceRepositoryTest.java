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
package com.yangqiongai.ai.data.workflow.repository;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.yangqiongai.ai.data.workflow.entity.WorkflowNodeExecutionEntity;
import com.yangqiongai.ai.data.workflow.mapper.WorkflowNodeExecutionMapper;
import com.yangqiongai.ai.workflow.model.WorkflowNodeTrace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 节点执行轨迹仓储单元测试
 *
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultWorkflowNodeTraceRepositoryTest {

    @Mock
    private WorkflowNodeExecutionMapper mapper;

    private DefaultWorkflowNodeTraceRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DefaultWorkflowNodeTraceRepository(mapper);
    }

    private WorkflowNodeTrace buildTrace() {
        WorkflowNodeTrace trace = new WorkflowNodeTrace();
        trace.setInstanceId("inst-1");
        trace.setNodeId("agent1");
        trace.setNodeName("代理节点");
        trace.setNodeType("AGENT");
        trace.setExecutionOrder(2);
        trace.setStatus("COMPLETED");
        trace.setInputData(Map.of("question", "你好"));
        trace.setOutputData(Map.of("output", "回答"));
        trace.setRetryCount(0);
        trace.setIterationCount(0);
        trace.setStartTime(1000L);
        trace.setEndTime(2000L);
        trace.setDurationMs(1000L);
        trace.setScopeId("scope-1");
        return trace;
    }

    @Test
    @DisplayName("首次记录走insert且字段映射正确")
    void record_firstTime_inserts() {
        when(mapper.selectOne(any())).thenReturn(null);

        repository.record(buildTrace());

        ArgumentCaptor<WorkflowNodeExecutionEntity> captor = ArgumentCaptor.forClass(WorkflowNodeExecutionEntity.class);
        verify(mapper).insert(captor.capture());
        verify(mapper, never()).updateById(any(WorkflowNodeExecutionEntity.class));
        WorkflowNodeExecutionEntity entity = captor.getValue();
        assertThat(entity.getInstanceId()).isEqualTo("inst-1");
        assertThat(entity.getNodeId()).isEqualTo("agent1");
        assertThat(entity.getNodeName()).isEqualTo("代理节点");
        assertThat(entity.getNodeType()).isEqualTo("AGENT");
        assertThat(entity.getExecutionOrder()).isEqualTo(2);
        assertThat(entity.getStatus()).isEqualTo("COMPLETED");
        assertThat(entity.getInputData()).contains("question");
        assertThat(entity.getOutputData()).contains("output");
        assertThat(entity.getRetryCount()).isEqualTo(0);
        assertThat(entity.getDurationMs()).isEqualTo(1000L);
        assertThat(entity.getScopeId()).isEqualTo("scope-1");
        assertThat(entity.getStartTime()).isNotNull();
        assertThat(entity.getEndTime()).isNotNull();
    }

    @Test
    @DisplayName("同实例同节点重复记录走update覆盖写")
    void record_existing_overwrites() {
        WorkflowNodeExecutionEntity existing = new WorkflowNodeExecutionEntity();
        existing.setId(99L);
        existing.setInstanceId("inst-1");
        existing.setNodeId("agent1");
        when(mapper.selectOne(any())).thenReturn(existing);

        WorkflowNodeTrace retryTrace = buildTrace();
        retryTrace.setStatus("FAILED");
        retryTrace.setRetryCount(1);
        retryTrace.setErrorMessage("执行失败");
        repository.record(retryTrace);

        ArgumentCaptor<WorkflowNodeExecutionEntity> captor = ArgumentCaptor.forClass(WorkflowNodeExecutionEntity.class);
        verify(mapper).updateById(captor.capture());
        verify(mapper, never()).insert(any(WorkflowNodeExecutionEntity.class));
        assertThat(captor.getValue().getId()).isEqualTo(99L);
        assertThat(captor.getValue().getStatus()).isEqualTo("FAILED");
        assertThat(captor.getValue().getRetryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("超4KB的输入输出被截断并带截断标记")
    void record_oversizedJson_truncatedWithMarker() {
        when(mapper.selectOne(any())).thenReturn(null);
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            big.append("数据");
        }
        Map<String, Object> output = new HashMap<>();
        output.put("text", big.toString());

        WorkflowNodeTrace trace = buildTrace();
        trace.setOutputData(output);
        repository.record(trace);

        ArgumentCaptor<WorkflowNodeExecutionEntity> captor = ArgumentCaptor.forClass(WorkflowNodeExecutionEntity.class);
        verify(mapper).insert(captor.capture());
        String stored = captor.getValue().getOutputData();
        assertThat(stored).startsWith("{\"__truncated\":true");
        assertThat(stored.length()).isLessThan(5000);
    }

    @Test
    @DisplayName("按实例查询时解析JSON并按顺序排序")
    void listByInstance_parsesJsonFields() {
        WorkflowNodeExecutionEntity entity = new WorkflowNodeExecutionEntity();
        entity.setInstanceId("inst-1");
        entity.setNodeId("agent1");
        entity.setNodeType("AGENT");
        entity.setStatus("COMPLETED");
        entity.setInputData("{\"question\":\"你好\"}");
        entity.setOutputData("{\"output\":\"回答\"}");
        entity.setExecutionOrder(1);
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));

        List<WorkflowNodeTrace> traces = repository.listByInstance("inst-1");

        assertThat(traces).hasSize(1);
        WorkflowNodeTrace trace = traces.get(0);
        assertThat(trace.getNodeId()).isEqualTo("agent1");
        assertThat(trace.getInputData()).containsEntry("question", "你好");
        assertThat(trace.getOutputData()).containsEntry("output", "回答");
    }

    @Test
    @DisplayName("非法JSON解析失败时降级为raw文本")
    void listByInstance_invalidJson_fallbackToRaw() {
        WorkflowNodeExecutionEntity entity = new WorkflowNodeExecutionEntity();
        entity.setInstanceId("inst-1");
        entity.setNodeId("agent1");
        entity.setStatus("COMPLETED");
        entity.setInputData("not-a-json");
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of(entity));

        List<WorkflowNodeTrace> traces = repository.listByInstance("inst-1");

        assertThat(traces.get(0).getInputData()).containsEntry("raw", "not-a-json");
    }

    @Test
    @DisplayName("错误信息与节点名称超长时截断到列宽")
    void record_overlongText_truncatedToColumnLimit() {
        when(mapper.selectOne(any())).thenReturn(null);
        char[] bigError = new char[2000];
        java.util.Arrays.fill(bigError, '错');
        char[] bigName = new char[300];
        java.util.Arrays.fill(bigName, '名');

        WorkflowNodeTrace trace = buildTrace();
        trace.setErrorMessage(new String(bigError));
        trace.setNodeName(new String(bigName));
        repository.record(trace);

        ArgumentCaptor<WorkflowNodeExecutionEntity> captor = ArgumentCaptor.forClass(WorkflowNodeExecutionEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getErrorMessage()).hasSize(1024);
        assertThat(captor.getValue().getNodeName()).hasSize(128);
    }
}
