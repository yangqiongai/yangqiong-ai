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
package com.yangqiongai.ai.agent.core.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存式会话存储
 * <p>
 * 作为 AgentSessionStore 的默认兜底实现，
 * 保证同一会话内的多次对话仍能在进程内保留上下文。仅适用于单进程场景，重启丢失、多副本不共享，
 * 生产多实例部署应引入具备持久化能力的会话模块实现，本实现会通过自动配置的条件装配自动退避。
 * </p>
 * @author yangqiong
 */
public class InMemoryAgentSessionStore implements AgentSessionStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryAgentSessionStore.class);

    /**
     * 默认最大会话数
     */
    private static final int DEFAULT_MAX_SESSIONS = 1000;

    /**
     * 默认单会话最大消息数
     */
    private static final int DEFAULT_MAX_MESSAGES_PER_SESSION = 100;

    /**
     * 会话缓存，key为会话ID
     */
    private final Map<String, AgentSessionRecord> sessions = new ConcurrentHashMap<>();

    /**
     * 消息缓存，key为会话ID，value为该会话的消息列表
     */
    private final Map<String, List<AgentMessageRecord>> messages = new ConcurrentHashMap<>();

    /**
     * 最大会话数
     */
    private final int maxSessions = DEFAULT_MAX_SESSIONS;

    /**
     * 单会话最大消息数
     */
    private final int maxMessagesPerSession = DEFAULT_MAX_MESSAGES_PER_SESSION;

    @Override
    public List<AgentMessageRecord> findMessagesBySessionId(String sessionId) {
        List<AgentMessageRecord> sessionMessages = messages.get(sessionId);
        if (sessionMessages == null || sessionMessages.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(sessionMessages);
    }

    @Override
    public void saveMessage(AgentMessageRecord record) {
        if (record == null || record.getSessionId() == null) {
            return;
        }
        messages.compute(record.getSessionId(), (sid, list) -> {
            List<AgentMessageRecord> target = list != null ? list : new ArrayList<>();
            target.add(record);
            // 超出上限时裁剪最旧消息，仅保留最近N条
            while (target.size() > maxMessagesPerSession) {
                target.remove(0);
            }
            return target;
        });
    }

    @Override
    public Optional<AgentSessionRecord> findSession(String sessionId) {
        return Optional.ofNullable(sessions.get(sessionId));
    }

    @Override
    public void createSession(AgentSessionRecord record) {
        if (record == null || record.getSessionId() == null) {
            return;
        }
        // 超出会话上限时淘汰最久未更新的会话，防止内存膨胀
        if (sessions.size() >= maxSessions && !sessions.containsKey(record.getSessionId())) {
            evictSessions(record.getSessionId());
        }
        sessions.put(record.getSessionId(), record);
    }

    @Override
    public void updateSession(AgentSessionRecord record) {
        if (record == null || record.getSessionId() == null) {
            return;
        }
        sessions.put(record.getSessionId(), record);
    }

    @Override
    public void closeSession(String sessionId) {
        if (sessionId == null) {
            return;
        }
        sessions.remove(sessionId);
        messages.remove(sessionId);
    }

    @Override
    public void triggerSummary(String sessionId) {
        if (sessionId == null) {
            return;
        }
        AgentSessionRecord session = sessions.get(sessionId);
        if (session != null) {
            session.setSummaryRound((session.getSummaryRound() == null ? 0 : session.getSummaryRound()) + 1);
        }
    }

    /**
     * 淘汰最早更新的会话，为新会话腾出空间
     * @param newSessionId
     */
    private void evictSessions(String newSessionId) {
        sessions.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(newSessionId))
                .min(Comparator.comparing(entry ->
                        Optional.ofNullable(entry.getValue().getUpdateTime()).orElse(entry.getValue().getCreateTime()),
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .ifPresent(oldest -> {
                    sessions.remove(oldest.getKey());
                    messages.remove(oldest.getKey());
                    log.warn("内存会话存储已满，淘汰最旧会话: sessionId={}", oldest.getKey());
                });
    }
}