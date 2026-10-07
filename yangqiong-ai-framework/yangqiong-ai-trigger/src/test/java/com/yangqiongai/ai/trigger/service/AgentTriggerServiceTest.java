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
package com.yangqiongai.ai.trigger.service;

import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.entity.AgentTriggerLogEntity;
import com.yangqiongai.ai.trigger.event.AgentTriggerFiredEvent;
import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.notify.TriggerNotifyService;
import com.yangqiongai.ai.trigger.repository.AgentTriggerLogRepository;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent触发器管理测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class AgentTriggerServiceTest {

    @Mock
    private AgentTriggerRepository triggerRepository;

    @Mock
    private AgentTriggerLogRepository logRepository;

    @Mock
    private AgentTaskRepository agentTaskRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private TriggerNotifyService notifyService;

    private AgentTriggerService service;

    @BeforeEach
    void setUp() {
        service = new AgentTriggerService();
        ReflectionTestUtils.setField(service, "triggerRepository", triggerRepository);
        ReflectionTestUtils.setField(service, "logRepository", logRepository);
        ReflectionTestUtils.setField(service, "agentTaskRepository", agentTaskRepository);
        ReflectionTestUtils.setField(service, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(service, "notifyService", notifyService);
    }

    /**
     * 构建启用中的CRON规则
     */
    private AgentTriggerEntity enabledCronTrigger() {
        AgentTriggerEntity trigger = new AgentTriggerEntity();
        trigger.setId(100L);
        trigger.setTriggerCode("tg-001");
        trigger.setTriggerType(AgentTriggerEntity.TYPE_CRON);
        trigger.setAgentCode("agent-a");
        trigger.setUserAnchor("user-1");
        trigger.setCronExpr("* * * * *");
        trigger.setEnabled(1);
        trigger.setDedupWindowSeconds(0);
        return trigger;
    }

    @Test
    void fireReturnsNotFoundWhenTriggerMissing() {
        when(triggerRepository.findByCode("tg-x")).thenReturn(null);

        AgentTriggerFireResult result = service.fire("tg-x", null, "载荷", "MANUAL");

        assertThat(result.getStatus()).isEqualTo(AgentTriggerFireResult.NOT_FOUND);
        assertThat(result.isFired()).isFalse();
        verify(agentTaskRepository, never()).createTask(any());
    }

    @Test
    void fireReturnsDisabledWhenTriggerDisabled() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        trigger.setEnabled(0);
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);

        AgentTriggerFireResult result = service.fire("tg-001", null, "载荷", "MANUAL");

        assertThat(result.getStatus()).isEqualTo(AgentTriggerFireResult.DISABLED);
        verify(agentTaskRepository, never()).createTask(any());
    }

    @Test
    void fireRejectsWhenDedupKeyAlreadyFired() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);
        when(logRepository.findLastByDedupKey(100L, "cron-1")).thenReturn(new AgentTriggerLogEntity());

        AgentTriggerFireResult result = service.fire("tg-001", "cron-1", "载荷", "MANUAL");

        assertThat(result.getStatus()).isEqualTo(AgentTriggerFireResult.DUPLICATED);
        verify(agentTaskRepository, never()).createTask(any());
    }

    @Test
    void fireRejectsWhenInsideDedupWindow() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        trigger.setDedupWindowSeconds(60);
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);
        AgentTriggerLogEntity last = new AgentTriggerLogEntity();
        last.setCreateTime(LocalDateTime.now().plusNanos(500_000_000).minusSeconds(60));
        // dedupKey为空跳过幂等键判定，直接走同源去重窗口
        when(logRepository.findLastByTrigger(100L)).thenReturn(last);

        AgentTriggerFireResult result = service.fire("tg-001", null, "载荷", "MANUAL");

        assertThat(result.getStatus()).isEqualTo(AgentTriggerFireResult.DUPLICATED);
    }

    @Test
    void fireAllowsWhenElapsedEqualsDedupWindow() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        trigger.setDedupWindowSeconds(60);
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);
        AgentTriggerLogEntity last = new AgentTriggerLogEntity();
        last.setCreateTime(LocalDateTime.now().minusSeconds(60));
        // dedupKey为空跳过幂等键判定，直接走同源去重窗口
        when(logRepository.findLastByTrigger(100L)).thenReturn(last);

        AgentTriggerFireResult result = service.fire("tg-001", null, "载荷", "MANUAL");

        assertThat(result.isFired()).isTrue();
    }

    @Test
    void fireRejectsWhenDailyQuotaExhausted() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        trigger.setDailyQuota(3);
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);
        when(logRepository.findLastByDedupKey(eq(100L), any())).thenReturn(null);
        when(logRepository.countByTriggerAndTimeRange(eq(100L), any(), any())).thenReturn(3L);

        AgentTriggerFireResult result = service.fire("tg-001", "cron-1", "载荷", "MANUAL");

        assertThat(result.getStatus()).isEqualTo(AgentTriggerFireResult.QUOTA_EXCEEDED);
        assertThat(result.getMessage()).contains("3");
    }

    @Test
    void fireEnqueuesTaskRecordsLogNotifiesAndPublishes() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        trigger.setPayloadTemplate("处理: {payload}");
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);
        when(logRepository.findLastByDedupKey(eq(100L), any())).thenReturn(null);

        AgentTriggerFireResult result = service.fire("tg-001", "cron-1", "订单数据", "MANUAL");

        assertThat(result.isFired()).isTrue();
        assertThat(result.getTaskId()).isNotBlank();

        ArgumentCaptor<AgentTaskInfo> taskCaptor = ArgumentCaptor.forClass(AgentTaskInfo.class);
        verify(agentTaskRepository).createTask(taskCaptor.capture());
        AgentTaskInfo task = taskCaptor.getValue();
        assertThat(task.getTaskStatus()).isEqualTo("QUEUED");
        assertThat(task.getTaskSource()).isEqualTo("TRIGGER");
        assertThat(task.getAgentCode()).isEqualTo("agent-a");
        assertThat(task.getUserInput()).isEqualTo("处理: 订单数据");
        assertThat(task.getBody()).contains("agent-a");

        ArgumentCaptor<AgentTriggerLogEntity> logCaptor = ArgumentCaptor.forClass(AgentTriggerLogEntity.class);
        verify(logRepository).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getDedupKey()).isEqualTo("cron-1");
        assertThat(logCaptor.getValue().getFireSource()).isEqualTo("MANUAL");
        assertThat(logCaptor.getValue().getTaskId()).isEqualTo(result.getTaskId());

        verify(eventPublisher).publishEvent(any(AgentTriggerFiredEvent.class));
        verify(notifyService).notifyFire(eq(trigger), eq(result.getTaskId()), eq("MANUAL"), eq("订单数据"));
    }

    @Test
    void fireEventSourceFiresAllMatchingTriggers() {
        AgentTriggerEntity first = enabledCronTrigger();
        first.setTriggerType(AgentTriggerEntity.TYPE_EVENT);
        first.setEventSource("CONFIG_DRIFT");
        AgentTriggerEntity second = enabledCronTrigger();
        second.setId(200L);
        second.setTriggerCode("tg-002");
        second.setTriggerType(AgentTriggerEntity.TYPE_EVENT);
        second.setEventSource("CONFIG_DRIFT");
        when(triggerRepository.findEnabledByEventSource("CONFIG_DRIFT")).thenReturn(List.of(first, second));
        when(triggerRepository.findByCode(anyString())).thenAnswer(inv -> {
            String code = inv.getArgument(0);
            return "tg-001".equals(code) ? first : second;
        });
        when(logRepository.findLastByDedupKey(any(), any())).thenReturn(null);

        List<String> fired = service.fireEventSource("CONFIG_DRIFT", "key-1", "漂移载荷");

        assertThat(fired).containsExactly("tg-001", "tg-002");
        verify(agentTaskRepository, times(2)).createTask(any());
    }

    @Test
    void fireEventSourceIsolatesSingleTriggerFailure() {
        AgentTriggerEntity first = enabledCronTrigger();
        first.setTriggerType(AgentTriggerEntity.TYPE_EVENT);
        first.setEventSource("EVAL_REGRESSION");
        AgentTriggerEntity second = enabledCronTrigger();
        second.setId(200L);
        second.setTriggerCode("tg-002");
        second.setTriggerType(AgentTriggerEntity.TYPE_EVENT);
        second.setEventSource("EVAL_REGRESSION");
        when(triggerRepository.findEnabledByEventSource("EVAL_REGRESSION")).thenReturn(List.of(first, second));
        when(triggerRepository.findByCode("tg-001"))
                .thenThrow(new IllegalStateException("模拟单规则异常"));
        when(triggerRepository.findByCode("tg-002")).thenReturn(second);
        when(logRepository.findLastByDedupKey(any(), any())).thenReturn(null);

        List<String> fired = service.fireEventSource("EVAL_REGRESSION", "key-1", "载荷");

        assertThat(fired).containsExactly("tg-002");
    }

    @Test
    void fireByWebhookTokenFiresMatchedTrigger() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        trigger.setTriggerType(AgentTriggerEntity.TYPE_WEBHOOK);
        trigger.setWebhookToken("whk-abc");
        when(triggerRepository.findEnabledByWebhookToken("whk-abc")).thenReturn(trigger);
        when(triggerRepository.findByCode("tg-001")).thenReturn(trigger);
        when(logRepository.findLastByDedupKey(eq(100L), any())).thenReturn(null);

        AgentTriggerFireResult result = service.fireByWebhookToken("whk-abc", "key-1", "载荷", "WEBHOOK");

        assertThat(result.isFired()).isTrue();
    }

    @Test
    void fireByWebhookTokenReturnsNotFoundForUnknownToken() {
        when(triggerRepository.findEnabledByWebhookToken("whk-none")).thenReturn(null);

        AgentTriggerFireResult result = service.fireByWebhookToken("whk-none", "key-1", "载荷", "WEBHOOK");

        assertThat(result.getStatus()).isEqualTo(AgentTriggerFireResult.NOT_FOUND);
    }

    @Test
    void createGeneratesCodesAndDefaults() {
        AgentTriggerEntity entity = new AgentTriggerEntity();
        entity.setTriggerType(AgentTriggerEntity.TYPE_CRON);
        entity.setAgentCode("agent-a");
        entity.setCronExpr("* * * * *");
        when(triggerRepository.insert(entity)).thenReturn(1L);

        AgentTriggerEntity created = service.create(entity);

        assertThat(created.getTriggerCode()).startsWith("tg-");
        assertThat(created.getEnabled()).isEqualTo(1);
        assertThat(created.getDedupWindowSeconds()).isEqualTo(0);
    }

    @Test
    void createGeneratesWebhookTokenForWebhookType() {
        AgentTriggerEntity entity = new AgentTriggerEntity();
        entity.setTriggerType(AgentTriggerEntity.TYPE_WEBHOOK);
        entity.setAgentCode("agent-a");
        when(triggerRepository.insert(entity)).thenReturn(1L);

        AgentTriggerEntity created = service.create(entity);

        assertThat(created.getWebhookToken()).startsWith("whk-");
    }

    @Test
    void createRejectsCronWithoutExpression() {
        AgentTriggerEntity entity = new AgentTriggerEntity();
        entity.setTriggerType(AgentTriggerEntity.TYPE_CRON);
        entity.setAgentCode("agent-a");

        assertThatThrownBy(() -> service.create(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cron表达式");
    }

    @Test
    void createRejectsEventWithoutSource() {
        AgentTriggerEntity entity = new AgentTriggerEntity();
        entity.setTriggerType(AgentTriggerEntity.TYPE_EVENT);
        entity.setAgentCode("agent-a");

        assertThatThrownBy(() -> service.create(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("事件源");
    }

    @Test
    void createRejectsFileWithoutWatchDir() {
        AgentTriggerEntity entity = new AgentTriggerEntity();
        entity.setTriggerType(AgentTriggerEntity.TYPE_FILE);
        entity.setAgentCode("agent-a");

        assertThatThrownBy(() -> service.create(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("监听目录");
    }

    @Test
    void createRejectsUnknownTypeAndNegativeQuota() {
        AgentTriggerEntity unknown = new AgentTriggerEntity();
        unknown.setTriggerType("MQ");
        unknown.setAgentCode("agent-a");

        assertThatThrownBy(() -> service.create(unknown))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("触发类型不合法");

        AgentTriggerEntity negative = new AgentTriggerEntity();
        negative.setTriggerType(AgentTriggerEntity.TYPE_CRON);
        negative.setAgentCode("agent-a");
        negative.setCronExpr("* * * * *");
        negative.setDailyQuota(-1);

        assertThatThrownBy(() -> service.create(negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("每日配额");
    }

    @Test
    void changeEnabledFlipsFlag() {
        AgentTriggerEntity trigger = enabledCronTrigger();
        when(triggerRepository.selectById(100L)).thenReturn(trigger);

        service.changeEnabled(100L, false);

        assertThat(trigger.getEnabled()).isEqualTo(0);
        verify(triggerRepository).update(trigger);
    }

    @Test
    void updateRejectsMissingTrigger() {
        AgentTriggerEntity entity = new AgentTriggerEntity();
        entity.setId(999L);
        when(triggerRepository.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> service.update(entity))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("触发器不存在");
    }
}
