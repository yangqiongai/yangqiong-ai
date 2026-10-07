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

import com.yangqiongai.ai.agent.runtime.guardrail.ContentModerationPolicy;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.memory.agentmemory.event.AgentMemoryQuarantinedEvent;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryCandidate;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.vector.AgentMemoryVectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Agent运行记忆写入
 * @author yangqiong
 */
@Service
public class AgentMemoryWriteService {

    private static final Logger log = LoggerFactory.getLogger(AgentMemoryWriteService.class);

    private static final String DEFAULT_MEMORY_TYPE = AgentMemoryEntryInfo.TYPE_EPISODIC;

    private static final double DEFAULT_CONFIDENCE = 0.8;

    @Autowired
    private AgentMemoryEntryRepository entryRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired(required = false)
    private AgentMemoryVectorStore agentMemoryVectorStore;

    @Autowired(required = false)
    private ContentModerationPolicy contentModerationPolicy;

    @Value("${ai.memory.agent.write.enabled:true}")
    private boolean writeEnabled;

    @Value("${ai.memory.agent.write.max-content-length:4000}")
    private int maxContentLength;

    /**
     * 写入记忆候选（安全网关→去重→落库+向量化）
     * @param candidate
     * @return
     */
    public AgentMemoryEntryInfo store(AgentMemoryCandidate candidate) {
        if (!writeEnabled || candidate == null) {
            return null;
        }
        String content = normalize(candidate.getContent());
        if (content == null) {
            return null;
        }
        String agentCode = trimToNull(candidate.getAgentCode());
        String userAnchor = trimToNull(candidate.getUserAnchor());
        if (agentCode == null || userAnchor == null) {
            log.warn("Agent记忆写入缺少归属维度, agentCode={}, userAnchor={}", agentCode, userAnchor);
            return null;
        }
        String memoryType = candidate.getMemoryType() == null ? DEFAULT_MEMORY_TYPE : candidate.getMemoryType();
        // 异步写线程绑定记忆归属作用域，保证去重查询与落库在同一隔离域
        String resolvedScopeId = candidate.getScopeId() != null && !candidate.getScopeId().isBlank()
                ? candidate.getScopeId() : ScopeContext.getScopeId();
        return runWithScope(resolvedScopeId, () -> doStore(candidate, content, agentCode, userAnchor, memoryType, resolvedScopeId));
    }

    /**
     * 记忆落库主体（安全网关→去重→插入→向量化→隔离事件）
     * @param candidate
     * @param content
     * @param agentCode
     * @param userAnchor
     * @param memoryType
     * @param scopeId
     * @return
     */
    private AgentMemoryEntryInfo doStore(AgentMemoryCandidate candidate, String content, String agentCode,
                                         String userAnchor, String memoryType, String scopeId) {
        // 安全网关：可疑来源内容进入隔离态，不参与检索
        boolean quarantined = false;
        String quarantineReason = null;
        if (contentModerationPolicy != null) {
            try {
                var verdict = contentModerationPolicy.moderate(buildModerationMessage(content));
                if (verdict.isBlock()) {
                    quarantined = true;
                    quarantineReason = verdict.getReason();
                }
            } catch (Exception e) {
                log.warn("Agent记忆内容审查异常，按放行处理", e);
            }
        }
        // 内容摘要哈希去重：同锚点同类型已有生效同内容条目则跳过
        String inputHash = sha256(agentCode + "|" + userAnchor + "|" + memoryType + "|" + content);
        if (!quarantined) {
            AgentMemoryEntryInfo existing = entryRepository.findActiveByInputHash(agentCode, userAnchor, inputHash);
            if (existing != null) {
                log.debug("Agent记忆重复写入跳过, entryId={}", existing.getId());
                return existing;
            }
        }

        AgentMemoryEntryInfo entry = new AgentMemoryEntryInfo();
        entry.setAgentCode(agentCode);
        entry.setScopeId(scopeId);
        entry.setUserAnchor(userAnchor);
        entry.setMemoryType(memoryType);
        entry.setContent(content);
        entry.setSourceTaskId(trimToNull(candidate.getSourceTaskId()));
        entry.setSourceUser(trimToNull(candidate.getSourceUser()));
        entry.setConfidence(candidate.getConfidence() == null ? DEFAULT_CONFIDENCE
                : Math.max(0.0, Math.min(1.0, candidate.getConfidence())));
        entry.setStatus(quarantined ? AgentMemoryEntryInfo.STATUS_QUARANTINED : AgentMemoryEntryInfo.STATUS_ACTIVE);
        entry.setInputHash(inputHash);
        entry.setAccessCount(0);
        entry.setVersionNo(1);
        if (candidate.getTtlSeconds() != null && candidate.getTtlSeconds() > 0) {
            entry.setTtlExpireTime(LocalDateTime.now().plusSeconds(candidate.getTtlSeconds()));
        }
        LocalDateTime now = LocalDateTime.now();
        entry.setCreateTime(now);
        entry.setUpdateTime(now);
        Long id = entryRepository.insert(entry);
        entry.setId(id);

        // 向量化（向量库不可用时静默降级，DB侧记录仍完整）
        if (!quarantined && agentMemoryVectorStore != null) {
            boolean indexed = agentMemoryVectorStore.embedAndIndex(id, content, agentCode, userAnchor, memoryType);
            entry.setEmbeddingRef(indexed ? String.valueOf(id) : null);
        }

        if (quarantined) {
            eventPublisher.publishEvent(new AgentMemoryQuarantinedEvent(id, agentCode, userAnchor, quarantineReason));
        }
        return entry;
    }

    /**
     * 以指定作用域执行数据库操作，缺失时保持当前上下文
     * @param scopeId
     * @param action
     * @return
     */
    private <T> T runWithScope(String scopeId, java.util.function.Supplier<T> action) {
        if (scopeId == null || scopeId.isBlank() || scopeId.equals(ScopeContext.getScopeId())) {
            return action.get();
        }
        ScopeContext.setScopeId(scopeId);
        try {
            return action.get();
        } finally {
            ScopeContext.clear();
        }
    }

    /**
     * 批量写入记忆候选
     * @param candidates
     * @return
     */
    public List<AgentMemoryEntryInfo> storeAll(List<AgentMemoryCandidate> candidates) {
        List<AgentMemoryEntryInfo> stored = new ArrayList<>();
        if (candidates == null) {
            return stored;
        }
        for (AgentMemoryCandidate candidate : candidates) {
            AgentMemoryEntryInfo entry = store(candidate);
            if (entry != null) {
                stored.add(entry);
            }
        }
        return stored;
    }

    /**
     * 构建内容审查消息
     * @param content
     * @return
     */
    private AgentMessage buildModerationMessage(String content) {
        return AgentMessage.builder()
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(content).build()))
                .build();
    }

    /**
     * 规范化内容：去除首尾空白、控制长度、空内容返回null
     * @param content
     * @return
     */
    private String normalize(String content) {
        if (content == null) {
            return null;
        }
        String trimmed = content.strip();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > maxContentLength ? trimmed.substring(0, maxContentLength) : trimmed;
    }

    /**
     * 计算内容摘要哈希
     * @param text
     * @return
     */
    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256算法不可用", e);
        }
    }

    private String trimToNull(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        return text.strip();
    }
}
