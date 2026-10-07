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
package com.yangqiongai.ai.platform.conversation.adapter;

import com.yangqiongai.ai.agent.core.session.AgentMessageRecord;
import com.yangqiongai.ai.agent.core.session.AgentSessionRecord;
import com.yangqiongai.ai.agent.core.session.AgentSessionStore;
import com.yangqiongai.ai.platform.conversation.governance.TokenBudgetTracker;
import com.yangqiongai.ai.platform.conversation.service.ConversationSlaService;
import com.yangqiongai.ai.platform.conversation.service.SessionLifecycleService;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.memory.ConversationSummaryService;
import com.yangqiongai.ai.memory.ChatMemoryManager;
import com.yangqiongai.ai.memory.SessionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 对话会话存储适配器
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class ConversationAgentSessionStore implements AgentSessionStore {

    private static final Logger log = LoggerFactory.getLogger(ConversationAgentSessionStore.class);

    @Autowired
    private ChatMemoryManager chatMemoryManager;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ConversationSummaryService conversationSummaryService;

    @Autowired
    private SessionLifecycleService sessionLifecycleService;

    @Autowired
    private TokenBudgetTracker tokenBudgetTracker;

    @Autowired
    private ConversationSlaService conversationSlaService;

    /**
     * 根据会话ID查找消息列表
     * @param sessionId
     * @return
     */
    @Override
    public List<AgentMessageRecord> findMessagesBySessionId(String sessionId) {
        List<ChatMemoryRecord> memories = chatMemoryManager.findBySessionId(sessionId);
        List<AgentMessageRecord> records = new ArrayList<>();
        if (memories == null) {
            return records;
        }
        for (ChatMemoryRecord memory : memories) {
            records.add(toMessageRecord(memory));
        }
        return records;
    }

    /**
     * 保存消息并追踪 Token 用量与 SLA
     * @param record
     */
    @Override
    public void saveMessage(AgentMessageRecord record) {
        ChatMemoryRecord memory = toChatMemoryRecord(record);
        chatMemoryManager.saveMessage(memory);
        // 优先使用模型返回的真实Token数，无真实值时降级为估算值
        int tokenCountForBudget = record.getTotalTokens() != null && record.getTotalTokens() > 0
                ? record.getTotalTokens()
                : (record.getTokenCount() != null ? record.getTokenCount() : 0);
        if (tokenCountForBudget > 0) {
            tokenBudgetTracker.checkAndAccrue(record.getUserId(), record.getSessionId(), tokenCountForBudget);
        }
        // 记录 SLA：assistant 消息时计算距上一条 user 消息的响应时间
        recordSlaIfNeeded(record);
    }

    /**
     * 当保存 assistant 消息时，查询同会话上一条 user 消息并计算响应时间
     * @param record
     */
    private void recordSlaIfNeeded(AgentMessageRecord record) {
        if (!"assistant".equalsIgnoreCase(record.getMessageRole())) {
            return;
        }
        try {
            List<ChatMemoryRecord> messages = chatMemoryManager.findBySessionId(record.getSessionId());
            ChatMemoryRecord lastUser = null;
            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMemoryRecord m = messages.get(i);
                if ("user".equalsIgnoreCase(m.getMessageRole())) {
                    lastUser = m;
                    break;
                }
            }
            if (lastUser == null || lastUser.getCreateTime() == null || record.getCreateTime() == null) {
                return;
            }
            long responseTimeMs = Duration.between(lastUser.getCreateTime(), record.getCreateTime()).toMillis();
            int tokenCount = record.getTotalTokens() != null && record.getTotalTokens() > 0
                    ? record.getTotalTokens()
                    : (record.getTokenCount() != null ? record.getTokenCount() : 0);
            conversationSlaService.recordResponse(record.getUserId(), record.getSessionId(), responseTimeMs, tokenCount);
        } catch (Exception e) {
            log.warn("SLA 记录异常，不影响业务: sessionId={}", record.getSessionId(), e);
        }
    }

    /**
     * 根据会话ID查找会话
     * @param sessionId
     * @return
     */
    @Override
    public Optional<AgentSessionRecord> findSession(String sessionId) {
        return sessionManager.findBySessionId(sessionId)
                .map(this::toSessionRecord);
    }

    /**
     * 创建会话
     * @param record
     */
    @Override
    public void createSession(AgentSessionRecord record) {
        ConversationSessionInfo session = toConversationSessionInfo(record);
        sessionManager.create(session);
    }

    /**
     * 更新会话
     * @param record
     */
    @Override
    public void updateSession(AgentSessionRecord record) {
        ConversationSessionInfo session = toConversationSessionInfo(record);
        sessionManager.update(session);
    }

    /**
     * 关闭会话并合并长期记忆
     * @param sessionId
     */
    @Override
    public void closeSession(String sessionId) {
        sessionLifecycleService.closeAndMerge(sessionId);
    }

    /**
     * 触发增量摘要
     * @param sessionId
     */
    @Override
    public void triggerSummary(String sessionId) {
        conversationSummaryService.triggerIncrementalSummaryAsync(sessionId);
    }

    /**
     * 将ChatMemoryRecord转换为AgentMessageRecord
     * @param memory
     * @return
     */
    private AgentMessageRecord toMessageRecord(ChatMemoryRecord memory) {
        AgentMessageRecord record = new AgentMessageRecord();
        record.setMessageId(memory.getMessageId());
        record.setSessionId(memory.getSessionId());
        record.setUserId(memory.getUserId());
        record.setMessageRole(memory.getMessageRole());
        record.setMessageContent(memory.getMessageContent());
        record.setTokenCount(memory.getTokenCount());
        record.setInputTokens(memory.getInputTokens());
        record.setOutputTokens(memory.getOutputTokens());
        record.setTotalTokens(memory.getTotalTokens());
        record.setExecutionTime(memory.getExecutionTime());
        record.setCreateTime(memory.getCreateTime());
        return record;
    }

    /**
     * 将AgentMessageRecord转换为ChatMemoryRecord
     * @param record
     * @return
     */
    private ChatMemoryRecord toChatMemoryRecord(AgentMessageRecord record) {
        ChatMemoryRecord memory = new ChatMemoryRecord();
        memory.setMessageId(record.getMessageId());
        memory.setSessionId(record.getSessionId());
        memory.setUserId(record.getUserId());
        memory.setScopeId(record.getScopeId());
        memory.setMessageRole(record.getMessageRole());
        memory.setMessageContent(record.getMessageContent());
        memory.setTokenCount(record.getTokenCount());
        memory.setInputTokens(record.getInputTokens());
        memory.setOutputTokens(record.getOutputTokens());
        memory.setTotalTokens(record.getTotalTokens());
        memory.setExecutionTime(record.getExecutionTime());
        memory.setCreateTime(record.getCreateTime());
        return memory;
    }

    /**
     * 将ConversationSessionInfo转换为AgentSessionRecord
     * @param session
     * @return
     */
    private AgentSessionRecord toSessionRecord(ConversationSessionInfo session) {
        AgentSessionRecord record = new AgentSessionRecord();
        record.setSessionId(session.getSessionId());
        record.setUserId(session.getUserId());
        record.setScopeId(session.getScopeId());
        record.setAgentCode(session.getAgentCode());
        record.setSessionType(session.getSessionType());
        record.setSessionTitle(session.getSessionTitle());
        record.setSessionStatus(session.getSessionStatus());
        record.setSummaryRound(session.getSummaryRound());
        record.setSummaryText(session.getSummaryText());
        record.setUpdateTime(session.getUpdateTime());
        return record;
    }

    /**
     * 将AgentSessionRecord转换为ConversationSessionInfo
     * @param record
     * @return
     */
    private ConversationSessionInfo toConversationSessionInfo(AgentSessionRecord record) {
        ConversationSessionInfo session = new ConversationSessionInfo();
        session.setSessionId(record.getSessionId());
        session.setUserId(record.getUserId());
        session.setScopeId(record.getScopeId());
        session.setAgentCode(record.getAgentCode());
        session.setSessionType(record.getSessionType());
        session.setSessionTitle(record.getSessionTitle());
        session.setSessionStatus(record.getSessionStatus());
        session.setSummaryRound(record.getSummaryRound());
        session.setUpdateTime(record.getUpdateTime());
        return session;
    }
}
