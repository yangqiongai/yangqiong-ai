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
package com.yangqiongai.ai.trigger.scheduler;

import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CRON触发调度测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class TriggerSchedulerTest {

    @Mock
    private AgentTriggerRepository triggerRepository;

    @Mock
    private AgentTriggerService triggerService;

    private TriggerScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new TriggerScheduler();
        ReflectionTestUtils.setField(scheduler, "triggerRepository", triggerRepository);
        ReflectionTestUtils.setField(scheduler, "triggerService", triggerService);
    }

    /**
     * 构建规则
     */
    private AgentTriggerEntity trigger(String cronExpr, LocalDateTime lastFireTime) {
        AgentTriggerEntity trigger = new AgentTriggerEntity();
        trigger.setId(1L);
        trigger.setTriggerCode("tg-001");
        trigger.setTriggerType(AgentTriggerEntity.TYPE_CRON);
        trigger.setAgentCode("agent-a");
        trigger.setCronExpr(cronExpr);
        trigger.setEnabled(1);
        trigger.setLastFireTime(lastFireTime);
        return trigger;
    }

    @Test
    void fireIfDueTriggersOverdueCronAndAdvancesBase() {
        AgentTriggerEntity trigger = trigger("* * * * *", LocalDateTime.now().minusHours(1));
        Instant now = Instant.now();
        when(triggerService.fire(eq("tg-001"), anyString(), anyString(), anyString()))
                .thenReturn(AgentTriggerFireResult.fired("task-1"));

        boolean fired = scheduler.fireIfDue(trigger, now);

        assertThat(fired).isTrue();
        // 基准推进到计划时刻：晚于原基准（now-1h），且不晚于当前时刻
        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(triggerRepository).updateLastFireTime(eq(1L), captor.capture());
        LocalDateTime advanced = captor.getValue();
        LocalDateTime oldBase = trigger.getLastFireTime();
        assertThat(advanced).isAfter(oldBase);
        assertThat(advanced).isBeforeOrEqualTo(LocalDateTime.ofInstant(now, ZoneId.systemDefault()));
    }

    @Test
    void fireIfDueSkipsFutureCron() {
        AgentTriggerEntity trigger = trigger("* * * * *",
                LocalDateTime.ofInstant(Instant.now().plusSeconds(3600), ZoneId.systemDefault()));
        boolean fired = scheduler.fireIfDue(trigger, Instant.now());
        assertThat(fired).isFalse();
        verify(triggerService, never()).fire(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void fireIfDueSkipsBlankExpression() {
        boolean fired = scheduler.fireIfDue(trigger("", null), Instant.now());
        assertThat(fired).isFalse();
    }

    @Test
    void fireIfDueSkipsInvalidExpression() {
        boolean fired = scheduler.fireIfDue(trigger("not-a-cron", null), Instant.now());
        assertThat(fired).isFalse();
    }

    @Test
    void fireIfDueFiresImmediatelyForNeverFiredTrigger() {
        // 从未触发过的规则以60秒回退为基准，每分钟规则应立即到期
        AgentTriggerEntity trigger = trigger("* * * * *", null);
        when(triggerService.fire(eq("tg-001"), anyString(), anyString(), anyString()))
                .thenReturn(AgentTriggerFireResult.fired("task-1"));

        boolean fired = scheduler.fireIfDue(trigger, Instant.now());

        assertThat(fired).isTrue();
    }

    @Test
    void fireIfDueAdvancesBaseEvenWhenRejected() {
        // 配额耗尽等拒绝场景下基准同样推进，避免失败风暴追赶
        AgentTriggerEntity trigger = trigger("* * * * *", LocalDateTime.now().minusHours(1));
        Instant now = Instant.now();
        when(triggerService.fire(eq("tg-001"), anyString(), anyString(), anyString()))
                .thenReturn(AgentTriggerFireResult.rejected(AgentTriggerFireResult.QUOTA_EXCEEDED, "配额耗尽"));

        boolean fired = scheduler.fireIfDue(trigger, now);

        assertThat(fired).isFalse();
        verify(triggerRepository).updateLastFireTime(eq(1L), any(LocalDateTime.class));
    }
}
