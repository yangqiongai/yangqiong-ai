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
package com.yangqiongai.ai.agent.core.task;

import java.time.LocalDateTime;

/**
 * 任务事件日志
 * @author yangqiong
 */
public class TaskEventLog {

    /**
     * 事件类型
     */
    private String eventType;

    /**
     * 步骤编码
     */
    private String stepCode;

    /**
     * 消息
     */
    private String message;

    /**
     * 载荷
     */
    private String payload;

    /**
     * 时间戳
     */
    private LocalDateTime timestamp;

    public TaskEventLog() {
        this.timestamp = LocalDateTime.now();
    }

    public TaskEventLog(String eventType, String stepCode, String message, String payload, LocalDateTime timestamp) {
        this.eventType = eventType;
        this.stepCode = stepCode;
        this.message = message;
        this.payload = payload;
        this.timestamp = timestamp;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getStepCode() {
        return stepCode;
    }

    public void setStepCode(String stepCode) {
        this.stepCode = stepCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }
}
