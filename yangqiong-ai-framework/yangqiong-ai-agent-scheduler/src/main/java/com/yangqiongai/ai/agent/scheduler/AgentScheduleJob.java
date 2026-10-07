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
import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.scheduler.model.ScheduleLogInfo;
import com.yangqiongai.ai.agent.scheduler.model.SchedulerInfo;
import com.yangqiongai.ai.agent.scheduler.repository.ScheduleLogRepository;
import com.yangqiongai.ai.agent.scheduler.repository.SchedulerRepository;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent调度任务
 * <p>
 * Quartz Job实现，触发时从数据库加载调度配置，
 * 构造AgentRequest并调用AgentEngine执行。
 * 由于Quartz通过自身工厂创建Job实例，不经过Spring容器，
 * 因此通过静态ApplicationContext手动获取Spring Bean。
 * </p>
 * @author yangqiong
 */
public class AgentScheduleJob implements Job {

    private static final Logger log = LoggerFactory.getLogger(AgentScheduleJob.class);

    /**
     * Spring应用上下文（由SchedulerAutoConfiguration注入）
     */
    private static ApplicationContext applicationContext;

    /**
     * 注入Spring应用上下文
     * @param ctx
     */
    public static void setApplicationContext(ApplicationContext ctx) {
        applicationContext = ctx;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        if (applicationContext == null) {
            log.error("ApplicationContext未注入，调度任务无法执行");
            return;
        }
        JobDataMap dataMap = context.getMergedJobDataMap();
        String scheduleId = dataMap.getString("scheduleId");

        SchedulerRepository schedulerRepository = applicationContext.getBean(SchedulerRepository.class);
        ObjectMapper objectMapper = applicationContext.getBean(ObjectMapper.class);

        SchedulerInfo info = schedulerRepository.findByScheduleId(scheduleId);
        if (info == null) {
            log.warn("调度任务不存在，跳过执行: scheduleId={}", scheduleId);
            return;
        }
        if (Boolean.FALSE.equals(info.getEnabled())) {
            log.info("调度任务已禁用，跳过执行: scheduleId={}", scheduleId);
            return;
        }

        log.info("触发调度任务: scheduleId={}, agentCode={}, userId={}",
                scheduleId, info.getAgentCode(), info.getUserId());
        long start = System.currentTimeMillis();
        try {
            AgentEngine agentEngine = applicationContext.getBean(AgentEngine.class);
            AgentRequest request = buildRequest(objectMapper, info);
            AgentResult result = agentEngine.run(request);
            long durationMs = System.currentTimeMillis() - start;
            log.info("调度任务执行完成: scheduleId={}, success={}", scheduleId, result.isSuccess());
            saveLog(info, result, durationMs);
        } catch (Exception e) {
            log.error("调度任务执行失败: scheduleId={}", scheduleId, e);
            saveFailureLog(info, e.getMessage(), System.currentTimeMillis() - start);
        } finally {
            updateFireTime(schedulerRepository, info);
        }
    }

    /**
     * 记录成功执行历史（含token消耗）
     * @param info
     * @param result
     * @param durationMs
     */
    private void saveLog(SchedulerInfo info, AgentResult result, long durationMs) {
        ScheduleLogInfo record = buildLog(info, result.isSuccess(), result.getOutputAsText(), null, durationMs);
        if (result.getTokenMetrics() != null) {
            record.setInputTokens(result.getTokenMetrics().getInputTokens());
            record.setOutputTokens(result.getTokenMetrics().getOutputTokens());
            record.setTotalTokens(result.getTokenMetrics().getTotalTokens());
        }
        saveLogQuietly(record);
        if (!result.isSuccess()) {
            disableIfConsecutiveFailures(info);
        }
    }

    /**
     * 记录失败执行历史并检查连续失败
     * @param info
     * @param error
     * @param durationMs
     */
    private void saveFailureLog(SchedulerInfo info, String error, long durationMs) {
        saveLogQuietly(buildLog(info, false, null, error, durationMs));
        disableIfConsecutiveFailures(info);
    }

    /**
     * 记录执行历史（写入失败不影响主流程）
     * @param record
     */
    private void saveLogQuietly(ScheduleLogInfo record) {
        try {
            ScheduleLogRepository logRepository = applicationContext.getBean(ScheduleLogRepository.class);
            record.setFireTime(LocalDateTime.now());
            logRepository.save(record);
        } catch (Exception e) {
            log.warn("记录调度执行历史失败: scheduleId={}", record.getScheduleId(), e);
        }
    }

    /**
     * 构造执行历史记录
     * @param info
     * @param success
     * @param output
     * @param error
     * @param durationMs
     * @return
     */
    private ScheduleLogInfo buildLog(SchedulerInfo info, boolean success, String output, String error, long durationMs) {
        ScheduleLogInfo record = new ScheduleLogInfo();
        record.setScheduleId(info.getScheduleId());
        record.setAgentCode(info.getAgentCode());
        record.setUserId(info.getUserId());
        record.setInputText(info.getInputText());
        record.setOutputText(output);
        record.setSuccess(success);
        record.setErrorMessage(error);
        record.setDurationMs(durationMs);
        return record;
    }

    /**
     * 连续失败达到阈值时自动暂停任务，防止无人值守场景持续消耗模型资源
     * @param info
     */
    private void disableIfConsecutiveFailures(SchedulerInfo info) {
        final int failureThreshold = 3;
        try {
            ScheduleLogRepository logRepository = applicationContext.getBean(ScheduleLogRepository.class);
            List<ScheduleLogInfo> recent = logRepository.findRecentByScheduleId(info.getScheduleId(), failureThreshold);
            boolean allFailed = recent.size() >= failureThreshold
                    && recent.stream().allMatch(r -> !Boolean.TRUE.equals(r.getSuccess()));
            if (!allFailed) {
                return;
            }
            info.setEnabled(false);
            applicationContext.getBean(SchedulerRepository.class).updateById(info);
            // 同步暂停Quartz触发器，避免禁用后继续空触发
            applicationContext.getBean(UserSchedulerService.class).pauseQuartzJob(info.getScheduleId());
            log.warn("调度任务连续{}次执行失败，已自动暂停，请排查后重新启用: scheduleId={}, 最近错误={}",
                    failureThreshold, info.getScheduleId(),
                    recent.get(0).getErrorMessage());
        } catch (Exception e) {
            log.warn("连续失败检查异常: scheduleId={}", info.getScheduleId(), e);
        }
    }

    /**
     * 构造Agent请求
     * @param objectMapper
     * @param info
     * @return
     */
    @SuppressWarnings("unchecked")
    private AgentRequest buildRequest(ObjectMapper objectMapper, SchedulerInfo info) {
        AgentRequest request = new AgentRequest()
                .agentCode(info.getAgentCode())
                .userId(info.getUserId())
                .input(info.getInputText());

        if (info.getBody() != null && !info.getBody().isBlank()) {
            try {
                Map<String, Object> body = objectMapper.readValue(info.getBody(), Map.class);
                request.setBody(body);
            } catch (Exception e) {
                log.warn("解析body失败，使用空Map: scheduleId={}", info.getScheduleId());
                request.setBody(new HashMap<>());
            }
        }
        return request;
    }

    /**
     * 更新触发时间
     * @param schedulerRepository
     * @param info
     */
    private void updateFireTime(SchedulerRepository schedulerRepository, SchedulerInfo info) {
        info.setLastFireTime(LocalDateTime.now());
        schedulerRepository.updateById(info);
    }
}
