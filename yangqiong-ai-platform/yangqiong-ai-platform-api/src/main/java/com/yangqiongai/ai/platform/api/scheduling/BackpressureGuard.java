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
package com.yangqiongai.ai.platform.api.scheduling;

import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Agent任务入队背压守卫
 * @author yangqiong
 */
@Component
public class BackpressureGuard {

    private static final Logger log = LoggerFactory.getLogger(BackpressureGuard.class);

    /**
     * DEGRADE策略降级后的优先级
     */
    private static final int DEGRADED_PRIORITY = 2;

    private static final long WAIT_POLL_INTERVAL_MS = 200;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private QueueProperties properties;

    /**
     * 背压判定结果
     */
    public record CheckResult(int priority) {
    }

    /**
     * 入队前背压检查，返回生效优先级（DEGRADE时可能被降低）
     * @param scopeId
     * @param agentCode
     * @param priority
     * @return
     */
    public CheckResult check(String scopeId, String agentCode, int priority) {
        QueueProperties.Backpressure bp = properties.getBackpressure();
        String strategy = bp.getStrategy() == null ? "REJECT" : bp.getStrategy().toUpperCase();

        if (exceedsLimit(scopeId, agentCode)) {
            return switch (strategy) {
                case "WAIT" -> waitThenCheck(scopeId, agentCode, priority);
                case "DEGRADE" -> {
                    int degraded = Math.min(priority, DEGRADED_PRIORITY);
                    log.info("背压DEGRADE降优先级入队: scopeId={}, agentCode={}, priority={}->{}, queueDepth={}, running={}",
                            scopeId, agentCode, priority, degraded,
                            queueDepth(scopeId, agentCode), runningCount(scopeId, agentCode));
                    yield new CheckResult(degraded);
                }
                default -> throw new AiException(AiErrorCode.AGENT_QUOTA_EXCEEDED,
                        "Agent任务队列已满，触发背压拒绝: agentCode=" + agentCode
                                + ", queueDepth=" + queueDepth(scopeId, agentCode)
                                + ", running=" + runningCount(scopeId, agentCode));
            };
        }
        return new CheckResult(priority);
    }

    /**
     * WAIT策略：阻塞等待容量释放，超时转REJECT
     * @param scopeId
     * @param agentCode
     * @param priority
     * @return
     */
    private CheckResult waitThenCheck(String scopeId, String agentCode, int priority) {
        long deadline = System.currentTimeMillis() + properties.getBackpressure().getWaitTimeoutMs();
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(WAIT_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            if (!exceedsLimit(scopeId, agentCode)) {
                return new CheckResult(priority);
            }
        }
        throw new AiException(AiErrorCode.AGENT_QUOTA_EXCEEDED,
                "Agent任务队列已满，等待超时触发背压拒绝: agentCode=" + agentCode);
    }

    /**
     * 是否超出并发或队列深度限额
     * @param scopeId
     * @param agentCode
     * @return
     */
    private boolean exceedsLimit(String scopeId, String agentCode) {
        QueueProperties.Backpressure bp = properties.getBackpressure();
        if (bp.getMaxQueueDepthPerScope() > 0
                && agentTaskRepository.countByStatus("QUEUED", scopeId, null) >= bp.getMaxQueueDepthPerScope()) {
            return true;
        }
        if (bp.getMaxQueueDepthPerAgent() > 0
                && agentTaskRepository.countByStatus("QUEUED", null, agentCode) >= bp.getMaxQueueDepthPerAgent()) {
            return true;
        }
        if (bp.getMaxConcurrentPerScope() > 0
                && agentTaskRepository.countByStatus("RUNNING", scopeId, null) >= bp.getMaxConcurrentPerScope()) {
            return true;
        }
        return bp.getMaxConcurrentPerAgent() > 0
                && agentTaskRepository.countByStatus("RUNNING", null, agentCode) >= bp.getMaxConcurrentPerAgent();
    }

    /**
     * 当前队列深度（诊断信息用，取agent维度）
     * @param scopeId
     * @param agentCode
     * @return
     */
    private long queueDepth(String scopeId, String agentCode) {
        return agentTaskRepository.countByStatus("QUEUED", null, agentCode);
    }

    /**
     * 当前并发运行数（诊断信息用，取agent维度）
     * @param scopeId
     * @param agentCode
     * @return
     */
    private long runningCount(String scopeId, String agentCode) {
        return agentTaskRepository.countByStatus("RUNNING", null, agentCode);
    }

    /**
     * 供测试注入仓库
     * @param agentTaskRepository
     */
    void setAgentTaskRepository(AgentTaskRepository agentTaskRepository) {
        this.agentTaskRepository = agentTaskRepository;
    }

    /**
     * 供测试注入配置
     * @param properties
     */
    void setProperties(QueueProperties properties) {
        this.properties = properties;
    }
}
