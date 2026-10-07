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

import java.util.List;
import java.util.Optional;

/**
 * Agent会话存储端口
 * <p>
 * 定义Agent会话持久化的抽象接口，由具体的会话模块（如ai-conversation）提供实现。
 * ai-agent-core通过此接口与上层会话模块解耦，实现依赖倒置。
 * </p>
 * @author yangqiong
 */
public interface AgentSessionStore {

    /**
     * 根据会话ID查找消息列表
     * @param sessionId
     * @return
     */
    List<AgentMessageRecord> findMessagesBySessionId(String sessionId);

    /**
     * 保存消息
     * @param record
     */
    void saveMessage(AgentMessageRecord record);

    /**
     * 根据会话ID查找会话
     * @param sessionId
     * @return
     */
    Optional<AgentSessionRecord> findSession(String sessionId);

    /**
     * 创建会话
     * @param record
     */
    void createSession(AgentSessionRecord record);

    /**
     * 更新会话
     * @param record
     */
    void updateSession(AgentSessionRecord record);

    /**
     * 关闭会话
     * @param sessionId
     */
    void closeSession(String sessionId);

    /**
     * 触发增量摘要
     * @param sessionId
     */
    void triggerSummary(String sessionId);
}
