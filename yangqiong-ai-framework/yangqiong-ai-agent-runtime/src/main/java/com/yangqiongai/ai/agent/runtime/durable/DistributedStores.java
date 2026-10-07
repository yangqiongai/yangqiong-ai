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

import com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory;
import com.yangqiongai.ai.agent.runtime.memory.AgentSessionMemory;

/**
 * 分布式共享存储集合
 * <p>
 * 一次性注入运行时所需的全部共享存储，单项setter注入优先于本聚合。
 * </p>
 * @author yangqiong
 */
public interface DistributedStores {

    /**
     * 获取运行记录存储
     * @return
     */
    AgentRunStore runStore();

    /**
     * 获取检查点存储
     * @return
     */
    CheckpointStore checkpointStore();

    /**
     * 获取审批存储
     * @return
     */
    ApprovalStore approvalStore();

    /**
     * 获取会话级短期记忆
     * @return
     */
    AgentSessionMemory sessionMemory();

    /**
     * 获取长期记忆
     * @return
     */
    AgentLongTermMemory longTermMemory();

    /**
     * 获取工具执行记录存储
     * @return
     */
    ToolExecutionStore toolExecutionStore();

    /**
     * 获取运行锁存储
     * @return
     */
    RunLockStore runLockStore();
}
