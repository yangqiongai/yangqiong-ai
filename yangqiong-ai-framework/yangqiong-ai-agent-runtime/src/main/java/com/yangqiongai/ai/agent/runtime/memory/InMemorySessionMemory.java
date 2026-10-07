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
package com.yangqiongai.ai.agent.runtime.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

/**
 * 会话级短期记忆内存默认实现
 * <p>
 * 按sessionId隔离存储会话摘要与情节消息，线程安全。
 * 作为L2默认实现，外部可替换为Redis/DB等持久化实现。
 * </p>
 * @author yangqiong
 */
public class InMemorySessionMemory implements AgentSessionMemory {

    /**
     * 最大会话数，超限淘汰一个已有会话键，防止内存无界增长
     */
    public static final int MAX_SESSIONS = 1000;

    /**
     * 单会话最大情节消息数，超限淘汰最旧情节
     */
    public static final int MAX_EPISODES_PER_SESSION = 200;

    /**
     * 按会话ID隔离的运行摘要
     */
    private final Map<String, SessionSummary> summaries = new ConcurrentHashMap<>();

    /**
     * 按会话ID隔离的情节消息
     */
    private final Map<String, List<AgentMessage>> episodes = new ConcurrentHashMap<>();

    @Override
    public void saveSummary(String sessionId, String summary, int summarizedMessageCount) {
        if (sessionId == null) {
            return;
        }
        if (!summaries.containsKey(sessionId)) {
            evictOverflow(sessionId);
        }
        summaries.put(sessionId, new SessionSummary(summary, summarizedMessageCount));
    }

    /**
     * 会话数超限时淘汰一个已有会话键（摘要与情节同步清理）
     * @param protectedKey 本次写入的会话键，禁止作为淘汰对象
     */
    private void evictOverflow(String protectedKey) {
        if (summaries.size() + episodes.size() < MAX_SESSIONS) {
            return;
        }
        String victim = episodes.keySet().stream()
                .filter(k -> !k.equals(protectedKey))
                .findFirst()
                .orElseGet(() -> summaries.keySet().stream()
                        .filter(k -> !k.equals(protectedKey))
                        .findFirst()
                        .orElse(null));
        if (victim != null) {
            summaries.remove(victim);
            episodes.remove(victim);
        }
    }

    @Override
    public SessionSummary loadSummary(String sessionId) {
        if (sessionId == null) {
            return null;
        }
        return summaries.get(sessionId);
    }

    @Override
    public void saveEpisode(String sessionId, AgentMessage episode) {
        if (sessionId == null || episode == null) {
            return;
        }
        if (!episodes.containsKey(sessionId)) {
            evictOverflow(sessionId);
        }
        List<AgentMessage> list = episodes.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(episode);
        // 单会话情节超限淘汰最旧条目
        if (list.size() > MAX_EPISODES_PER_SESSION) {
            list.remove(0);
        }
    }

    @Override
    public List<AgentMessage> listEpisodes(String sessionId, int limit) {
        if (sessionId == null) {
            return Collections.emptyList();
        }
        List<AgentMessage> list = episodes.get(sessionId);
        if (list == null || list.isEmpty()) {
            return Collections.emptyList();
        }
        int size = list.size();
        int from = limit > 0 ? Math.max(0, size - limit) : 0;
        return new ArrayList<>(list.subList(from, size));
    }

    @Override
    public void clear(String sessionId) {
        if (sessionId == null) {
            return;
        }
        summaries.remove(sessionId);
        episodes.remove(sessionId);
    }
}
