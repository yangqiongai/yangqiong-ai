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
package com.yangqiongai.ai.trigger.event;

/**
 * Agent触发执行事件（企业侧监听后写审计链闭环）
 * @author yangqiong
 */
public class AgentTriggerFiredEvent {

    /**
     * 触发器编码
     */
    private final String triggerCode;

    /**
     * 目标Agent编码
     */
    private final String agentCode;

    /**
     * 触发来源(CRON_SCHEDULER/BUILTIN_EVENT/MANUAL/WEBHOOK)
     */
    private final String fireSource;

    /**
     * 入队任务ID
     */
    private final String taskId;

    /**
     * 触发载荷摘要
     */
    private final String payload;

    public AgentTriggerFiredEvent(String triggerCode, String agentCode, String fireSource,
                                  String taskId, String payload) {
        this.triggerCode = triggerCode;
        this.agentCode = agentCode;
        this.fireSource = fireSource;
        this.taskId = taskId;
        this.payload = payload;
    }

    public String getTriggerCode() {
        return triggerCode;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getFireSource() {
        return fireSource;
    }

    public String getTaskId() {
        return taskId;
    }

    public String getPayload() {
        return payload;
    }
}
