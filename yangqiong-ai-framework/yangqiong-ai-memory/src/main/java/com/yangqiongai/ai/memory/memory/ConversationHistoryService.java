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

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.memory.model.ChatMemoryRecord;
import com.yangqiongai.ai.memory.model.ConversationSessionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话历史加载与转换
 * @author yangqiong
 */
@Service
public class ConversationHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ConversationHistoryService.class);

    @Autowired
    private ConversationSessionManager sessionManager;

    @Autowired
    private HistoryTrimmer historyTrimmer;

    /**
     * 加载会话全部消息
     * @param sessionId
     * @return
     */
    public List<ChatMemoryRecord> loadMessages(String sessionId) {
        return sessionManager.loadMessages(sessionId);
    }

    /**
     * 将ChatMemory列表转换为Agent消息格式
     * @param messages
     * @return
     */
    public List<AgentMessage> toAgentMessages(List<ChatMemoryRecord> messages) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentMessage> contextMessages = new ArrayList<>();
        List<AgentMessage> dialogueMessages = new ArrayList<>();

        for (ChatMemoryRecord memory : messages) {
            String normalizedRole = normalizeRole(memory.getMessageRole());
            String content = memory.getMessageContent() != null ? memory.getMessageContent() : "";

            if ("system".equals(normalizedRole)) {
                contextMessages.add(buildContextMsg(content));
            } else {
                dialogueMessages.add(buildDialogueMsg(normalizedRole, content));
            }
        }

        List<AgentMessage> result = new ArrayList<>(contextMessages.size() + dialogueMessages.size());
        result.addAll(contextMessages);
        result.addAll(dialogueMessages);
        return result;
    }

    /**
     * 按Token预算裁剪历史消息
     * @param messages
     * @param maxTokens
     * @return
     */
    public List<AgentMessage> trimToTokenBudget(List<AgentMessage> messages, int maxTokens) {
        if (messages == null || messages.isEmpty()) {
            return new ArrayList<>();
        }

        List<AgentMessage> systemMessages = new ArrayList<>();
        List<AgentMessage> regularMessages = new ArrayList<>();

        for (AgentMessage msg : messages) {
            if (msg.getRole() == AgentMessageRole.SYSTEM) {
                systemMessages.add(msg);
            } else {
                regularMessages.add(msg);
            }
        }

        // 计算系统消息占用Token
        int systemTokens = estimateMsgTokens(systemMessages);
        int remainingBudget = maxTokens - systemTokens;
        if (remainingBudget <= 0) {
            log.warn("系统消息已超出Token预算, systemTokens={}, maxTokens={}", systemTokens, maxTokens);
            return systemMessages;
        }

        // 从最新消息开始保留
        List<AgentMessage> keptRegular = new ArrayList<>();
        int usedTokens = 0;
        for (int i = regularMessages.size() - 1; i >= 0; i--) {
            AgentMessage msg = regularMessages.get(i);
            int msgTokens = estimateSingleMsgTokens(msg);
            if (usedTokens + msgTokens > remainingBudget) {
                break;
            }
            keptRegular.add(0, msg);
            usedTokens += msgTokens;
        }

        List<AgentMessage> result = new ArrayList<>(systemMessages);
        result.addAll(keptRegular);
        log.debug("历史消息裁剪完成, 原始消息数={}, 裁剪后消息数={}, 使用Token={}",
                messages.size(), result.size(), systemTokens + usedTokens);
        return result;
    }

    /**
     * 获取裁剪后的历史消息（含摘要）
     * @param sessionId
     * @param maxTokens
     * @return
     */
    public List<AgentMessage> getTrimmedHistory(String sessionId, int maxTokens) {
        List<ChatMemoryRecord> messages = loadMessages(sessionId);
        ConversationSessionInfo session = sessionManager.getSession(sessionId);
        String summary = session != null ? session.getSummaryText() : null;

        List<ChatMemoryRecord> trimmed = historyTrimmer.trim(messages, maxTokens, summary);
        return toAgentMessages(trimmed);
    }

    /**
     * 构建上下文消息
     * @param content
     * @return
     */
    private AgentMessage buildContextMsg(String content) {
        String normalizedText = content != null ? content : "";
        if (!normalizedText.startsWith("Context note:\n") && !normalizedText.startsWith("Summary from previous conversation:\n")) {
            normalizedText = "Context note:\n" + normalizedText;
        }
        return AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.<AgentContentBlock>of(AgentTextBlock.builder().text(normalizedText).build()))
                .build();
    }

    /**
     * 构建对话消息
     * @param role
     * @param content
     * @return
     */
    private AgentMessage buildDialogueMsg(String role, String content) {
        return AgentMessage.builder()
                .name(role)
                .role(resolveMsgRole(role))
                .content(List.<AgentContentBlock>of(AgentTextBlock.builder().text(content != null ? content : "").build()))
                .build();
    }

    /**
     * 规范化角色名称
     * @param role
     * @return
     */
    private String normalizeRole(String role) {
        if (role == null || role.isBlank()) {
            return "user";
        }
        String normalized = role.toLowerCase();
        if ("system".equals(normalized) || "assistant".equals(normalized) || "user".equals(normalized)) {
            return normalized;
        }
        return "user";
    }

    /**
     * 解析消息角色
     * @param role
     * @return
     */
    private AgentMessageRole resolveMsgRole(String role) {
        return switch (normalizeRole(role)) {
            case "system" -> AgentMessageRole.SYSTEM;
            case "assistant" -> AgentMessageRole.ASSISTANT;
            default -> AgentMessageRole.USER;
        };
    }

    /**
     * 估算消息列表的Token数
     * @param messages
     * @return
     */
    private int estimateMsgTokens(List<AgentMessage> messages) {
        int total = 0;
        for (AgentMessage msg : messages) {
            total += estimateSingleMsgTokens(msg);
        }
        return total;
    }

    /**
     * 估算单条消息的Token数
     * @param msg
     * @return
     */
    private int estimateSingleMsgTokens(AgentMessage msg) {
        if (msg == null) {
            return 0;
        }
        String text = msg.getTextContent();
        return TokenEstimator.estimateTokens(text);
    }
}
