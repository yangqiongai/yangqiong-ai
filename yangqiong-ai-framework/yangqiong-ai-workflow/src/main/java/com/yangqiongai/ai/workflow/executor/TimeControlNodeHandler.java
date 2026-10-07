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
package com.yangqiongai.ai.workflow.executor;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.event.WorkflowTimeArrivedEvent;
import com.yangqiongai.ai.workflow.model.NodeTimeControlConfig;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.spi.WorkflowTimeModeResolver;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 时间控制节点处理
 * @author yangqiong
 */
@Service
public class TimeControlNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(TimeControlNodeHandler.class);

    private static final DateTimeFormatter RESUME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ApplicationEventPublisher eventPublisher;

    private final ObjectProvider<WorkflowTimeModeResolver> timeModeResolverProvider;

    private final ScheduledExecutorService scheduler;

    @Autowired
    public TimeControlNodeHandler(ApplicationEventPublisher eventPublisher,
                                  ObjectProvider<WorkflowTimeModeResolver> timeModeResolverProvider) {
        this.eventPublisher = eventPublisher;
        this.timeModeResolverProvider = timeModeResolverProvider;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "workflow-time-control");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    /**
     * 执行时间控制节点，计算恢复时间并调度定时恢复，返回暂停结果
     * @param context
     * @param state
     * @param node
     * @return 未到设定时间返回paused结果，已过期或未配置直接成功
     */
    public AgentResult executeTimeControlNode(AgentContext context, WorkflowState state, WorkflowNode node) {
        NodeTimeControlConfig config = node.getTimeControlConfig();
        if (config == null) {
            log.warn("TIME_CONTROL节点未配置timeControlConfig，直接通过: nodeId={}", node.getId());
            return AgentResult.success("时间控制节点未配置，直接通过");
        }
        NodeTimeControlConfig.TimeType timeType = config.getTimeType() != null
                ? config.getTimeType() : NodeTimeControlConfig.TimeType.DELAY;
        long now = System.currentTimeMillis();
        // 高级时间模式依赖企业版解析器，未注入实现时明确提示专属能力不可用
        if (isEnterpriseAdvancedMode(timeType) && timeModeResolverProvider.getIfAvailable() == null) {
            return AgentResult.failure("高级时间模式为企业版专属功能，当前版本不可用（" + describeConfig(config) + "）");
        }
        long resumeAtMillis = computeResumeAtMillis(config, now);
        if (resumeAtMillis < 0) {
            return AgentResult.failure("时间控制节点配置无效: " + describeConfig(config));
        }
        long waitMillis = resumeAtMillis - now;
        if (waitMillis <= 0) {
            log.info("时间控制节点设定时间已过，直接继续: nodeId={}, resumeAt={}",
                    node.getId(), formatTime(resumeAtMillis));
            return AgentResult.success("设定时间已过（" + formatTime(resumeAtMillis) + "），直接继续执行");
        }
        log.info("时间控制节点暂停等待: instanceId={}, nodeId={}, 模式={}, 恢复时间={}, 等待{}秒",
                state.getInstanceId(), node.getId(), config.getTimeType(),
                formatTime(resumeAtMillis), waitMillis / 1000);
        // 变量记录等待目标，供流程变量展示与启动恢复扫描读取
        state.setVariable("timeResumeAt:" + node.getId(), resumeAtMillis);
        state.setVariable("timeResumeAtText:" + node.getId(), formatTime(resumeAtMillis));
        scheduleResumeEvent(state.getInstanceId(), node.getId(), resumeAtMillis);
        String pausedRequestId = "time:" + node.getId();
        return AgentResult.paused(pausedRequestId,
                "时间控制等待至 " + formatTime(resumeAtMillis) + "（" + describeConfig(config) + "）");
    }

    /**
     * 调试预览时间控制节点，只计算恢复时间不实际调度暂停等待
     * @param node
     * @return
     */
    public AgentResult previewTimeControlNode(WorkflowNode node) {
        NodeTimeControlConfig config = node.getTimeControlConfig();
        if (config == null) {
            return AgentResult.success("时间控制节点未配置，直接通过");
        }
        NodeTimeControlConfig.TimeType timeType = config.getTimeType() != null
                ? config.getTimeType() : NodeTimeControlConfig.TimeType.DELAY;
        long now = System.currentTimeMillis();
        if (isEnterpriseAdvancedMode(timeType) && timeModeResolverProvider.getIfAvailable() == null) {
            return AgentResult.failure("高级时间模式为企业版专属功能，当前版本不可用（" + describeConfig(config) + "）");
        }
        long resumeAtMillis = computeResumeAtMillis(config, now);
        if (resumeAtMillis < 0) {
            return AgentResult.failure("时间控制节点配置无效: " + describeConfig(config));
        }
        long waitSeconds = Math.max(0, (resumeAtMillis - now) / 1000);
        return AgentResult.success("Mock时间控制：恢复时间 " + formatTime(resumeAtMillis)
                + "，需等待 " + waitSeconds + " 秒（" + describeConfig(config) + "）");
    }

    /**
     * 调度时间到达事件（到点后由引擎校验并恢复工作流），服务重启后的恢复扫描也复用此方法
     * @param instanceId
     * @param nodeId
     * @param resumeAtMillis
     */
    public void scheduleResumeEvent(String instanceId, String nodeId, long resumeAtMillis) {
        long waitMillis = Math.max(0, resumeAtMillis - System.currentTimeMillis());
        scheduler.schedule(() -> {
            try {
                log.info("时间控制到达设定时间，发布恢复事件: instanceId={}, nodeId={}", instanceId, nodeId);
                eventPublisher.publishEvent(new WorkflowTimeArrivedEvent(instanceId, nodeId, resumeAtMillis));
            } catch (Exception e) {
                log.error("发布时间到达事件失败: instanceId={}, nodeId={}", instanceId, nodeId, e);
            }
        }, waitMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * 根据时间模式计算恢复时间点（毫秒时间戳），无效配置返回-1
     * <p>
     * 延迟执行与倒计时为社区内置模式；具体时间/周期性时间/Cron表达式为高级时间模式，
     * 由企业版提供的WorkflowTimeModeResolver计算，未注入实现时报企业版专属提示。
     * </p>
     * @param config
     * @param nowMillis
     * @return
     */
    private long computeResumeAtMillis(NodeTimeControlConfig config, long nowMillis) {
        NodeTimeControlConfig.TimeType timeType = config.getTimeType();
        if (timeType == null) {
            timeType = NodeTimeControlConfig.TimeType.DELAY;
        }
        if (timeType == NodeTimeControlConfig.TimeType.DELAY) {
            int seconds = config.getDelaySeconds() != null ? config.getDelaySeconds() : 0;
            return seconds > 0 ? nowMillis + seconds * 1000L : nowMillis;
        }
        if (timeType == NodeTimeControlConfig.TimeType.COUNTDOWN) {
            int minutes = config.getCountdownMinutes() != null ? config.getCountdownMinutes() : 0;
            int seconds = config.getCountdownSeconds() != null ? config.getCountdownSeconds() : 0;
            long totalSeconds = minutes * 60L + seconds;
            return totalSeconds > 0 ? nowMillis + totalSeconds * 1000L : nowMillis;
        }
        WorkflowTimeModeResolver resolver = timeModeResolverProvider.getIfAvailable();
        if (resolver == null) {
            log.warn("高级时间模式{}为企业版专属功能，当前版本不可用", timeType);
            return -1;
        }
        return resolver.resolveResumeAtMillis(config, nowMillis);
    }

    /**
     * 判断是否为企业版专属的高级时间模式（具体时间/周期性时间/Cron表达式）
     * @param timeType
     * @return
     */
    private boolean isEnterpriseAdvancedMode(NodeTimeControlConfig.TimeType timeType) {
        return timeType == NodeTimeControlConfig.TimeType.SPECIFIC
                || timeType == NodeTimeControlConfig.TimeType.PERIODIC
                || timeType == NodeTimeControlConfig.TimeType.CRON;
    }

    /**
     * 生成配置摘要描述（用于日志与节点输出）
     * @param config
     * @return
     */
    private String describeConfig(NodeTimeControlConfig config) {
        NodeTimeControlConfig.TimeType timeType = config.getTimeType();
        if (timeType == null) {
            timeType = NodeTimeControlConfig.TimeType.DELAY;
        }
        return switch (timeType) {
            case DELAY -> "延迟" + (config.getDelaySeconds() != null ? config.getDelaySeconds() : 0) + "秒";
            case COUNTDOWN -> "倒计时" + (config.getCountdownMinutes() != null ? config.getCountdownMinutes() : 0)
                    + "分" + (config.getCountdownSeconds() != null ? config.getCountdownSeconds() : 0) + "秒";
            case SPECIFIC -> "等待至 " + (config.getSpecificTime() != null ? config.getSpecificTime() : "未配置");
            case PERIODIC -> "周期" + describePeriod(config);
            case CRON -> "cron " + (config.getCronExpression() != null ? config.getCronExpression() : "未配置");
        };
    }

    /**
     * 生成周期规则摘要描述
     * @param config
     * @return
     */
    private String describePeriod(NodeTimeControlConfig config) {
        NodeTimeControlConfig.PeriodType periodType = config.getPeriodType();
        if (periodType == null) {
            periodType = NodeTimeControlConfig.PeriodType.DAILY;
        }
        StringBuilder desc = new StringBuilder();
        switch (periodType) {
            case DAILY -> desc.append("每天");
            case WEEKLY -> {
                desc.append("每周");
                List<Integer> weekdays = config.getPeriodWeekdays();
                if (weekdays != null && !weekdays.isEmpty()) {
                    String[] names = {"", "一", "二", "三", "四", "五", "六", "日"};
                    for (Integer day : weekdays.stream().sorted().toList()) {
                        if (day != null && day >= 1 && day <= 7) {
                            desc.append(names[day]);
                        }
                    }
                } else {
                    desc.append("全部");
                }
            }
            case MONTHLY -> desc.append("每月").append(config.getPeriodDayOfMonth() != null ? config.getPeriodDayOfMonth() : 1).append("日");
        }
        desc.append(" ").append(config.getPeriodTime() != null ? config.getPeriodTime() : "00:00");
        if (Boolean.TRUE.equals(config.getWorkdayOnly())) {
            desc.append("（仅工作日）");
        }
        return desc.toString();
    }

    /**
     * 格式化毫秒时间戳为yyyy-MM-dd HH:mm:ss
     * @param millis
     * @return
     */
    private String formatTime(long millis) {
        return RESUME_TIME_FORMATTER.format(LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
    }
}
