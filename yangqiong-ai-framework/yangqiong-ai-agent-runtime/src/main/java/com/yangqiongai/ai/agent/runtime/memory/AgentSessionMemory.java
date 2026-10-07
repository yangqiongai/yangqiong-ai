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

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

import java.util.List;

/**
 * 会话级短期记忆
 * <p>
 * L2记忆SPI：承载会话内摘要缓存与情节消息，默认引擎内置内存实现，
 * 可替换为Redis/DB等共享存储实现。
 * </p>
 * @author yangqiong
 */
public interface AgentSessionMemory {

    /**
     * 保存会话摘要
     * @param sessionId
     * @param summary
     * @param summarizedMessageCount
     */
    void saveSummary(String sessionId, String summary, int summarizedMessageCount);

    /**
     * 加载会话摘要
     * @param sessionId
     * @return
     */
    SessionSummary loadSummary(String sessionId);

    /**
     * 保存情节消息
     * @param sessionId
     * @param episode
     */
    void saveEpisode(String sessionId, AgentMessage episode);

    /**
     * 列出会话情节消息
     * @param sessionId
     * @param limit
     * @return
     */
    List<AgentMessage> listEpisodes(String sessionId, int limit);

    /**
     * 清除会话记忆
     * @param sessionId
     */
    void clear(String sessionId);
}
