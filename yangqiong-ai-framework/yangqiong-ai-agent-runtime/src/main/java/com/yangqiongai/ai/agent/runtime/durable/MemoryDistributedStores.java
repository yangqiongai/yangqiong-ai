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
import com.yangqiongai.ai.agent.runtime.memory.InMemoryLongTermMemory;
import com.yangqiongai.ai.agent.runtime.memory.InMemorySessionMemory;

/**
 * 内存共享存储
 * <p>
 * 全部存储使用内存实现，作为分布式存储的内存兜底，
 * 适合单机演示、测试及未引入持久化扩展时的默认场景。
 * </p>
 * @author yangqiong
 */
public class MemoryDistributedStores implements DistributedStores {

    /**
     * 运行记录存储
     */
    private final AgentRunStore runStore = new InMemoryAgentRunStore();

    /**
     * 检查点存储
     */
    private final CheckpointStore checkpointStore = new InMemoryCheckpointStore();

    /**
     * 审批存储
     */
    private final ApprovalStore approvalStore = new InMemoryApprovalStore();

    /**
     * 会话记忆存储
     */
    private final AgentSessionMemory sessionMemory = new InMemorySessionMemory();

    /**
     * 长期记忆存储
     */
    private final AgentLongTermMemory longTermMemory = new InMemoryLongTermMemory();

    /**
     * 工具执行记录存储
     */
    private final ToolExecutionStore toolExecutionStore = new InMemoryToolExecutionStore();

    /**
     * 运行锁存储
     */
    private final RunLockStore runLockStore = new InMemoryRunLockStore();

    @Override
    public AgentRunStore runStore() {
        return runStore;
    }

    @Override
    public CheckpointStore checkpointStore() {
        return checkpointStore;
    }

    @Override
    public ApprovalStore approvalStore() {
        return approvalStore;
    }

    @Override
    public AgentSessionMemory sessionMemory() {
        return sessionMemory;
    }

    @Override
    public AgentLongTermMemory longTermMemory() {
        return longTermMemory;
    }

    @Override
    public ToolExecutionStore toolExecutionStore() {
        return toolExecutionStore;
    }

    @Override
    public RunLockStore runLockStore() {
        return runLockStore;
    }
}
