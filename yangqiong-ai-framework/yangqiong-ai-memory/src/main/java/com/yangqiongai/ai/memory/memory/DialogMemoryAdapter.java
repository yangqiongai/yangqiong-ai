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
package com.yangqiongai.ai.memory.memory;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.scheduler.MemoryScheduler;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 对话记忆适配门面
 * @author yangqiong
 */
@Service
public class DialogMemoryAdapter {

    private static final Logger log = LoggerFactory.getLogger(DialogMemoryAdapter.class);

    /**
     * 默认Token预算
     */
    private static final int DEFAULT_MAX_TOKENS = 4000;

    /**
     * 跨会话记忆Token预算上限
     */
    private static final int CROSS_SESSION_MEMORY_TOKEN_BUDGET = 500;

    @Autowired
    private ConversationSessionManager sessionManager;

    @Autowired
    private ConversationSummaryService summaryService;

    @Autowired
    private ConversationHistoryService historyService;

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    @Autowired(required = false)
    private MemoryScheduler memoryScheduler;

    @Value("${ai.memory.schedule.enabled:false}")
    private boolean scheduleEnabled;

    /**
     * 准备对话上下文，确保会话存在并恢复状态
     * @param sessionId
     * @param userId
     * @return
     */
    public ConversationSessionInfo prepareContext(String sessionId, String userId) {
        return prepareContext(sessionId, userId, false);
    }

    /**
     * 准备对话上下文，支持加载跨会话长期记忆
     * @param sessionId
     * @param userId
     * @param enableCrossSessionMemory
     * @return
     */
    public ConversationSessionInfo prepareContext(String sessionId, String userId, boolean enableCrossSessionMemory) {
        return prepareContext(sessionId, userId, enableCrossSessionMemory, null);
    }

    /**
     * 准备对话上下文，支持加载跨会话长期记忆，并可基于当前消息做主动预加载
     * @param sessionId
     * @param userId
     * @param enableCrossSessionMemory
     * @param currentMessage 当前用户消息，非空且开启调度时触发预加载
     * @return
     */
    public ConversationSessionInfo prepareContext(String sessionId, String userId, boolean enableCrossSessionMemory, String currentMessage) {
        ConversationSessionInfo session = sessionManager.ensureSession(sessionId, userId);
        sessionManager.restoreSession(sessionId);
        if (enableCrossSessionMemory) {
            loadCrossSessionMemory(session, userId, currentMessage);
        }
        log.info("对话上下文准备完成: sessionId={}, userId={}, crossSession={}, preload={}",
                sessionId, userId, enableCrossSessionMemory, scheduleEnabled && currentMessage != null);
        return session;
    }

    /**
     * 加载用户跨会话长期记忆并注入到会话摘要中
     * @param session
     * @param userId
     * @param currentMessage
     */
    private void loadCrossSessionMemory(ConversationSessionInfo session, String userId, String currentMessage) {
        try {
            String longTermMemory;
            // 主动调度开启且有当前消息时走预加载路径，否则全量加载
            if (scheduleEnabled && memoryScheduler != null && currentMessage != null && !currentMessage.isBlank()) {
                longTermMemory = memoryScheduler.preloadByContext(userId, currentMessage, CROSS_SESSION_MEMORY_TOKEN_BUDGET);
            } else {
                longTermMemory = longTermMemoryManager.loadUserLongTermMemory(userId, CROSS_SESSION_MEMORY_TOKEN_BUDGET);
            }
            if (longTermMemory == null || longTermMemory.isBlank()) {
                return;
            }
            // 将长期记忆注入到会话摘要前缀，使后续裁剪保留
            String existingSummary = session.getSummaryText() != null ? session.getSummaryText() : "";
            String combinedSummary = "[Cross-Session Memory]\n" + longTermMemory + "\n\n" + existingSummary;
            sessionManager.updateSessionSummary(session.getSessionId(), combinedSummary);
            log.info("跨会话长期记忆已加载: sessionId={}, userId={}, preload={}",
                    session.getSessionId(), userId, scheduleEnabled && currentMessage != null);
        } catch (Exception e) {
            log.warn("加载跨会话长期记忆失败: sessionId={}, userId={}", session.getSessionId(), userId, e);
        }
    }

    /**
     * 追加消息到会话
     * @param sessionId
     * @param role
     * @param content
     */
    public void appendMessage(String sessionId, String role, String content) {
        sessionManager.appendMessage(sessionId, role, content);
        // 异步触发摘要生成
        summaryService.triggerIncrementalSummaryAsync(sessionId);
    }

    /**
     * 获取裁剪后的对话历史
     * @param sessionId
     * @param maxTokens
     * @return
     */
    public List<AgentMessage> getHistory(String sessionId, int maxTokens) {
        int safeMaxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
        return historyService.getTrimmedHistory(sessionId, safeMaxTokens);
    }

    /**
     * 获取裁剪后的对话历史（默认Token预算）
     * @param sessionId
     * @return
     */
    public List<AgentMessage> getHistory(String sessionId) {
        return getHistory(sessionId, DEFAULT_MAX_TOKENS);
    }

    /**
     * 触发摘要生成
     * @param sessionId
     */
    public void generateSummary(String sessionId) {
        summaryService.generateIncrementalSummary(sessionId);
    }

    /**
     * 持久化会话状态
     * @param sessionId
     * @param result
     */
    public void persistSession(String sessionId, Object result) {
        sessionManager.persistSession(sessionId, result);
    }

    /**
     * 加载原始对话历史
     * @param sessionId
     * @return
     */
    public List<ChatMemoryRecord> loadRawHistory(String sessionId) {
        return sessionManager.loadMessages(sessionId);
    }

    /**
     * 清空会话历史
     * @param sessionId
     */
    public void clearHistory(String sessionId) {
        sessionManager.clearSession(sessionId);
    }
}
