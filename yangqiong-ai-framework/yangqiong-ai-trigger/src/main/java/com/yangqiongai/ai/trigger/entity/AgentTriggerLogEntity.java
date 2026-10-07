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
package com.yangqiongai.ai.trigger.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * Agent触发记录
 * @author yangqiong
 */
@TableName("ai_agent_trigger_log")
public class AgentTriggerLogEntity extends ScopeEntity {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 触发规则ID
     */
    private Long triggerId;

    /**
     * 触发器编码(冗余,便于查询)
     */
    private String triggerCode;

    /**
     * 幂等键(CRON为计划时刻/事件为载荷摘要,空为无幂等约束)
     */
    private String dedupKey;

    /**
     * 触发来源(CRON_SCHEDULER/BUILTIN_EVENT/MANUAL/WEBHOOK)
     */
    private String fireSource;

    /**
     * 入队任务ID
     */
    private String taskId;

    /**
     * 触发载荷摘要(截断)
     */
    private String payloadDigest;

    private LocalDateTime createTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTriggerId() {
        return triggerId;
    }

    public void setTriggerId(Long triggerId) {
        this.triggerId = triggerId;
    }

    public String getTriggerCode() {
        return triggerCode;
    }

    public void setTriggerCode(String triggerCode) {
        this.triggerCode = triggerCode;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public void setDedupKey(String dedupKey) {
        this.dedupKey = dedupKey;
    }

    public String getFireSource() {
        return fireSource;
    }

    public void setFireSource(String fireSource) {
        this.fireSource = fireSource;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getPayloadDigest() {
        return payloadDigest;
    }

    public void setPayloadDigest(String payloadDigest) {
        this.payloadDigest = payloadDigest;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
