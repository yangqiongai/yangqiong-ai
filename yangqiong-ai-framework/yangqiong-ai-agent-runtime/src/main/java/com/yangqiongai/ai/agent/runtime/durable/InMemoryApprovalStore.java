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

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审批存储内存实现
 * @author yangqiong
 */
public class InMemoryApprovalStore implements ApprovalStore {

    /**
     * 最大审批记录数，超限淘汰创建时间最旧的记录，防止内存无界增长
     */
    public static final int MAX_RECORDS = 1000;

    /**
     * 按审批ID索引
     */
    private final ConcurrentHashMap<String, ApprovalRecord> byApprovalId = new ConcurrentHashMap<>();

    /**
     * 按工具调用ID索引的最近审批
     */
    private final ConcurrentHashMap<String, ApprovalRecord> byToolCallId = new ConcurrentHashMap<>();

    @Override
    public ApprovalRecord create(ApprovalRecord record) {
        if (record == null || record.getApprovalId() == null) {
            throw new IllegalArgumentException("审批记录或approvalId不能为空");
        }
        byApprovalId.put(record.getApprovalId(), record);
        if (record.getToolCallId() != null) {
            byToolCallId.put(record.getToolCallId(), record);
        }
        evictOverflow(record);
        return record;
    }

    /**
     * 容量超限时淘汰创建时间最旧记录并保持双索引一致
     * @param protectedRecord 本次保存的记录，禁止作为淘汰对象
     */
    private void evictOverflow(ApprovalRecord protectedRecord) {
        if (byApprovalId.size() <= MAX_RECORDS) {
            return;
        }
        byApprovalId.values().stream()
                .filter(r -> r != protectedRecord)
                .min(Comparator.comparingLong(ApprovalRecord::getCreatedAt))
                .ifPresent(victim -> {
                    byApprovalId.remove(victim.getApprovalId(), victim);
                    if (victim.getToolCallId() != null) {
                        byToolCallId.remove(victim.getToolCallId(), victim);
                    }
                });
    }

    @Override
    public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
        return Optional.ofNullable(byApprovalId.get(approvalId));
    }

    @Override
    public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
        return Optional.ofNullable(byToolCallId.get(toolCallId));
    }

    @Override
    public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
        ApprovalRecord record = byApprovalId.get(approvalId);
        if (record == null) {
            throw new IllegalStateException("审批记录不存在: " + approvalId);
        }
        if (record.getState() != ApprovalRecord.ApprovalState.PENDING) {
            return record;
        }
        ApprovalRecord resolved = record.resolve(approved, reason);
        byApprovalId.put(approvalId, resolved);
        if (resolved.getToolCallId() != null) {
            byToolCallId.put(resolved.getToolCallId(), resolved);
        }
        return resolved;
    }

    @Override
    public List<ApprovalRecord> findPending(String scopeId) {
        return byApprovalId.values().stream()
                .filter(r -> r.getState() == ApprovalRecord.ApprovalState.PENDING)
                .filter(r -> scopeId == null || scopeId.equals(r.getScopeId()))
                .toList();
    }

    @Override
    public List<ApprovalRecord> findByRunId(String runId) {
        if (runId == null) {
            return List.of();
        }
        return byApprovalId.values().stream()
                .filter(r -> runId.equals(r.getRunId()))
                .toList();
    }
}
