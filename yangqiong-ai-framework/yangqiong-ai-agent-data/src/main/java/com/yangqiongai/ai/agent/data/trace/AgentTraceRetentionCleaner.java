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
package com.yangqiongai.ai.agent.data.trace;

import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Agent运行Span保留期清理
 * <p>
 * 按cron周期分批删除创建时间早于保留期的Span，retention-days为0或负数时关闭清理，
 * 单批上限由retention-batch-size控制防大事务锁表，清理异常仅告警不中断调度。
 * </p>
 * @author yangqiong
 */
public class AgentTraceRetentionCleaner {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceRetentionCleaner.class);

    /**
     * 单次调度最大清理轮数（轮数×单批上限为单次执行删除总量上限，防长时间占用调度线程）
     */
    private static final int MAX_ROUNDS = 200;

    private final TraceSpanRepository traceSpanRepository;

    private final AgentTraceProperties properties;

    /**
     * scope保留期覆盖策略(可空，无覆盖时全部scope按全局保留期清理)
     */
    private final TraceRetentionScopePolicy scopePolicy;

    /**
     * 构造保留期清理任务
     * @param traceSpanRepository Span存储
     * @param properties Trace配置
     */
    public AgentTraceRetentionCleaner(TraceSpanRepository traceSpanRepository, AgentTraceProperties properties) {
        this(traceSpanRepository, properties, null);
    }

    /**
     * 构造保留期清理任务(带scope覆盖策略)
     * @param traceSpanRepository Span存储
     * @param properties Trace配置
     * @param scopePolicy scope保留期覆盖策略(可空)
     */
    public AgentTraceRetentionCleaner(TraceSpanRepository traceSpanRepository, AgentTraceProperties properties,
                                      TraceRetentionScopePolicy scopePolicy) {
        this.traceSpanRepository = traceSpanRepository;
        this.properties = properties;
        this.scopePolicy = scopePolicy;
    }

    /**
     * 按cron周期清理过期Span
     */
    @Scheduled(cron = "${ai.agent.trace.retention-cron:0 30 3 * * ?}")
    public void cleanup() {
        try {
            long deleted = cleanupOnce();
            if (deleted > 0) {
                log.info("Span保留期清理完成: retentionDays={}, deleted={}", properties.getRetentionDays(), deleted);
            }
        } catch (Exception e) {
            log.warn("Span保留期清理异常: {}", e.getMessage(), e);
        }
    }

    /**
     * 执行一次过期Span清理，分批循环直到删空或达单次轮数上限
     * <p>
     * 有scope覆盖时：各覆盖scope按覆盖保留期单独清理，全局清理排除覆盖scope
     * （避免覆盖保留期长于全局时数据被提前删除），轮数预算全程共享。
     * </p>
     * @return 删除总行数（保留期非正数时返回0）
     */
    public long cleanupOnce() {
        int retentionDays = properties.getRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        Map<String, Integer> scopeOverrides = scopePolicy != null
                ? scopePolicy.getScopeRetentionDays() : null;
        if (scopeOverrides == null || scopeOverrides.isEmpty()) {
            return cleanupGlobal();
        }
        int batchSize = Math.max(1, properties.getRetentionBatchSize());
        long total = 0;
        int rounds = 0;
        LocalDateTime globalThreshold = LocalDateTime.now().minusDays(retentionDays);
        for (Map.Entry<String, Integer> entry : scopeOverrides.entrySet()) {
            Integer overrideDays = entry.getValue();
            if (overrideDays == null || overrideDays <= 0) {
                continue;
            }
            LocalDateTime threshold = LocalDateTime.now().minusDays(overrideDays);
            while (rounds < MAX_ROUNDS) {
                rounds++;
                int deleted = traceSpanRepository.deleteCreatedBeforeByScope(entry.getKey(), threshold, batchSize);
                total += deleted;
                if (deleted < batchSize) {
                    break;
                }
            }
        }
        while (rounds < MAX_ROUNDS) {
            rounds++;
            int deleted = traceSpanRepository.deleteCreatedBeforeExcludingScopes(
                    globalThreshold, batchSize, scopeOverrides.keySet());
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }

    /**
     * 全局保留期清理（无scope覆盖时的默认路径）
     * @return 删除总行数
     */
    private long cleanupGlobal() {
        int retentionDays = properties.getRetentionDays();
        LocalDateTime threshold = LocalDateTime.now().minusDays(retentionDays);
        int batchSize = Math.max(1, properties.getRetentionBatchSize());
        long total = 0;
        for (int round = 0; round < MAX_ROUNDS; round++) {
            int deleted = traceSpanRepository.deleteCreatedBefore(threshold, batchSize);
            total += deleted;
            if (deleted < batchSize) {
                break;
            }
        }
        return total;
    }
}
