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

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Agent任务队列调度器
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.agent.queue.enabled", havingValue = "true")
public class AgentTaskQueueScheduler {

    private static final Logger log = LoggerFactory.getLogger(AgentTaskQueueScheduler.class);

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private AgentTaskEnqueuer agentTaskEnqueuer;

    @Autowired
    private AgentEngine agentEngine;

    @Autowired
    private QueueProperties properties;

    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * 实例标识(hostname:port:uuid)，启动时生成
     */
    private final String runnerId;

    public AgentTaskQueueScheduler() {
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown-host";
        }
        this.runnerId = host + ":" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 获取本实例标识
     * @return
     */
    public String getRunnerId() {
        return runnerId;
    }

    /**
     * 轮询调度：先回收心跳超时任务，再抢占队列任务分发执行
     */
    @Scheduled(fixedDelayString = "${ai.agent.queue.poll-interval-ms:2000}")
    public void poll() {
        // 心跳超时回收：未耗尽重派次数的置回QUEUED，耗尽的置FAILED（失败不阻断抢占）
        try {
            int reclaimed = agentTaskRepository.reclaimExpired(
                    LocalDateTime.now().minusSeconds(properties.getReclaimExpireSeconds()),
                    properties.getMaxRedeliver(), properties.getBatchSize());
            if (reclaimed > 0) {
                log.info("回收心跳超时任务: count={}, runnerId={}", reclaimed, runnerId);
            }
        } catch (Exception e) {
            log.error("回收心跳超时任务失败: runnerId={}", runnerId, e);
        }

        // 抢占队列任务并分发到本实例执行链路
        try {
            List<AgentTaskInfo> claimed = agentTaskRepository.claimQueued(runnerId, properties.getBatchSize());
            for (AgentTaskInfo task : claimed) {
                dispatch(task);
            }
        } catch (Exception e) {
            log.error("队列抢占调度失败: runnerId={}", runnerId, e);
        }
    }

    /**
     * 心跳续约：定期刷新本实例全部在跑任务的心跳
     */
    @Scheduled(fixedDelayString = "${ai.agent.queue.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        try {
            agentTaskRepository.heartbeat(runnerId);
        } catch (Exception e) {
            log.error("刷新任务心跳失败: runnerId={}", runnerId, e);
        }
    }

    /**
     * 分发抢占到的任务到既有执行链路
     * @param task
     */
    private void dispatch(AgentTaskInfo task) {
        try {
            AgentRequest request = agentTaskEnqueuer.deserializeRequest(task);
            if (request == null) {
                log.error("队列任务请求还原失败，标记FAILED: taskId={}", task.getTaskId());
                agentTaskRepository.markFailed(task.getTaskId(), "队列任务请求还原失败", 0);
                return;
            }
            agentEngine.executeClaimed(task.getTaskId(), request);
            log.info("队列任务已分发执行: taskId={}, runnerId={}", task.getTaskId(), runnerId);
        } catch (Exception e) {
            log.error("队列任务分发失败: taskId={}", task.getTaskId(), e);
            agentTaskRepository.markFailed(task.getTaskId(), "队列任务分发失败: " + e.getMessage(), 0);
        }
    }
}
