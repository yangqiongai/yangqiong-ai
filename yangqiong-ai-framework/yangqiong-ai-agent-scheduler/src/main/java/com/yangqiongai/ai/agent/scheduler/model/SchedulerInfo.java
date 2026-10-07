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
package com.yangqiongai.ai.agent.scheduler.model;

import java.time.LocalDateTime;

/**
 * Agent定时调度
 * @author yangqiong
 */
public class SchedulerInfo {

    /**
     * 主键
     */
    private Long id;

    /**
     * 调度ID
     */
    private String scheduleId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 任务名称
     */
    private String taskName;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * Cron表达式
     */
    private String cronExpression;

    /**
     * 输入文本
     */
    private String inputText;

    /**
     * 任务描述
     */
    private String description;

    /**
     * 请求体参数（JSON）
     */
    private String body;

    /**
     * 是否启用
     */
    private Boolean enabled;

    /**
     * 下次触发时间
     */
    private LocalDateTime nextFireTime;

    /**
     * 上次触发时间
     */
    private LocalDateTime lastFireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(String scheduleId) {
        this.scheduleId = scheduleId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTaskName() {
        return taskName;
    }

    public void setTaskName(String taskName) {
        this.taskName = taskName;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getInputText() {
        return inputText;
    }

    public void setInputText(String inputText) {
        this.inputText = inputText;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getNextFireTime() {
        return nextFireTime;
    }

    public void setNextFireTime(LocalDateTime nextFireTime) {
        this.nextFireTime = nextFireTime;
    }

    public LocalDateTime getLastFireTime() {
        return lastFireTime;
    }

    public void setLastFireTime(LocalDateTime lastFireTime) {
        this.lastFireTime = lastFireTime;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
