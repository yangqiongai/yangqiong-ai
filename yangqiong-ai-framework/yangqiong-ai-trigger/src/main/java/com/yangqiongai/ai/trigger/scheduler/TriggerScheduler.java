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

import com.yangqiongai.agent.harness.cron.CronExpression;
import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * CRON触发调度
 * <p>
 * 轮询启用的CRON触发器，解析复用引擎CronExpression避免双口径。
 * 幂等推进：以last_fire_time为基准计算下一次计划时刻，触发后推进到计划时刻
 * （而非当前时间），保证同窗口不重复且停机后不追帧风暴。
 * </p>
 * @author yangqiong
 */
@Component
public class TriggerScheduler {

    private static final Logger log = LoggerFactory.getLogger(TriggerScheduler.class);

    private static final String FIRE_SOURCE = "CRON_SCHEDULER";

    /**
     * 未触发过的触发器基准回退秒数（保证首次立即触发）
     */
    private static final long FIRST_FIRE_BACKOFF_SECONDS = 60;

    private static final int MAX_TRIGGERS_PER_ROUND = 200;

    @Autowired
    private AgentTriggerRepository triggerRepository;

    @Autowired
    private AgentTriggerService triggerService;

    /**
     * CRON触发轮询（单轮限量防止规则异常导致风暴）
     * @return
     */
    @Scheduled(fixedDelayString = "${ai.agent.trigger.cron-poll-interval-ms:60000}",
            initialDelayString = "${ai.agent.trigger.cron-poll-initial-delay-ms:15000}")
    public int scanCronTriggers() {
        List<AgentTriggerEntity> triggers =
                triggerRepository.findEnabledByType(AgentTriggerEntity.TYPE_CRON);
        int fired = 0;
        Instant now = Instant.now();
        for (AgentTriggerEntity trigger : triggers) {
            if (fired >= MAX_TRIGGERS_PER_ROUND) {
                log.warn("CRON触发单轮达到上限{}, 剩余规则下轮处理", MAX_TRIGGERS_PER_ROUND);
                break;
            }
            if (fireIfDue(trigger, now)) {
                fired++;
            }
        }
        return fired;
    }

    /**
     * 到期判定与触发：nextFire按last_fire_time推进，到期则触发并推进基准
     * @param trigger
     * @param now
     * @return
     */
    boolean fireIfDue(AgentTriggerEntity trigger, Instant now) {
        if (trigger.getCronExpr() == null || trigger.getCronExpr().isBlank()) {
            return false;
        }
        CronExpression expression;
        try {
            expression = CronExpression.parse(trigger.getCronExpr());
        } catch (Exception e) {
            log.warn("CRON表达式解析失败, 跳过: trigger={}, expr={}", trigger.getTriggerCode(), trigger.getCronExpr());
            return false;
        }
        ZoneId zone = ZoneId.systemDefault();
        Instant base = trigger.getLastFireTime() != null
                ? trigger.getLastFireTime().atZone(zone).toInstant()
                : now.minusSeconds(FIRST_FIRE_BACKOFF_SECONDS);
        Instant nextFire;
        try {
            nextFire = expression.nextFireAfter(base);
        } catch (Exception e) {
            log.warn("CRON下一次触发时刻计算失败: trigger={}", trigger.getTriggerCode(), e);
            return false;
        }
        if (nextFire == null || nextFire.isAfter(now)) {
            return false;
        }
        String dedupKey = "cron-" + nextFire.toEpochMilli();
        AgentTriggerFireResult result = triggerService.fire(trigger.getTriggerCode(), dedupKey,
                "定时触发(" + trigger.getCronExpr() + ")", FIRE_SOURCE);
        // 触发后推进基准到计划时刻，无论入队结果如何均不重复追赶，避免失败风暴
        triggerRepository.updateLastFireTime(trigger.getId(),
                LocalDateTime.ofInstant(nextFire, zone));
        if (!result.isFired()) {
            log.info("CRON触发被拒绝: trigger={}, status={}, reason={}",
                    trigger.getTriggerCode(), result.getStatus(), result.getMessage());
        }
        return result.isFired();
    }
}
