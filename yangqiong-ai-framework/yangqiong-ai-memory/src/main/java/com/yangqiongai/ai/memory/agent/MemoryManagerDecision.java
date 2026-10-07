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
package com.yangqiongai.ai.memory.agent;

/**
 * 记忆整理决策
 * @author yangqiong
 */
public class MemoryManagerDecision {

    /**
     * 目标记忆ID
     */
    private Long memoryId;

    /**
     * 决策动作
     */
    private MemoryManagerAction action;

    /**
     * 合并目标（仅 MERGE 动作时使用）：合并后的新内容
     */
    private String mergedContent;

    /**
     * 合并源ID列表（仅 MERGE 动作时使用）：被合并删除的记忆ID
     */
    private java.util.List<Long> mergeSourceIds;

    /**
     * 决策理由
     */
    private String reason;

    public MemoryManagerDecision() {
    }

    public MemoryManagerDecision(Long memoryId, MemoryManagerAction action, String reason) {
        this.memoryId = memoryId;
        this.action = action;
        this.reason = reason;
    }

    public Long getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(Long memoryId) {
        this.memoryId = memoryId;
    }

    public MemoryManagerAction getAction() {
        return action;
    }

    public void setAction(MemoryManagerAction action) {
        this.action = action;
    }

    public String getMergedContent() {
        return mergedContent;
    }

    public void setMergedContent(String mergedContent) {
        this.mergedContent = mergedContent;
    }

    public java.util.List<Long> getMergeSourceIds() {
        return mergeSourceIds;
    }

    public void setMergeSourceIds(java.util.List<Long> mergeSourceIds) {
        this.mergeSourceIds = mergeSourceIds;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
