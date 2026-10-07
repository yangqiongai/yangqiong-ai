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
package com.yangqiongai.ai.platform.ecosystem.a2a;

/**
 * A2A任务状态映射
 * <p>
 * 平台任务状态 → A2A协议状态（QUEUED→submitted、RUNNING→working、
 * PAUSED→input-required、终态→completed/failed/canceled）。
 * </p>
 * @author yangqiong
 */
public final class A2aStateMapper {

    /**
     * A2A任务状态(以字符串承载,避免强绑spec枚举序列化)
     */
    public enum A2aTaskState {
        SUBMITTED("submitted"),
        WORKING("working"),
        INPUT_REQUIRED("input-required"),
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELED("canceled");

        private final String wire;

        A2aTaskState(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    private A2aStateMapper() {
    }

    /**
     * 平台任务状态映射为A2A状态,未知状态回退working
     * @param platformStatus
     * @return
     */
    public static A2aTaskState map(String platformStatus) {
        if (platformStatus == null) {
            return A2aTaskState.WORKING;
        }
        return switch (platformStatus) {
            case "QUEUED" -> A2aTaskState.SUBMITTED;
            case "RUNNING" -> A2aTaskState.WORKING;
            case "PAUSED" -> A2aTaskState.INPUT_REQUIRED;
            case "SUCCEEDED" -> A2aTaskState.COMPLETED;
            case "FAILED" -> A2aTaskState.FAILED;
            case "CANCELED" -> A2aTaskState.CANCELED;
            default -> A2aTaskState.WORKING;
        };
    }
}
