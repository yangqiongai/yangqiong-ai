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
package com.yangqiongai.ai.trigger.model;

/**
 * Agent触发结果
 * @author yangqiong
 */
public class AgentTriggerFireResult {

    /**
     * 触发成功
     */
    public static final String FIRED = "FIRED";

    /**
     * 触发器不存在
     */
    public static final String NOT_FOUND = "NOT_FOUND";

    /**
     * 触发器已停用
     */
    public static final String DISABLED = "DISABLED";

    /**
     * 命中幂等键或去重窗口拒绝
     */
    public static final String DUPLICATED = "DUPLICATED";

    /**
     * 每日配额耗尽拒绝
     */
    public static final String QUOTA_EXCEEDED = "QUOTA_EXCEEDED";

    /**
     * 触发状态
     */
    private final String status;

    /**
     * 入队任务ID(触发成功时非空)
     */
    private final String taskId;

    /**
     * 附加说明
     */
    private final String message;

    public AgentTriggerFireResult(String status, String taskId, String message) {
        this.status = status;
        this.taskId = taskId;
        this.message = message;
    }

    public static AgentTriggerFireResult fired(String taskId) {
        return new AgentTriggerFireResult(FIRED, taskId, null);
    }

    public static AgentTriggerFireResult rejected(String status, String message) {
        return new AgentTriggerFireResult(status, null, message);
    }

    public String getStatus() {
        return status;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getMessage() {
        return message;
    }

    public boolean isFired() {
        return FIRED.equals(status);
    }
}
