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
package com.yangqiongai.ai.platform.conversation.scheduler;

import com.yangqiongai.ai.platform.conversation.service.SessionLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 会话清理调度
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class SessionCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(SessionCleanupScheduler.class);

    @Autowired
    private SessionLifecycleService sessionLifecycleService;

    @Value("${ai.memory.cleanup.enabled:${ai.conversation.cleanup.enabled:true}}")
    private boolean cleanupEnabled;

    @Value("${ai.memory.cleanup.inactive-days:${ai.conversation.cleanup.inactive-days:90}}")
    private int inactiveDays;

    /**
     * 每天凌晨2点清理不活跃会话
     */
    @Scheduled(cron = "${ai.memory.cleanup.cron:${ai.conversation.cleanup.cron:0 0 2 * * ?}}")
    public void cleanupInactiveSessions() {
        if (!cleanupEnabled) {
            return;
        }
        try {
            int count = sessionLifecycleService.archiveInactiveSessions(inactiveDays);
            if (count > 0) {
                log.info("定时清理不活跃会话完成, 归档数量={}", count);
            }
        } catch (Exception e) {
            log.error("定时清理不活跃会话异常", e);
        }
    }
}
