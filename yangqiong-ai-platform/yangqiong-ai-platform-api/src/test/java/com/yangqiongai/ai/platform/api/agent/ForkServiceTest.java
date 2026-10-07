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
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;
import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 轨迹分叉调试测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class ForkServiceTest {

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private ContextSnapshotRepository contextSnapshotRepository;

    @Mock
    private AgentEngine agentEngine;

    private ForkService forkService;

    @BeforeEach
    void setUp() throws Exception {
        forkService = new ForkService();
        inject("agentTaskRepository", agentTaskRepository);
        inject("contextSnapshotRepository", contextSnapshotRepository);
        inject("agentEngine", agentEngine);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = ForkService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(forkService, value);
    }

    /**
     * 构造来源任务
     * @return
     */
    private AgentTaskInfo sourceTask() {
        AgentTaskInfo task = new AgentTaskInfo();
        task.setTaskId("task-1");
        task.setAgentCode("code-assist");
        task.setUserId("user-1");
        task.setSessionId("session-1");
        task.setUserInput("原始输入");
        return task;
    }

    /**
     * 构造快照
     * @param callSeq
     * @return
     */
    private ContextSnapshotEntity snapshot(int callSeq) {
        ContextSnapshotEntity entity = new ContextSnapshotEntity();
        entity.setTaskId("task-1");
        entity.setCallSeq(callSeq);
        entity.setSnapshotJson("[{\"role\":\"USER\",\"content\":\"你好\",\"source\":\"history\",\"truncated\":false}]");
        return entity;
    }

    @Test
    void 任务不存在时报错且不提交() {
        when(agentTaskRepository.queryTask("task-x")).thenReturn(null);

        assertThatThrownBy(() -> forkService.fork("task-x", new ForkRequest()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("任务不存在");
        verify(agentEngine, never()).submitTask(any());
    }

    @Test
    void 指定序号快照不存在时报错() {
        when(agentTaskRepository.queryTask("task-1")).thenReturn(sourceTask());
        when(contextSnapshotRepository.findByTaskIdAndCallSeq("task-1", 99)).thenReturn(null);

        ForkRequest request = new ForkRequest();
        request.setCallSeq(99);
        assertThatThrownBy(() -> forkService.fork("task-1", request))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("上下文快照不存在");
        verify(agentEngine, never()).submitTask(any());
    }

    @Test
    void 未指定序号时取最后一次调用() {
        when(agentTaskRepository.queryTask("task-1")).thenReturn(sourceTask());
        when(contextSnapshotRepository.findByTaskId("task-1"))
                .thenReturn(List.of(snapshot(1), snapshot(2), snapshot(5)));
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        Map<String, Object> result = forkService.fork("task-1", new ForkRequest());

        assertThat(result.get("forkCallSeq")).isEqualTo(5);
        assertThat(result.get("newTaskId")).isEqualTo("task-new");
        assertThat(result.get("originalTaskId")).isEqualTo("task-1");
    }

    @Test
    void 快照存在时构造种子上下文调用() {
        when(agentTaskRepository.queryTask("task-1")).thenReturn(sourceTask());
        when(contextSnapshotRepository.findByTaskId("task-1")).thenReturn(List.of(snapshot(2)));
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        forkService.fork("task-1", new ForkRequest());

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        AgentRequest submitted = captor.getValue();
        // 引擎收到种子消息，role统一小写映射
        List<Map<String, Object>> seeds = submitted.getSeedMessages();
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0)).containsEntry("role", "user").containsEntry("content", "你好");
        // 种子模式下不再重复注入input
        assertThat(submitted.getInputAsText()).isBlank();
        assertThat(submitted.getBody().get(AgentRequest.BodyKeys.PARENT_TASK_ID)).isEqualTo("task-1");
        assertThat(submitted.getBody().get(AgentRequest.BodyKeys.FORK_CALL_SEQ)).isEqualTo(2);
    }

    @Test
    void 覆盖输入追加到种子末尾且不重复注入input() {
        when(agentTaskRepository.queryTask("task-1")).thenReturn(sourceTask());
        when(contextSnapshotRepository.findByTaskIdAndCallSeq("task-1", 2)).thenReturn(snapshot(2));
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        ForkRequest request = new ForkRequest();
        request.setCallSeq(2);
        request.setUserInputOverride("覆盖后的调试输入");
        request.setRemark("复现工具幻觉");
        Map<String, Object> result = forkService.fork("task-1", request);

        assertThat(result.get("newTaskId")).isEqualTo("task-new");
        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        AgentRequest submitted = captor.getValue();
        List<Map<String, Object>> seeds = submitted.getSeedMessages();
        assertThat(seeds).hasSize(2);
        assertThat(seeds.get(1)).containsEntry("role", "user").containsEntry("content", "覆盖后的调试输入");
        assertThat(submitted.getInputAsText()).isBlank();
        assertThat(submitted.getBody().get("_forkOf")).isEqualTo("复现工具幻觉");
    }

    @Test
    void systemPrompt来源条目被过滤() {
        AgentTaskInfo task = sourceTask();
        when(agentTaskRepository.queryTask("task-1")).thenReturn(task);
        ContextSnapshotEntity entity = new ContextSnapshotEntity();
        entity.setTaskId("task-1");
        entity.setCallSeq(1);
        entity.setSnapshotJson("[{\"role\":\"SYSTEM\",\"content\":\"系统提示\",\"source\":\"system_prompt\"},"
                + "{\"role\":\"user\",\"content\":\"你好\",\"source\":\"history\"}]");
        when(contextSnapshotRepository.findByTaskIdAndCallSeq("task-1", 1)).thenReturn(entity);
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        ForkRequest request = new ForkRequest();
        request.setCallSeq(1);
        forkService.fork("task-1", request);

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        List<Map<String, Object>> seeds = captor.getValue().getSeedMessages();
        assertThat(seeds).hasSize(1);
        assertThat(seeds.get(0)).containsEntry("content", "你好");
    }

    @Test
    void 快照损坏时降级复现重跑() {
        when(agentTaskRepository.queryTask("task-1")).thenReturn(sourceTask());
        ContextSnapshotEntity broken = new ContextSnapshotEntity();
        broken.setTaskId("task-1");
        broken.setCallSeq(2);
        broken.setSnapshotJson("这不是合法JSON{{{");
        when(contextSnapshotRepository.findByTaskIdAndCallSeq("task-1", 2)).thenReturn(broken);
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        ForkRequest request = new ForkRequest();
        request.setCallSeq(2);
        forkService.fork("task-1", request);

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        AgentRequest submitted = captor.getValue();
        // 降级模式：无种子，按原任务输入复现重跑
        assertThat(submitted.getSeedMessages()).isEmpty();
        assertThat(submitted.getBody()).doesNotContainKey(AgentRequest.BodyKeys.SEED_MESSAGES);
        assertThat(submitted.getInputAsText()).isEqualTo("原始输入");
    }

    @Test
    void 快照消息为空时降级复现重跑() {
        when(agentTaskRepository.queryTask("task-1")).thenReturn(sourceTask());
        ContextSnapshotEntity empty = new ContextSnapshotEntity();
        empty.setTaskId("task-1");
        empty.setCallSeq(1);
        empty.setSnapshotJson("  ");
        when(contextSnapshotRepository.findByTaskIdAndCallSeq("task-1", 1)).thenReturn(empty);
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        ForkRequest request = new ForkRequest();
        request.setCallSeq(1);
        forkService.fork("task-1", request);

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        AgentRequest submitted = captor.getValue();
        assertThat(submitted.getSeedMessages()).isEmpty();
        assertThat(submitted.getInputAsText()).isEqualTo("原始输入");
    }

    @Test
    void 恢复原body时剔除任务追踪字段() {
        AgentTaskInfo task = sourceTask();
        task.setBody("{\"taskId\":\"task-1\",\"skillCodes\":[\"sql\"],\"parentTaskId\":\"task-old\"}");
        when(agentTaskRepository.queryTask("task-1")).thenReturn(task);
        when(contextSnapshotRepository.findByTaskIdAndCallSeq("task-1", 1)).thenReturn(snapshot(1));
        when(agentEngine.submitTask(any())).thenReturn("task-new");

        ForkRequest request = new ForkRequest();
        request.setCallSeq(1);
        forkService.fork("task-1", request);

        ArgumentCaptor<AgentRequest> captor = ArgumentCaptor.forClass(AgentRequest.class);
        verify(agentEngine).submitTask(captor.capture());
        Map<String, Object> body = captor.getValue().getBody();
        assertThat(body).doesNotContainKey("taskId");
        assertThat(body.get("skillCodes")).isEqualTo(List.of("sql"));
        // 溯源标记恢复body后被分叉服务覆盖为新溯源值
        assertThat(body.get(AgentRequest.BodyKeys.PARENT_TASK_ID)).isEqualTo("task-1");
        assertThat(body.get(AgentRequest.BodyKeys.FORK_CALL_SEQ)).isEqualTo(1);
    }
}
