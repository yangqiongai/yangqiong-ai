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
package com.yangqiongai.ai.agent.core.task;

import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.budget.UsageListener;
import com.yangqiongai.ai.agent.runtime.budget.UsageRecord;
import com.yangqiongai.ai.agent.runtime.model.TokenMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 步骤记录管理器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class StepRecordingManagerTest {

    @Mock
    private TaskStore taskStore;

    @Mock
    private AgentTaskTracker agentTaskTracker;

    @Mock
    private ObjectProvider<UsageListener> usageListenerProvider;

    @Mock
    private UsageListener usageListener;

    private StepRecordingManager manager;

    @BeforeEach
    void setUp() throws Exception {
        manager = new StepRecordingManager();
        java.lang.reflect.Field storeField = StepRecordingManager.class.getDeclaredField("persistenceService");
        storeField.setAccessible(true);
        storeField.set(manager, taskStore);
        java.lang.reflect.Field trackerField = StepRecordingManager.class.getDeclaredField("agentTaskTracker");
        trackerField.setAccessible(true);
        trackerField.set(manager, agentTaskTracker);
        java.lang.reflect.Field enabledField = StepRecordingManager.class.getDeclaredField("stepRecordingEnabled");
        enabledField.setAccessible(true);
        enabledField.set(manager, true);
        java.lang.reflect.Field streamEnabledField = StepRecordingManager.class.getDeclaredField("streamEnabled");
        streamEnabledField.setAccessible(true);
        streamEnabledField.set(manager, true);
        java.lang.reflect.Field syncEnabledField = StepRecordingManager.class.getDeclaredField("syncEnabled");
        syncEnabledField.setAccessible(true);
        syncEnabledField.set(manager, true);
        java.lang.reflect.Field providerField = StepRecordingManager.class.getDeclaredField("usageListenerProvider");
        providerField.setAccessible(true);
        providerField.set(manager, usageListenerProvider);
        lenient().when(usageListenerProvider.getIfAvailable()).thenReturn(usageListener);
    }

    private AgentRequest streamRequest() {
        AgentRequest request = org.mockito.Mockito.mock(AgentRequest.class);
        Map<String, Object> body = new HashMap<>();
        body.put("_internalExecutionMode", "stream");
        lenient().when(request.getBody()).thenReturn(body);
        return request;
    }

    private TaskStepRecorder createStreamRecorder(AgentRequest request) {
        TaskStepRecorder recorder = manager.createRecorder(request);
        assertThat(recorder).isNotNull();
        return recorder;
    }

    @Test
    @DisplayName("流式路径无结果级Token统计时从LLM调用步骤聚合")
    void finishRecording_aggregatesTokensFromSteps() {
        AgentRequest request = streamRequest();
        TaskStepRecorder recorder = createStreamRecorder(request);
        recorder.recordLlmCall("agent", "test-model", "思考1", 100L, 100L, 50L, 150L);
        recorder.recordLlmCall("agent", "test-model", "思考2", 200L, 80L, 40L, 120L);

        String taskId = StepRecordingManager.getRecordingTaskId(request);
        manager.finishRecording(taskId, "SUCCEEDED", "", 300L, null);

        ArgumentCaptor<TokenMetrics> captor = ArgumentCaptor.forClass(TokenMetrics.class);
        verify(taskStore).updateTaskStatus(eq(taskId), eq("SUCCEEDED"), anyString(), anyLong(), captor.capture());
        TokenMetrics metrics = captor.getValue();
        assertThat(metrics.getInputTokens()).isEqualTo(180);
        assertThat(metrics.getOutputTokens()).isEqualTo(90);
        assertThat(metrics.getTotalTokens()).isEqualTo(270);
    }

    @Test
    @DisplayName("已有结果级Token统计时直接使用不聚合")
    void finishRecording_usesProvidedMetrics() {
        AgentRequest request = streamRequest();
        TaskStepRecorder recorder = createStreamRecorder(request);
        recorder.recordLlmCall("agent", "test-model", "思考", 100L, 100L, 50L, 150L);

        String taskId = StepRecordingManager.getRecordingTaskId(request);
        TokenMetrics provided = new TokenMetrics(1, 2, 3, 0);
        manager.finishRecording(taskId, "SUCCEEDED", "", 100L, provided);

        ArgumentCaptor<TokenMetrics> captor = ArgumentCaptor.forClass(TokenMetrics.class);
        verify(taskStore).updateTaskStatus(eq(taskId), eq("SUCCEEDED"), anyString(), anyLong(), captor.capture());
        assertThat(captor.getValue()).isSameAs(provided);
    }

    @Test
    @DisplayName("步骤token为null时聚合按0处理")
    void finishRecording_aggregatesNullTokensAsZero() {
        AgentRequest request = streamRequest();
        TaskStepRecorder recorder = createStreamRecorder(request);
        recorder.recordLlmCall("agent", "test-model", "无usage", 100L, null, null, null);

        String taskId = StepRecordingManager.getRecordingTaskId(request);
        manager.finishRecording(taskId, "SUCCEEDED", "", 100L, null);

        ArgumentCaptor<TokenMetrics> captor = ArgumentCaptor.forClass(TokenMetrics.class);
        verify(taskStore).updateTaskStatus(eq(taskId), eq("SUCCEEDED"), anyString(), anyLong(), captor.capture());
        TokenMetrics metrics = captor.getValue();
        assertThat(metrics.getInputTokens()).isZero();
        assertThat(metrics.getOutputTokens()).isZero();
        assertThat(metrics.getTotalTokens()).isZero();
    }

    @Test
    @DisplayName("非LLM调用步骤不参与token聚合")
    void finishRecording_ignoresNonLlmSteps() {
        AgentRequest request = streamRequest();
        TaskStepRecorder recorder = createStreamRecorder(request);
        recorder.recordLlmCall("agent", "test-model", "思考", 100L, 100L, 50L, 150L);
        recorder.recordToolCall("agent", null, null, 50L, "ALLOW");
        recorder.recordSubagentCall("agent", "child", "输入", 60L, "child-1");

        String taskId = StepRecordingManager.getRecordingTaskId(request);
        manager.finishRecording(taskId, "SUCCEEDED", "", 210L, null);

        ArgumentCaptor<TokenMetrics> captor = ArgumentCaptor.forClass(TokenMetrics.class);
        verify(taskStore).updateTaskStatus(eq(taskId), eq("SUCCEEDED"), anyString(), anyLong(), captor.capture());
        assertThat(captor.getValue().getTotalTokens()).isEqualTo(150);
    }

    @Test
    @DisplayName("同步/流式路径结束后上报run级用量")
    void finishRecording_publishesRunLevelUsage() {
        AgentRequest request = streamRequest();
        Map<String, Object> body = request.getBody();
        lenient().when(request.getAgentCode()).thenReturn("chat");
        lenient().when(request.getModelCode()).thenReturn("test-model");
        lenient().when(request.getScopeId()).thenReturn("t-1");
        lenient().when(request.getUserId()).thenReturn("u1");
        TaskStepRecorder recorder = createStreamRecorder(request);
        recorder.recordLlmCall("agent", "test-model", "思考", 100L, 100L, 50L, 150L);

        String taskId = StepRecordingManager.getRecordingTaskId(request);
        manager.finishRecording(taskId, "SUCCEEDED", "", 300L, null);

        ArgumentCaptor<UsageRecord> captor = ArgumentCaptor.forClass(UsageRecord.class);
        verify(usageListener).onUsage(captor.capture());
        UsageRecord record = captor.getValue();
        assertThat(record.getTaskId()).isEqualTo(taskId);
        assertThat(record.getAgentCode()).isEqualTo("chat");
        assertThat(record.getStatus()).isEqualTo("SUCCEEDED");
        assertThat(record.getTotalTokens()).isEqualTo(150);
        assertThat(record.getDurationMs()).isEqualTo(300L);
        assertThat(record.getScopeId()).isEqualTo("t-1");
        assertThat(record.getUserId()).isEqualTo("u1");
        assertThat(body.get(AgentRequest.BodyKeys.TASK_ID)).isEqualTo(taskId);
    }

    @Test
    @DisplayName("计量监听缺失时静默跳过run级用量上报")
    void finishRecording_skipsUsageWhenListenerMissing() throws Exception {
        java.lang.reflect.Field providerField = StepRecordingManager.class.getDeclaredField("usageListenerProvider");
        providerField.setAccessible(true);
        providerField.set(manager, null);
        AgentRequest request = streamRequest();
        TaskStepRecorder recorder = createStreamRecorder(request);

        String taskId = StepRecordingManager.getRecordingTaskId(request);
        manager.finishRecording(taskId, "SUCCEEDED", "", 100L, null);

        verify(taskStore).updateTaskStatus(eq(taskId), eq("SUCCEEDED"), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("body携带非异步TASK_ID时按同步/流式逻辑新建录制")
    void createRecorder_fallsThroughWhenTaskIdNotOwnedByTracker() {
        AgentRequest request = streamRequest();
        Map<String, Object> body = request.getBody();
        body.put(AgentRequest.BodyKeys.TASK_ID, "orphan-task-id");
        org.mockito.Mockito.when(agentTaskTracker.query("orphan-task-id")).thenReturn(null);

        TaskStepRecorder recorder = manager.createRecorder(request);

        assertThat(recorder).isNotNull();
        String recordingTaskId = (String) body.get("_recordingTaskId");
        assertThat(recordingTaskId).isNotNull().isNotEqualTo("orphan-task-id");
    }

    @Test
    @DisplayName("异步任务复用tracker持有的录制器")
    void createRecorder_reusesTrackerRecorderForAsyncTask() {
        AgentRequest request = streamRequest();
        Map<String, Object> body = request.getBody();
        body.put(AgentRequest.BodyKeys.TASK_ID, "async-task-id");
        TaskStepRecorder trackerRecorder = new TaskStepRecorder("async-task-id");
        AgentTaskRecord record = mock(AgentTaskRecord.class);
        org.mockito.Mockito.when(agentTaskTracker.query("async-task-id")).thenReturn(record);
        org.mockito.Mockito.when(record.getStepRecorder()).thenReturn(trackerRecorder);

        TaskStepRecorder recorder = manager.createRecorder(request);

        assertThat(recorder).isSameAs(trackerRecorder);
    }
}
