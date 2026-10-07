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

import java.util.List;
import java.util.Optional;

/**
 * 检查点存储
 * <p>
 * 持久执行SPI：保存每轮迭代的完整快照，供中断恢复与续跑。
 * </p>
 * @author yangqiong
 */
public interface CheckpointStore {

    /**
     * 保存检查点
     * @param checkpoint
     */
    void save(AgentCheckpoint checkpoint);

    /**
     * 查询会话最新检查点
     * @param scopeId
     * @param sessionId
     * @return
     */
    Optional<AgentCheckpoint> latest(String scopeId, String sessionId);

    /**
     * 按运行ID查询检查点
     * @param runId
     * @return
     */
    List<AgentCheckpoint> findByRunId(String runId);

    /**
     * 清除指定运行的全部检查点
     * @param runId
     */
    void clear(String runId);
}
