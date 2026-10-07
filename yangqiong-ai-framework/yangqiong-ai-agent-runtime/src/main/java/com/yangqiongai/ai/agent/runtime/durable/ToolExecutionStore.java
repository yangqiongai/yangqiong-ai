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

import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;

/**
 * 工具执行记录存储
 * <p>
 * 以幂等键去重工具调用：同键首次结果落地后，重复调用直接复用，
 * 用于恢复场景副作用零重复执行。外部可替换为共享存储实现实现跨节点去重。
 * </p>
 * @author yangqiong
 */
public interface ToolExecutionStore {

    /**
     * 记录幂等键已完成
     * @param idempotencyKey
     * @param result
     */
    void record(String idempotencyKey, AgentToolResultBlock result);

    /**
     * 查询幂等键是否已完成
     * @param idempotencyKey
     * @return
     */
    boolean isCompleted(String idempotencyKey);

    /**
     * 取幂等键的既有结果
     * @param idempotencyKey
     * @return
     */
    AgentToolResultBlock getResult(String idempotencyKey);
}
