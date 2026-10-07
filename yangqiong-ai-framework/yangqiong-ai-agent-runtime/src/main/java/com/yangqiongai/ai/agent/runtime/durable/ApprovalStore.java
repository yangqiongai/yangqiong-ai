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
 * 审批存储
 * <p>
 * 持久执行SPI：保存审批记录供跨节点查询与人工审批后恢复。
 * </p>
 * @author yangqiong
 */
public interface ApprovalStore {

    /**
     * 创建审批记录
     * @param record
     * @return
     */
    ApprovalRecord create(ApprovalRecord record);

    /**
     * 按审批ID查询
     * @param approvalId
     * @return
     */
    Optional<ApprovalRecord> findByApprovalId(String approvalId);

    /**
     * 按工具调用ID查询
     * @param toolCallId
     * @return
     */
    Optional<ApprovalRecord> findByToolCallId(String toolCallId);

    /**
     * 审批落定（批准或拒绝）
     * @param approvalId
     * @param approved
     * @param reason
     * @return
     */
    ApprovalRecord resolve(String approvalId, boolean approved, String reason);

    /**
     * 查询待审批记录
     * @param scopeId
     * @return
     */
    List<ApprovalRecord> findPending(String scopeId);

    /**
     * 按运行ID查询审批记录
     * @param runId
     * @return
     */
    List<ApprovalRecord> findByRunId(String runId);
}
