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
package com.yangqiongai.ai.agent.runtime.durable;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

import java.util.List;

/**
 * 检查点管理器
 * <p>
 * 会话粒度的轻量检查点管理SPI：每轮迭代保存消息现场，超限LRU淘汰防止内存膨胀。
 * 引擎内置内存实现，可替换为共享存储实现。
 * </p>
 * @author yangqiong
 */
public interface CheckpointManager {

    /**
     * 保存会话检查点
     * @param sessionId
     * @param messages
     * @param iteration
     */
    void save(String sessionId, List<AgentMessage> messages, int iteration);

    /**
     * 恢复会话消息现场
     * @param sessionId
     * @return
     */
    List<AgentMessage> restore(String sessionId);

    /**
     * 获取检查点时的迭代轮次
     * @param sessionId
     * @return
     */
    int restoreIteration(String sessionId);

    /**
     * 清除会话检查点
     * @param sessionId
     */
    void clear(String sessionId);

    /**
     * 判断会话是否存在检查点
     * @param sessionId
     * @return
     */
    boolean hasCheckpoint(String sessionId);
}
