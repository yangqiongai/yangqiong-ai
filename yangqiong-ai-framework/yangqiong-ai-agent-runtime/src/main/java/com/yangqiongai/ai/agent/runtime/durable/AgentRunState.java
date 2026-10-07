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

/**
 * Agent运行状态
 * <p>
 * 运行生命周期状态机：CREATED→RUNNING→WAITING_APPROVAL→SUCCEEDED/FAILED/CANCELLED，
 * 终态不可再迁移，非法迁移由{@link AgentRunRecord#transitionTo}拒绝。
 * </p>
 * @author yangqiong
 */
public enum AgentRunState {

    /**
     * 已创建未启动
     */
    CREATED,

    /**
     * 运行中
     */
    RUNNING,

    /**
     * 等待人工审批
     */
    WAITING_APPROVAL,

    /**
     * 已成功（终态）
     */
    SUCCEEDED,

    /**
     * 已失败（终态）
     */
    FAILED,

    /**
     * 已取消（终态）
     */
    CANCELLED;

    /**
     * 判断是否为终态
     * @return true时不可再迁移
     */
    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    /**
     * 判断能否迁移到目标状态
     * @param target 目标状态
     * @return true时允许迁移
     */
    public boolean canTransitionTo(AgentRunState target) {
        if (target == null || this == target) {
            return false;
        }
        if (isTerminal()) {
            return false;
        }
        switch (this) {
            case CREATED:
                return target == RUNNING || target == CANCELLED;
            case RUNNING:
                return target == WAITING_APPROVAL || target == SUCCEEDED
                        || target == FAILED || target == CANCELLED;
            case WAITING_APPROVAL:
                return target == RUNNING || target == CANCELLED;
            default:
                return false;
        }
    }
}
