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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Trace保留期清理调度
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.trace.enabled", havingValue = "true")
public class TraceCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(TraceCleanupScheduler.class);

    /**
     * 单批删除条数
     */
    private static final int BATCH_SIZE = 5000;

    /**
     * 单次任务最大批次数(防止单次任务耗时过长)
     */
    private static final int MAX_BATCHES = 100;

    @Autowired
    private TraceSpanRepository traceSpanRepository;

    @Value("${ai.agent.trace.retention-days:30}")
    private int retentionDays;

    /**
     * 每天凌晨2点分批删除超过保留期的Span(跨scope)
     */
    @Scheduled(cron = "${ai.agent.trace.cleanup-cron:0 0 2 * * ?}")
    public void cleanupExpiredSpans() {
        try {
            LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
            int total = 0;
            for (int i = 0; i < MAX_BATCHES; i++) {
                int deleted = traceSpanRepository.deleteCreatedBefore(threshold, BATCH_SIZE);
                total += deleted;
                if (deleted < BATCH_SIZE) {
                    break;
                }
            }
            if (total > 0) {
                log.info("定时清理过期Span完成, 保留天数={}, 删除数量={}", retentionDays, total);
            }
        } catch (Exception e) {
            log.error("定时清理过期Span异常", e);
        }
    }
}
