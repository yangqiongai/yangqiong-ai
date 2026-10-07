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
package com.yangqiongai.ai.agent.scheduler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.scheduler.model.ScheduleLogInfo;
import com.yangqiongai.ai.agent.scheduler.model.SchedulerInfo;
import com.yangqiongai.ai.agent.scheduler.repository.ScheduleLogRepository;
import com.yangqiongai.ai.agent.scheduler.repository.SchedulerRepository;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 用户级调度服务
 * <p>
 * 基于Quartz实现的用户级cron调度，支持创建、更新、删除、暂停和恢复调度任务。
 * 调度触发时自动构造AgentRequest并调用AgentEngine执行。
 * 存在AgentSchedulerProvider实现（如集群模块）时使用其调度器，否则自建内存调度器。
 * </p>
 * @author yangqiong
 */
public class UserSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(UserSchedulerService.class);

    @Autowired
    private SchedulerRepository schedulerRepository;

    @Autowired
    private ScheduleLogRepository scheduleLogRepository;

    @Autowired
    private SchedulerProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectProvider<AgentSchedulerProvider> schedulerProvider;

    private Scheduler scheduler;

    /**
     * 调度器是否为本服务自建（自建时负责启停，外部提供时生命周期交由提供方管理）
     */
    private boolean selfManagedScheduler;

    @PostConstruct
    public void init() throws SchedulerException {
        if (!properties.isEnabled()) {
            log.info("用户调度服务已禁用");
            return;
        }
        AgentSchedulerProvider provider = schedulerProvider.getIfAvailable();
        if (provider != null) {
            selfManagedScheduler = false;
            scheduler = provider.getScheduler();
            log.info("使用外部调度器: name={}", scheduler.getSchedulerName());
        } else {
            selfManagedScheduler = true;
            SchedulerFactory schedulerFactory = new StdSchedulerFactory();
            scheduler = schedulerFactory.getScheduler();
        }
        if (properties.isAutoStart()) {
            if (selfManagedScheduler) {
                scheduler.start();
                log.info("用户调度服务已启动: instanceName={}", properties.getInstanceName());
            }
            // 内存JobStore重启后任务丢失需从数据库恢复；JDBC JobStore中任务已持久化，恢复时做状态对齐
            restoreSchedules();
        }
    }

    /**
     * 恢复启用状态的调度任务到Quartz
     */
    private void restoreSchedules() {
        List<SchedulerInfo> schedules = schedulerRepository.listAll();
        int restored = 0;
        for (SchedulerInfo info : schedules) {
            if (Boolean.FALSE.equals(info.getEnabled())) {
                continue;
            }
            try {
                // JDBC JobStore中任务已持久化：存在则恢复触发（对齐暂停态），缺失才注册
                if (scheduler.checkExists(JobKey.jobKey(info.getScheduleId()))) {
                    scheduler.resumeJob(JobKey.jobKey(info.getScheduleId()));
                } else {
                    scheduleQuartzJob(info);
                }
                restored++;
            } catch (Exception e) {
                log.error("恢复调度任务失败: scheduleId={}", info.getScheduleId(), e);
            }
        }
        log.info("调度任务恢复完成: 启用中{}个, 已恢复{}个", schedules.size(), restored);
    }

    @PreDestroy
    public void destroy() throws SchedulerException {
        if (selfManagedScheduler && scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown(true);
            log.info("用户调度服务已关闭");
        }
    }

    /**
     * 创建调度任务
     * @param userId
     * @param taskName
     * @param agentCode
     * @param cronExpression
     * @param inputText
     * @param description
     * @param body
     * @return
     */
    public String createSchedule(String userId, String taskName, String agentCode,
                                  String cronExpression, String inputText, String description,
                                  Map<String, Object> body) {
        validateCronExpression(cronExpression);
        String scheduleId = UUID.randomUUID().toString().replace("-", "");

        SchedulerInfo info = new SchedulerInfo();
        info.setScheduleId(scheduleId);
        info.setUserId(userId);
        info.setTaskName(taskName);
        info.setAgentCode(agentCode);
        info.setCronExpression(cronExpression);
        info.setInputText(inputText);
        info.setDescription(description);
        info.setBody(serializeBody(body));
        info.setEnabled(true);
        schedulerRepository.save(info);

        scheduleQuartzJob(info);
        log.info("创建调度任务: scheduleId={}, userId={}, taskName={}, agentCode={}, cron={}",
                scheduleId, userId, taskName, agentCode, cronExpression);
        return scheduleId;
    }

    /**
     * 更新调度任务
     * @param scheduleId
     * @param taskName
     * @param cronExpression
     * @param inputText
     * @param description
     * @param body
     */
    public void updateSchedule(String scheduleId, String taskName, String cronExpression,
                                String inputText, String description, Map<String, Object> body) {
        SchedulerInfo info = schedulerRepository.findByScheduleId(scheduleId);
        if (info == null) {
            throw new IllegalArgumentException("调度任务不存在: " + scheduleId);
        }
        if (taskName != null) {
            info.setTaskName(taskName);
        }
        if (cronExpression != null && !cronExpression.isBlank()) {
            validateCronExpression(cronExpression);
            info.setCronExpression(cronExpression);
        }
        if (inputText != null) {
            info.setInputText(inputText);
        }
        if (description != null) {
            info.setDescription(description);
        }
        if (body != null) {
            info.setBody(serializeBody(body));
        }
        info.setUpdateTime(LocalDateTime.now());
        schedulerRepository.updateById(info);

        rescheduleQuartzJob(info);
        log.info("更新调度任务: scheduleId={}", scheduleId);
    }

    /**
     * 删除调度任务
     * @param scheduleId
     */
    public void deleteSchedule(String scheduleId) {
        SchedulerInfo info = schedulerRepository.findByScheduleId(scheduleId);
        if (info == null) {
            return;
        }
        unscheduleQuartzJob(scheduleId);
        schedulerRepository.deleteById(info.getId());
        log.info("删除调度任务: scheduleId={}", scheduleId);
    }

    /**
     * 暂停调度任务
     * @param scheduleId
     */
    public void pauseSchedule(String scheduleId) {
        SchedulerInfo info = schedulerRepository.findByScheduleId(scheduleId);
        if (info == null) {
            throw new IllegalArgumentException("调度任务不存在: " + scheduleId);
        }
        try {
            scheduler.pauseJob(JobKey.jobKey(scheduleId));
        } catch (SchedulerException e) {
            log.error("暂停调度任务失败: scheduleId={}", scheduleId, e);
            throw new IllegalStateException("暂停调度任务失败，请重试", e);
        }
        info.setEnabled(false);
        info.setUpdateTime(LocalDateTime.now());
        schedulerRepository.updateById(info);
        log.info("暂停调度任务: scheduleId={}", scheduleId);
    }

    /**
     * 恢复调度任务
     * @param scheduleId
     */
    public void resumeSchedule(String scheduleId) {
        SchedulerInfo info = schedulerRepository.findByScheduleId(scheduleId);
        if (info == null) {
            throw new IllegalArgumentException("调度任务不存在: " + scheduleId);
        }
        try {
            JobKey jobKey = JobKey.jobKey(scheduleId);
            // 内存JobStore重启后丢失（禁用态任务启动时不恢复），任务缺失时需重新注册
            if (!scheduler.checkExists(jobKey)) {
                scheduleQuartzJob(info);
            } else {
                scheduler.resumeJob(jobKey);
            }
        } catch (Exception e) {
            log.error("恢复调度任务失败: scheduleId={}", scheduleId, e);
            throw new IllegalStateException("恢复调度任务失败，请重试", e);
        }
        info.setEnabled(true);
        info.setUpdateTime(LocalDateTime.now());
        schedulerRepository.updateById(info);
        log.info("恢复调度任务: scheduleId={}", scheduleId);
    }

    /**
     * 仅暂停Quartz触发器（不更新业务表），供连续失败自动暂停时同步停触发
     * @param scheduleId
     */
    public void pauseQuartzJob(String scheduleId) {
        try {
            scheduler.pauseJob(JobKey.jobKey(scheduleId));
        } catch (Exception e) {
            log.error("暂停Quartz触发器失败: scheduleId={}", scheduleId, e);
        }
    }

    /**
     * 查询用户调度列表
     * @param userId
     * @return
     */
    public List<SchedulerInfo> listSchedules(String userId) {
        return schedulerRepository.listByUserId(userId);
    }

    /**
     * 分页查询调度执行历史
     * @param scheduleId
     * @param pageNum
     * @param pageSize
     * @return
     */
    public Map<String, Object> pageLogs(String scheduleId, int pageNum, int pageSize) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Map<String, Object> page = new HashMap<>();
        page.put("total", scheduleLogRepository.countByScheduleId(scheduleId));
        List<ScheduleLogInfo> logs = scheduleLogRepository.pageByScheduleId(scheduleId, safePageNum, safePageSize);
        page.put("list", logs);
        return page;
    }

    /**
     * 注册Quartz任务
     */
    private void scheduleQuartzJob(SchedulerInfo info) {
        try {
            JobDetail job = JobBuilder.newJob(AgentScheduleJob.class)
                    .withIdentity(info.getScheduleId())
                    .usingJobData("scheduleId", info.getScheduleId())
                    .storeDurably()
                    .build();
            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger_" + info.getScheduleId())
                    .withSchedule(CronScheduleBuilder.cronSchedule(info.getCronExpression()))
                    .build();
            scheduler.scheduleJob(job, trigger);
        } catch (SchedulerException e) {
            log.error("注册Quartz任务失败: scheduleId={}", info.getScheduleId(), e);
            throw new RuntimeException("注册调度任务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 重新调度Quartz任务
     */
    private void rescheduleQuartzJob(SchedulerInfo info) {
        try {
            TriggerKey triggerKey = TriggerKey.triggerKey("trigger_" + info.getScheduleId());
            Trigger newTrigger = TriggerBuilder.newTrigger()
                    .withIdentity(triggerKey)
                    .withSchedule(CronScheduleBuilder.cronSchedule(info.getCronExpression()))
                    .build();
            scheduler.rescheduleJob(triggerKey, newTrigger);
        } catch (SchedulerException e) {
            log.error("重新调度任务失败: scheduleId={}", info.getScheduleId(), e);
        }
    }

    /**
     * 取消Quartz任务
     */
    private void unscheduleQuartzJob(String scheduleId) {
        try {
            scheduler.deleteJob(JobKey.jobKey(scheduleId));
        } catch (SchedulerException e) {
            log.error("取消Quartz任务失败: scheduleId={}", scheduleId, e);
        }
    }

    /**
     * 验证Cron表达式
     */
    private void validateCronExpression(String cron) {
        if (cron == null || cron.isBlank()) {
            throw new IllegalArgumentException("Cron表达式不能为空");
        }
        try {
            CronScheduleBuilder.cronSchedule(cron);
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的Cron表达式: " + cron, e);
        }
    }

    /**
     * 序列化body为JSON
     */
    private String serializeBody(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(body);
        } catch (Exception e) {
            log.warn("序列化body失败", e);
            return null;
        }
    }
}
