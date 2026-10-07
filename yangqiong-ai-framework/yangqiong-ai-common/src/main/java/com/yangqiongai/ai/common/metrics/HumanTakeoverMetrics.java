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
package com.yangqiongai.ai.common.metrics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * 人工接管率统计
 * <p>
 * 统计 HumanApprovalTool 被调用次数和审批结果，用于计算"人工接管率"指标。
 * 人工接管率 = 触发审批的任务数 / 总任务数，该指标反映Agent自主决策与人工介入的平衡。
 * </p>
 * @author yangqiong
 */
@Service
public class HumanTakeoverMetrics {

    private static final Logger log = LoggerFactory.getLogger(HumanTakeoverMetrics.class);

    /**
     * 审批请求总数
     */
    private final AtomicInteger approvalRequested = new AtomicInteger(0);

    /**
     * 审批通过数
     */
    private final AtomicInteger approvalApproved = new AtomicInteger(0);

    /**
     * 审批拒绝数
     */
    private final AtomicInteger approvalRejected = new AtomicInteger(0);

    /**
     * 审批超时数
     */
    private final AtomicInteger approvalTimeout = new AtomicInteger(0);

    /**
     * 记录审批请求
     * @param sessionId
     * @param reason
     */
    public void recordApprovalRequest(String sessionId, String reason) {
        approvalRequested.incrementAndGet();
        log.debug("记录审批请求: sessionId={}, reason={}", sessionId, reason);
    }

    /**
     * 记录审批结果
     * @param approved
     * @param timeout
     */
    public void recordApprovalResult(boolean approved, boolean timeout) {
        if (timeout) {
            approvalTimeout.incrementAndGet();
        } else if (approved) {
            approvalApproved.incrementAndGet();
        } else {
            approvalRejected.incrementAndGet();
        }
    }

    /**
     * 计算接管率
     * @param totalTasks 总任务数
     * @return 接管率百分比（0-100），totalTasks为0时返回0
     */
    public double calculateTakeoverRate(int totalTasks) {
        if (totalTasks <= 0) {
            return 0.0;
        }
        return (double) approvalRequested.get() / totalTasks * 100.0;
    }

    /**
     * 获取审批请求总数
     * @return
     */
    public int getApprovalRequested() {
        return approvalRequested.get();
    }

    /**
     * 获取审批通过数
     * @return
     */
    public int getApprovalApproved() {
        return approvalApproved.get();
    }

    /**
     * 获取审批拒绝数
     * @return
     */
    public int getApprovalRejected() {
        return approvalRejected.get();
    }

    /**
     * 获取审批超时数
     * @return
     */
    public int getApprovalTimeout() {
        return approvalTimeout.get();
    }

    /**
     * 重置所有计数器（测试用）
     */
    public void reset() {
        approvalRequested.set(0);
        approvalApproved.set(0);
        approvalRejected.set(0);
        approvalTimeout.set(0);
    }
}