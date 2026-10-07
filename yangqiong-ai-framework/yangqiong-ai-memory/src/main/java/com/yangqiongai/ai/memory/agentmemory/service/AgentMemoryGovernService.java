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
package com.yangqiongai.ai.memory.agentmemory.service;

import com.yangqiongai.ai.memory.agentmemory.event.AgentMemoryQuarantinedEvent;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.vector.AgentMemoryVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent运行记忆治理
 * @author yangqiong
 */
@Service
public class AgentMemoryGovernService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryGovernService.class);

    private static final int SWEEP_BATCH_SIZE = 200;

    @Autowired
    private AgentMemoryEntryRepository entryRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private AgentMemoryVectorStore agentMemoryVectorStore;

    @Value("${ai.memory.agent.govern.negative-feedback-step:0.1}")
    private double negativeFeedbackStep;

    @Value("${ai.memory.agent.govern.stale-confidence-threshold:0.2}")
    private double staleConfidenceThreshold;

    /**
     * TTL过期扫描，过期生效条目转STALE归档
     * @return
     */
    @Scheduled(fixedDelayString = "${ai.memory.agent.govern.ttl-sweep-interval-ms:300000}",
            initialDelayString = "${ai.memory.agent.govern.ttl-sweep-initial-delay-ms:60000}")
    public int sweepExpiredTtl() {
        List<AgentMemoryEntryInfo> expired = entryRepository.findExpiredTtl(LocalDateTime.now(), SWEEP_BATCH_SIZE);
        for (AgentMemoryEntryInfo entry : expired) {
            markStale(entry);
        }
        if (!expired.isEmpty()) {
            log.info("Agent记忆TTL过期归档完成, size={}", expired.size());
        }
        return expired.size();
    }

    /**
     * 检索命中未采纳的负反馈降权，低置信归档
     * @param entryId
     * @return
     */
    public AgentMemoryEntryInfo reportNegativeFeedback(Long entryId) {
        AgentMemoryEntryInfo entry = entryRepository.selectById(entryId);
        if (entry == null) {
            return null;
        }
        double current = entry.getConfidence() == null ? 0.0 : entry.getConfidence();
        double updated = Math.max(0.0, current - negativeFeedbackStep);
        entryRepository.updateConfidence(entryId, updated);
        entry.setConfidence(updated);
        if (updated < staleConfidenceThreshold) {
            markStale(entry);
            entry.setStatus(AgentMemoryEntryInfo.STATUS_STALE);
        }
        return entry;
    }

    /**
     * 隔离指定条目（治理处置）
     * @param entryId
     * @param reason
     * @return
     */
    public AgentMemoryEntryInfo quarantine(Long entryId, String reason) {
        AgentMemoryEntryInfo entry = entryRepository.selectById(entryId);
        if (entry == null) {
            return null;
        }
        entryRepository.updateStatus(entryId, AgentMemoryEntryInfo.STATUS_QUARANTINED);
        entry.setStatus(AgentMemoryEntryInfo.STATUS_QUARANTINED);
        eventPublisher.publishEvent(new AgentMemoryQuarantinedEvent(
                entryId, entry.getAgentCode(), entry.getUserAnchor(), reason));
        return entry;
    }

    /**
     * 解除隔离恢复生效
     * @param entryId
     * @return
     */
    public AgentMemoryEntryInfo release(Long entryId) {
        AgentMemoryEntryInfo entry = entryRepository.selectById(entryId);
        if (entry == null) {
            return null;
        }
        entryRepository.updateStatus(entryId, AgentMemoryEntryInfo.STATUS_ACTIVE);
        entry.setStatus(AgentMemoryEntryInfo.STATUS_ACTIVE);
        return entry;
    }

    /**
     * 归档指定条目转STALE并清理向量
     * @param entryId
     * @return
     */
    public AgentMemoryEntryInfo archive(Long entryId) {
        AgentMemoryEntryInfo entry = entryRepository.selectById(entryId);
        if (entry == null) {
            return null;
        }
        markStale(entry);
        entry.setStatus(AgentMemoryEntryInfo.STATUS_STALE);
        return entry;
    }

    /**
     * 转STALE并清理向量点
     * @param entry
     */
    private void markStale(AgentMemoryEntryInfo entry) {
        entryRepository.updateStatus(entry.getId(), AgentMemoryEntryInfo.STATUS_STALE);
        if (agentMemoryVectorStore != null) {
            agentMemoryVectorStore.deleteByMemoryIds(List.of(entry.getId()));
        }
    }
}
