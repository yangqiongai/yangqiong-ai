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
package com.yangqiongai.ai.platform.conversation.service.impl;

import com.yangqiongai.ai.memory.enums.SessionStatus;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import com.yangqiongai.ai.memory.repository.ConversationSessionRepository;
import com.yangqiongai.ai.memory.ChatMemoryManager;
import com.yangqiongai.ai.memory.SessionManager;
import com.yangqiongai.ai.memory.LongTermMemoryManager;
import com.yangqiongai.ai.platform.conversation.service.SessionLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话生命周期管理
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.conversation.enabled", havingValue = "true")
public class SessionLifecycleServiceImpl implements SessionLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SessionLifecycleServiceImpl.class);

    @Autowired
    private ConversationSessionRepository conversationSessionRepository;

    @Autowired
    private ChatMemoryManager chatMemoryManager;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private LongTermMemoryManager longTermMemoryManager;

    @Value("${ai.memory.cleanup.delete-messages:${ai.conversation.cleanup.delete-messages:false}}")
    private boolean deleteMessagesOnArchive;

    @Override
    public int archiveInactiveSessions(int inactiveDays) {
        LocalDateTime threshold = LocalDateTime.now().minusDays(inactiveDays);
        List<ConversationSessionInfo> sessions = conversationSessionRepository.findInactiveSessions(
                SessionStatus.ACTIVE.getCode(), threshold);
        if (sessions.isEmpty()) {
            return 0;
        }

        int archivedCount = 0;
        for (ConversationSessionInfo session : sessions) {
            archiveSession(session);
            archivedCount++;
        }
        log.info("归档不活跃会话完成, 归档数量={}, 不活跃阈值天数={}", archivedCount, inactiveDays);
        return archivedCount;
    }

    @Override
    public void closeAndMerge(String sessionId) {
        // 合并会话摘要到用户长期记忆
        sessionManager.findBySessionId(sessionId).ifPresent(session -> {
            if (session.getSummaryText() != null && !session.getSummaryText().isBlank()) {
                longTermMemoryManager.mergeSessionSummary(session.getUserId(), sessionId, session.getSummaryText());
            }
        });
        sessionManager.closeSession(sessionId);
        log.info("会话已关闭并合并长期记忆: sessionId={}", sessionId);
    }

    @Override
    public String exportSession(String sessionId, String format) {
        ConversationSessionInfo session = sessionManager.findBySessionId(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在: " + sessionId));
        List<ChatMemoryRecord> messages = chatMemoryManager.findBySessionId(sessionId);

        if ("markdown".equalsIgnoreCase(format)) {
            return exportAsMarkdown(session, messages);
        }
        return exportAsJson(session, messages);
    }

    /**
     * 导出为 JSON 字符串
     * @param session
     * @param messages
     * @return
     */
    private String exportAsJson(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"sessionId\": \"").append(escape(session.getSessionId())).append("\",\n");
        sb.append("  \"userId\": \"").append(escape(session.getUserId())).append("\",\n");
        sb.append("  \"title\": \"").append(escape(session.getSessionTitle())).append("\",\n");
        sb.append("  \"agentCode\": \"").append(escape(session.getAgentCode())).append("\",\n");
        sb.append("  \"status\": ").append(session.getSessionStatus()).append(",\n");
        sb.append("  \"summary\": \"").append(escape(session.getSummaryText())).append("\",\n");
        sb.append("  \"messages\": [\n");
        for (int i = 0; i < messages.size(); i++) {
            ChatMemoryRecord msg = messages.get(i);
            sb.append("    {\"role\": \"").append(escape(msg.getMessageRole()))
                    .append("\", \"content\": \"").append(escape(msg.getMessageContent()))
                    .append("\", \"createTime\": \"").append(msg.getCreateTime()).append("\"}");
            if (i < messages.size() - 1) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n}");
        return sb.toString();
    }

    /**
     * 导出为 Markdown 格式
     * @param session
     * @param messages
     * @return
     */
    private String exportAsMarkdown(ConversationSessionInfo session, List<ChatMemoryRecord> messages) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(safeStr(session.getSessionTitle())).append("\n\n");
        sb.append("- **会话ID**: ").append(safeStr(session.getSessionId())).append("\n");
        sb.append("- **用户ID**: ").append(safeStr(session.getUserId())).append("\n");
        sb.append("- **Agent**: ").append(safeStr(session.getAgentCode())).append("\n");
        sb.append("- **状态**: ").append(session.getSessionStatus()).append("\n");
        if (session.getSummaryText() != null && !session.getSummaryText().isBlank()) {
            sb.append("\n## 会话摘要\n\n").append(session.getSummaryText()).append("\n");
        }
        sb.append("\n## 对话记录\n\n");
        for (ChatMemoryRecord msg : messages) {
            sb.append("**").append(safeStr(msg.getMessageRole())).append("**: ")
                    .append(safeStr(msg.getMessageContent())).append("\n\n");
        }
        return sb.toString();
    }

    private String safeStr(String s) {
        return s == null ? "" : s;
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    /**
     * 归档单个会话：更新状态为ARCHIVED并记录归档时间，可选清理详细消息
     * @param session
     */
    private void archiveSession(ConversationSessionInfo session) {
        conversationSessionRepository.archiveSession(session.getSessionId());

        // 归档时可选清理详细消息，保留摘要
        if (deleteMessagesOnArchive) {
            chatMemoryManager.deleteBySessionId(session.getSessionId());
            log.debug("会话消息已清理: sessionId={}", session.getSessionId());
        }
    }
}
