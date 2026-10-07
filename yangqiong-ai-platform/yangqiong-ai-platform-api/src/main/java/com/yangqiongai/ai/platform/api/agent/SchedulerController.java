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
package com.yangqiongai.ai.platform.api.agent;

import com.yangqiongai.ai.agent.scheduler.model.SchedulerInfo;
import com.yangqiongai.ai.agent.scheduler.UserSchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 调度管理
 * @author yangqiong
 */
@RestController
@RequestMapping("/api/schedule")
public class SchedulerController {

    @Autowired
    private UserSchedulerService schedulerService;

    /**
     * 创建调度任务
     * @param request
     * @return
     */
    @PostMapping
    public String createSchedule(@RequestBody CreateScheduleRequest request) {
        // 归属用户由前端取当前登录人传入，缺失说明登录态异常，拒绝创建避免任务归属错乱
        if (request.getUserId() == null || request.getUserId().isEmpty()) {
            throw new IllegalArgumentException("登录状态已过期，请重新登录后再创建调度任务");
        }
        return schedulerService.createSchedule(
                request.getUserId(),
                request.getTaskName(),
                request.getAgentCode(),
                request.getCronExpression(),
                request.getInputText(),
                request.getDescription(),
                request.getBody()
        );
    }

    /**
     * 更新调度任务
     * @param scheduleId
     * @param request
     */
    @PutMapping("/{scheduleId}")
    public void updateSchedule(@PathVariable String scheduleId, @RequestBody UpdateScheduleRequest request) {
        schedulerService.updateSchedule(
                scheduleId,
                request.getTaskName(),
                request.getCronExpression(),
                request.getInputText(),
                request.getDescription(),
                request.getBody()
        );
    }

    /**
     * 删除调度任务
     * @param scheduleId
     */
    @DeleteMapping("/{scheduleId}")
    public void deleteSchedule(@PathVariable String scheduleId) {
        schedulerService.deleteSchedule(scheduleId);
    }

    /**
     * 暂停调度任务
     * @param scheduleId
     */
    @PostMapping("/{scheduleId}/pause")
    public void pauseSchedule(@PathVariable String scheduleId) {
        schedulerService.pauseSchedule(scheduleId);
    }

    /**
     * 恢复调度任务
     * @param scheduleId
     */
    @PostMapping("/{scheduleId}/resume")
    public void resumeSchedule(@PathVariable String scheduleId) {
        schedulerService.resumeSchedule(scheduleId);
    }

    /**
     * 查询用户调度列表
     * @param userId
     * @return
     */
    @GetMapping
    public List<SchedulerInfo> listSchedules(@RequestParam String userId) {
        return schedulerService.listSchedules(userId);
    }

    /**
     * 分页查询调度执行历史
     * @param scheduleId
     * @param pageNum
     * @param pageSize
     * @return
     */
    @GetMapping("/{scheduleId}/logs")
    public Map<String, Object> pageLogs(@PathVariable String scheduleId,
                                        @RequestParam(defaultValue = "1") int pageNum,
                                        @RequestParam(defaultValue = "10") int pageSize) {
        return schedulerService.pageLogs(scheduleId, pageNum, pageSize);
    }

    /**
     * 创建调度请求
     */
    public static class CreateScheduleRequest {

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
         * 请求体参数
         */
        private Map<String, Object> body;

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

        public Map<String, Object> getBody() {
            return body;
        }

        public void setBody(Map<String, Object> body) {
            this.body = body;
        }
    }

    /**
     * 更新调度请求
     */
    public static class UpdateScheduleRequest {

        /**
         * 任务名称
         */
        private String taskName;

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
         * 请求体参数
         */
        private Map<String, Object> body;

        public String getTaskName() {
            return taskName;
        }

        public void setTaskName(String taskName) {
            this.taskName = taskName;
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

        public Map<String, Object> getBody() {
            return body;
        }

        public void setBody(Map<String, Object> body) {
            this.body = body;
        }
    }
}
