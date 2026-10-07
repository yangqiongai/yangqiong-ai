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

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.core.repository.AgentTaskStepRepository;
import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import com.yangqiongai.ai.common.bean.ApiResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent运行Trace查询接口测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentTraceControllerTest {

    @Mock
    private TraceSpanRepository traceSpanRepository;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private AgentTaskStepRepository agentTaskStepRepository;

    @Mock
    private AgentEngine agentEngine;

    private AgentTraceController controller;

    @BeforeEach
    void setUp() throws Exception {
        controller = new AgentTraceController();
        inject("traceSpanRepository", traceSpanRepository);
        inject("agentTaskRepository", agentTaskRepository);
        inject("agentTaskStepRepository", agentTaskStepRepository);
        inject("agentEngine", agentEngine);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = AgentTraceController.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(controller, value);
    }

    private TraceSpanEntity rootSpan(String traceId, String taskId) {
        TraceSpanEntity entity = new TraceSpanEntity();
        entity.setTraceId(traceId);
        entity.setSpanId("span-root");
        entity.setParentSpanId(null);
        entity.setTaskId(taskId);
        entity.setAgentCode("default");
        entity.setSessionId("session-1");
        entity.setOperation("agent_run");
        entity.setStatus("OK");
        entity.setDurationMs(1000L);
        entity.setStartTime(LocalDateTime.of(2026, 9, 5, 10, 0, 0));
        return entity;
    }

    @Test
    void 运行列表联查任务信息与Span数() {
        when(traceSpanRepository.findRootSpans(eq("default"), isNull(), isNull(),
                isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(rootSpan("t1", "task-1")));
        when(traceSpanRepository.countRootSpans(eq("default"), isNull(), isNull(),
                isNull(), isNull())).thenReturn(1L);
        when(traceSpanRepository.countByTraceIds(List.of("t1"))).thenReturn(Map.of("t1", 5L));
        AgentTaskInfo task = new AgentTaskInfo();
        task.setTaskId("task-1");
        task.setTaskStatus("SUCCEEDED");
        task.setUserInput("你好");
        task.setTotalTokens(100);
        when(agentTaskRepository.queryTask("task-1")).thenReturn(task);

        ApiResult<List<Map<String, Object>>> result =
                controller.runs("default", null, null, null, null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).hasSize(1);
        Map<String, Object> row = result.getData().get(0);
        assertThat(row.get("traceId")).isEqualTo("t1");
        assertThat(row.get("spanCount")).isEqualTo(5L);
        assertThat(row.get("taskStatus")).isEqualTo("SUCCEEDED");
        assertThat(row.get("userInput")).isEqualTo("你好");
        assertThat(row.get("totalTokens")).isEqualTo(100);
    }

    @Test
    void 运行列表无关联任务时省略任务字段() {
        when(traceSpanRepository.findRootSpans(isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(0), eq(20)))
                .thenReturn(List.of(rootSpan("t1", null)));
        when(traceSpanRepository.countRootSpans(isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(1L);
        when(traceSpanRepository.countByTraceIds(List.of("t1"))).thenReturn(Map.of());

        ApiResult<List<Map<String, Object>>> result =
                controller.runs(null, null, null, null, null, 1, 20);

        assertThat(result.isSuccess()).isTrue();
        Map<String, Object> row = result.getData().get(0);
        assertThat(row).doesNotContainKey("taskStatus");
        verify(agentTaskRepository, never()).queryTask(any());
    }

    @Test
    void 超大页码时页码修正为1() {
        when(traceSpanRepository.findRootSpans(isNull(), isNull(), isNull(),
                isNull(), isNull(), eq(0), eq(20))).thenReturn(List.of());
        when(traceSpanRepository.countRootSpans(isNull(), isNull(), isNull(),
                isNull(), isNull())).thenReturn(0L);

        ApiResult<List<Map<String, Object>>> result =
                controller.runs(null, null, null, null, null, 0, 20);

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void Span查询不存在时返回失败() {
        when(traceSpanRepository.findByTraceId("t-none")).thenReturn(List.of());

        ApiResult<List<TraceSpanNode>> result = controller.spans("t-none");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void Span树按父子关系组装() {
        TraceSpanEntity root = rootSpan("t1", "task-1");
        TraceSpanEntity reasoning = new TraceSpanEntity();
        reasoning.setTraceId("t1");
        reasoning.setSpanId("span-reasoning");
        reasoning.setParentSpanId("span-root");
        reasoning.setOperation("reasoning");
        reasoning.setStatus("OK");
        reasoning.setDurationMs(500L);
        reasoning.setStartTime(LocalDateTime.of(2026, 9, 5, 10, 0, 1));
        TraceSpanEntity acting = new TraceSpanEntity();
        acting.setTraceId("t1");
        acting.setSpanId("span-acting");
        acting.setParentSpanId("span-root");
        acting.setOperation("acting");
        acting.setStatus("ERROR");
        acting.setErrorMessage("工具超时");
        acting.setDurationMs(200L);
        acting.setStartTime(LocalDateTime.of(2026, 9, 5, 10, 0, 2));
        when(traceSpanRepository.findByTraceId("t1")).thenReturn(List.of(root, reasoning, acting));

        ApiResult<List<TraceSpanNode>> result = controller.spans("t1");

        assertThat(result.isSuccess()).isTrue();
        List<TraceSpanNode> tree = result.getData();
        assertThat(tree).hasSize(1);
        TraceSpanNode rootNode = tree.get(0);
        assertThat(rootNode.getOperation()).isEqualTo("agent_run");
        assertThat(rootNode.getChildren()).hasSize(2);
        assertThat(rootNode.getChildren().get(0).getOperation()).isEqualTo("reasoning");
        assertThat(rootNode.getChildren().get(1).getStatus()).isEqualTo("ERROR");
        assertThat(rootNode.getChildren().get(1).getErrorMessage()).isEqualTo("工具超时");
    }

    @Test
    void Span属性JSON解析为Map() {
        TraceSpanEntity root = rootSpan("t1", "task-1");
        root.setAttributes("{\"agentCode\":\"default\",\"taskId\":\"task-1\"}");
        when(traceSpanRepository.findByTraceId("t1")).thenReturn(List.of(root));

        ApiResult<List<TraceSpanNode>> result = controller.spans("t1");

        Object attributes = result.getData().get(0).getAttributes();
        assertThat(attributes).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) attributes).get("agentCode")).isEqualTo("default");
    }

    @Test
    void 非法属性JSON回退为原始字符串() {
        TraceSpanEntity root = rootSpan("t1", "task-1");
        root.setAttributes("not-json{");
        when(traceSpanRepository.findByTraceId("t1")).thenReturn(List.of(root));

        ApiResult<List<TraceSpanNode>> result = controller.spans("t1");

        assertThat(result.getData().get(0).getAttributes()).isEqualTo("not-json{");
    }

    @Test
    void 步骤时间线查询() {
        AgentTaskStepInfo step = new AgentTaskStepInfo();
        step.setTaskId("task-1");
        step.setStepOrder(1);
        step.setStepType("LLM_CALL");
        when(agentTaskStepRepository.querySteps("task-1")).thenReturn(List.of(step));

        ApiResult<?> result = controller.steps("task-1");

        assertThat(result.isSuccess()).isTrue();
        AgentTaskStepInfo returned = ((List<AgentTaskStepInfo>) result.getData()).get(0);
        assertThat(returned.getCallSeq()).isEqualTo(1);
    }

    @Test
    void 步骤时间线混排时LLM步骤按序次补callSeq() {
        AgentTaskStepInfo llm1 = new AgentTaskStepInfo();
        llm1.setStepOrder(1);
        llm1.setStepType("LLM_CALL");
        AgentTaskStepInfo tool = new AgentTaskStepInfo();
        tool.setStepOrder(2);
        tool.setStepType("TOOL_CALL");
        AgentTaskStepInfo llm2 = new AgentTaskStepInfo();
        llm2.setStepOrder(3);
        llm2.setStepType("LLM_CALL");
        when(agentTaskStepRepository.querySteps("task-1")).thenReturn(List.of(llm1, tool, llm2));

        ApiResult<?> result = controller.steps("task-1");

        List<AgentTaskStepInfo> returned = (List<AgentTaskStepInfo>) result.getData();
        assertThat(returned.get(0).getCallSeq()).isEqualTo(1);
        // 工具步骤不占模型调用序号
        assertThat(returned.get(1).getCallSeq()).isNull();
        assertThat(returned.get(2).getCallSeq()).isEqualTo(2);
    }

    @Test
    void 步骤时间线不存在时返回失败() {
        when(agentTaskStepRepository.querySteps("task-none")).thenReturn(List.of());

        ApiResult<?> result = controller.steps("task-none");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void 删除运行Trace() {
        when(traceSpanRepository.deleteByTraceId("t1")).thenReturn(5);

        ApiResult<Void> result = controller.deleteRuns("t1");

        assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void 删除不存在的Trace返回失败() {
        when(traceSpanRepository.deleteByTraceId("t-none")).thenReturn(0);

        ApiResult<Void> result = controller.deleteRuns("t-none");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    void 重放提交新任务并返回新任务ID() {
        AgentTaskInfo task = new AgentTaskInfo();
        task.setTaskId("task-1");
        task.setAgentCode("agent-a");
        task.setUserId("u1");
        task.setUserInput("原始输入");
        task.setBody("{\"taskId\":\"task-1\",\"modelCode\":\"model-a\",\"systemPrompt\":\"提示词\"}");
        when(agentTaskRepository.queryTask("task-1")).thenReturn(task);
        when(agentEngine.submitTask(any(AgentRequest.class))).thenReturn("task-new");

        ApiResult<Map<String, Object>> result = controller.replay("task-1");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().get("originalTaskId")).isEqualTo("task-1");
        assertThat(result.getData().get("newTaskId")).isEqualTo("task-new");
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        AgentRequest submitted = captor.getValue();
        assertThat(submitted.getAgentCode()).isEqualTo("agent-a");
        assertThat(submitted.getUserId()).isEqualTo("u1");
        assertThat(submitted.getInputAsText()).isEqualTo("原始输入");
        assertThat(submitted.getBody().get("modelCode")).isEqualTo("model-a");
        assertThat(submitted.getBody().get("systemPrompt")).isEqualTo("提示词");
        assertThat(submitted.getBody().get("taskId")).isNull();
        assertThat(submitted.getBody().get("_replayOf")).isEqualTo("task-1");
    }

    @Test
    void 重放任务不存在时返回失败() {
        when(agentTaskRepository.queryTask("task-none")).thenReturn(null);

        ApiResult<Map<String, Object>> result = controller.replay("task-none");

        assertThat(result.isSuccess()).isFalse();
        verify(agentEngine, never()).submitTask(any(AgentRequest.class));
    }

    @Test
    void 重放body非法JSON时按无body重放() {
        AgentTaskInfo task = new AgentTaskInfo();
        task.setTaskId("task-1");
        task.setAgentCode("agent-a");
        task.setUserInput("原始输入");
        task.setBody("not-json{");
        when(agentTaskRepository.queryTask("task-1")).thenReturn(task);
        when(agentEngine.submitTask(any(AgentRequest.class))).thenReturn("task-new");

        ApiResult<Map<String, Object>> result = controller.replay("task-1");

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        assertThat(captor.getValue().getBody().get("_replayOf")).isEqualTo("task-1");
    }
}
