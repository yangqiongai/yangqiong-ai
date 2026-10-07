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
 * Agent运行记录存储
 * <p>
 * 持久执行SPI：记录运行生命周期供中断续跑、审计与状态查询。
 * </p>
 * @author yangqiong
 */
public interface AgentRunStore {

    /**
     * 创建运行记录
     * @param record
     * @return
     */
    AgentRunRecord create(AgentRunRecord record);

    /**
     * 按运行ID查询
     * @param runId
     * @return
     */
    Optional<AgentRunRecord> findByRunId(String runId);

    /**
     * 按会话查询最近一次运行
     * @param scopeId
     * @param sessionId
     * @return
     */
    Optional<AgentRunRecord> findLatestBySession(String scopeId, String sessionId);

    /**
     * 按会话查询全部运行
     * @param scopeId
     * @param sessionId
     * @return
     */
    List<AgentRunRecord> findBySession(String scopeId, String sessionId);

    /**
     * 保存状态迁移
     * @param record
     * @return
     */
    AgentRunRecord saveTransition(AgentRunRecord record);

    /**
     * 查询等待审批的运行
     * @param scopeId
     * @return
     */
    List<AgentRunRecord> findWaitingApproval(String scopeId);
}
