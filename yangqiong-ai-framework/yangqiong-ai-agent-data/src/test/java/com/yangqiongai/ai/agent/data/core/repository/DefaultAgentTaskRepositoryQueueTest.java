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
package com.yangqiongai.ai.agent.data.core.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.agent.data.core.entity.AgentTaskEntity;
import com.yangqiongai.ai.agent.data.core.mapper.AgentTaskMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent任务仓库队列调度测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultAgentTaskRepositoryQueueTest {

    @Mock
    private AgentTaskMapper agentTaskMapper;

    private DefaultAgentTaskRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentTaskEntity.class);
        repository = new DefaultAgentTaskRepository();
        Field mapperField = DefaultAgentTaskRepository.class.getDeclaredField("agentTaskMapper");
        mapperField.setAccessible(true);
        mapperField.set(repository, agentTaskMapper);
        Field enabledField = DefaultAgentTaskRepository.class.getDeclaredField("persistenceEnabled");
        enabledField.setAccessible(true);
        enabledField.set(repository, true);
    }

    private AgentTaskEntity queuedTask(String taskId, int priority, LocalDateTime queuedTime) {
        AgentTaskEntity entity = new AgentTaskEntity();
        entity.setId(Long.parseLong("1" + Math.abs(taskId.hashCode()) % 100000000));
        entity.setTaskId(taskId);
        entity.setTaskStatus("QUEUED");
        entity.setPriority(priority);
        entity.setQueuedTime(queuedTime);
        entity.setRedeliverCount(0);
        entity.setBody("{\"agentCode\":\"agent-a\"}");
        return entity;
    }

    @Test
    void 抢占队列任务按优先级降序与入队时间升序返回() {
        LocalDateTime now = LocalDateTime.now();
        // 仓库查询本身按priority DESC, queuedTime ASC排序，这里模拟DB返回顺序
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                queuedTask("task-high", 9, now),
                queuedTask("task-low", 3, now)));
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        List<com.yangqiongai.ai.agent.core.model.AgentTaskInfo> claimed = repository.claimQueued("runner-1", 5);

        assertThat(claimed).hasSize(2);
        assertThat(claimed.get(0).getTaskId()).isEqualTo("task-high");
        assertThat(claimed.get(1).getTaskId()).isEqualTo("task-low");
        assertThat(claimed.get(0).getRunnerId()).isEqualTo("runner-1");
        assertThat(claimed.get(0).getTaskStatus()).isEqualTo("RUNNING");
        verify(agentTaskMapper, times(2)).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    void CAS竞争失败的任务不进入抢占结果() {
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(
                queuedTask("task-win", 9, LocalDateTime.now()),
                queuedTask("task-lose", 5, LocalDateTime.now())));
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1, 0);

        List<com.yangqiongai.ai.agent.core.model.AgentTaskInfo> claimed = repository.claimQueued("runner-1", 5);

        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).getTaskId()).isEqualTo("task-win");
    }

    @Test
    void 回收心跳超时任务未耗尽重派次数时置回QUEUED() {
        AgentTaskEntity expired = queuedTask("task-expired", 5, LocalDateTime.now());
        expired.setTaskStatus("RUNNING");
        expired.setRunnerId("runner-dead");
        expired.setRunnerHeartbeat(LocalDateTime.now().minusSeconds(120));
        expired.setRedeliverCount(0);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(expired));
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int count = repository.reclaimExpired(LocalDateTime.now().minusSeconds(90), 3, 10);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(agentTaskMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet).contains("task_status");
        assertThat(sqlSet).contains("redeliver_count = redeliver_count + 1");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue("QUEUED");
    }

    @Test
    void 回收心跳超时任务重派耗尽时置FAILED() {
        AgentTaskEntity expired = queuedTask("task-exhausted", 5, LocalDateTime.now());
        expired.setTaskStatus("RUNNING");
        expired.setRunnerId("runner-dead");
        expired.setRunnerHeartbeat(LocalDateTime.now().minusSeconds(120));
        expired.setRedeliverCount(3);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(expired));
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int count = repository.reclaimExpired(LocalDateTime.now().minusSeconds(90), 3, 10);

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(agentTaskMapper).update(isNull(), captor.capture());
        String sqlSet = captor.getValue().getSqlSet();
        assertThat(sqlSet).contains("error_message");
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue("FAILED");
    }

    @Test
    void 心跳刷新按实例标识更新RUNNING任务() {
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(2);

        repository.heartbeat("runner-1");

        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(agentTaskMapper).update(isNull(), captor.capture());
        assertThat(captor.getValue().getSqlSet()).contains("runner_heartbeat");
    }

    @Test
    void 按状态统计任务数区分作用域与Agent维度() {
        when(agentTaskMapper.selectCount(any())).thenReturn(7L);

        long agentScoped = repository.countByStatus("QUEUED", null, "agent-a");
        long global = repository.countByStatus("QUEUED", null, null);

        assertThat(agentScoped).isEqualTo(7L);
        assertThat(global).isEqualTo(7L);
        verify(agentTaskMapper, times(2)).selectCount(any());
    }

    @Test
    void 启动恢复跳过带心跳的RUNNING任务避免误杀其他实例() {
        AgentTaskEntity runningWithHeartbeat = new AgentTaskEntity();
        runningWithHeartbeat.setTaskId("task-alive");
        runningWithHeartbeat.setTaskStatus("RUNNING");
        runningWithHeartbeat.setRunnerId("runner-other");
        runningWithHeartbeat.setRunnerHeartbeat(LocalDateTime.now());

        AgentTaskEntity legacyRunning = new AgentTaskEntity();
        legacyRunning.setTaskId("task-legacy");
        legacyRunning.setTaskStatus("RUNNING");

        when(agentTaskMapper.selectList(any()))
                .thenReturn(List.of(runningWithHeartbeat, legacyRunning));
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        int count = repository.recoverPendingTasks();

        assertThat(count).isEqualTo(1);
        ArgumentCaptor<LambdaUpdateWrapper> captor = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(agentTaskMapper).update(isNull(), captor.capture());
        // 仅无心跳的存量RUNNING任务被置FAILED
        assertThat(captor.getValue().getParamNameValuePairs()).containsValue("FAILED");
    }

    @Test
    void 回收时CAS竞争失败不计入回收数() {
        AgentTaskEntity expired = queuedTask("task-raced", 5, LocalDateTime.now());
        expired.setTaskStatus("RUNNING");
        expired.setRunnerId("runner-dead");
        expired.setRunnerHeartbeat(LocalDateTime.now().minusSeconds(120));
        expired.setRedeliverCount(1);
        when(agentTaskMapper.selectList(any())).thenReturn(List.of(expired));
        when(agentTaskMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        int count = repository.reclaimExpired(LocalDateTime.now().minusSeconds(90), 3, 10);

        assertThat(count).isEqualTo(0);
    }

    @Test
    void 持久化关闭时队列方法直接返回空结果() throws Exception {
        Field enabledField = DefaultAgentTaskRepository.class.getDeclaredField("persistenceEnabled");
        enabledField.setAccessible(true);
        enabledField.set(repository, false);

        assertThat(repository.claimQueued("runner-1", 5)).isEmpty();
        assertThat(repository.reclaimExpired(LocalDateTime.now(), 3, 10)).isEqualTo(0);
        assertThat(repository.countByStatus("QUEUED", null, null)).isEqualTo(0);
        verify(agentTaskMapper, never()).selectList(any());
        verify(agentTaskMapper, never()).selectCount(any());
        verify(agentTaskMapper, never()).update(any(), eq((LambdaUpdateWrapper) null));
    }
}
