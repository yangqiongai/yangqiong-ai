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
package com.yangqiongai.ai.trigger.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.cron.CronExpression;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.common.util.StringUtils;
import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import com.yangqiongai.ai.trigger.entity.AgentTriggerLogEntity;
import com.yangqiongai.ai.trigger.event.AgentTriggerFiredEvent;
import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.notify.TriggerNotifyService;
import com.yangqiongai.ai.trigger.repository.AgentTriggerLogRepository;
import com.yangqiongai.ai.trigger.repository.AgentTriggerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Agent触发器管理
 * <p>
 * 触发统一入口：幂等键去重→同源去重窗口→每日配额→写队列→触发留痕→回投。
 * 队列走E2全链（抢占执行/心跳/背压由执行侧兜底）。
 * </p>
 * @author yangqiong
 */
@Service
public class AgentTriggerService {

    private static final Logger log = LoggerFactory.getLogger(AgentTriggerService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 队列默认优先级（与AgentTaskEnqueuer口径一致）
     */
    private static final int DEFAULT_PRIORITY = 5;

    /**
     * 触发任务来源标识
     */
    private static final String TASK_SOURCE_TRIGGER = "TRIGGER";

    /**
     * 内置事件触发来源标识
     */
    public static final String FIRE_SOURCE_BUILTIN_EVENT = "BUILTIN_EVENT";

    /**
     * 触发载荷摘要截断长度
     */
    private static final int PAYLOAD_DIGEST_LENGTH = 200;

    @Autowired
    private AgentTriggerRepository triggerRepository;

    @Autowired
    private AgentTriggerLogRepository logRepository;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TriggerNotifyService notifyService;

    /**
     * 创建触发规则（CRON校验表达式、EVENT校验事件源、WEBHOOK生成令牌）
     * @param entity
     * @return
     */
    public AgentTriggerEntity create(AgentTriggerEntity entity) {
        validate(entity);
        if (entity.getTriggerCode() == null || entity.getTriggerCode().isBlank()) {
            entity.setTriggerCode("tg-" + StringUtils.generateCompactId());
        }
        if (entity.getTriggerType().equals(AgentTriggerEntity.TYPE_WEBHOOK)
                && (entity.getWebhookToken() == null || entity.getWebhookToken().isBlank())) {
            entity.setWebhookToken("whk-" + StringUtils.generateCompactId());
        }
        if (entity.getEnabled() == null) {
            entity.setEnabled(1);
        }
        if (entity.getDedupWindowSeconds() == null) {
            entity.setDedupWindowSeconds(0);
        }
        entity.setId(null);
        Long id = triggerRepository.insert(entity);
        entity.setId(id);
        return entity;
    }

    /**
     * 更新触发规则
     * @param entity
     */
    public void update(AgentTriggerEntity entity) {
        if (entity.getId() == null || triggerRepository.selectById(entity.getId()) == null) {
            throw new IllegalArgumentException("触发器不存在: " + entity.getId());
        }
        validate(entity);
        triggerRepository.update(entity);
    }

    /**
     * 启停触发规则
     * @param id
     * @param enabled
     */
    public void changeEnabled(Long id, boolean enabled) {
        AgentTriggerEntity entity = triggerRepository.selectById(id);
        if (entity == null) {
            throw new IllegalArgumentException("触发器不存在: " + id);
        }
        entity.setEnabled(enabled ? 1 : 0);
        triggerRepository.update(entity);
    }

    /**
     * 删除触发规则
     * @param id
     */
    public void delete(Long id) {
        triggerRepository.deleteById(id);
    }

    /**
     * 查询触发规则
     * @param id
     * @return
     */
    public AgentTriggerEntity getById(Long id) {
        return triggerRepository.selectById(id);
    }

    /**
     * 触发统一入口（幂等去重→去重窗口→配额→入队→留痕→回投）
     * @param triggerCode 触发器编码
     * @param dedupKey 幂等键（空则跳过幂等判断）
     * @param payload 触发载荷（渲染{payload}占位符）
     * @param fireSource 触发来源
     * @return
     */
    public AgentTriggerFireResult fire(String triggerCode, String dedupKey, String payload, String fireSource) {
        AgentTriggerEntity trigger = triggerRepository.findByCode(triggerCode);
        if (trigger == null) {
            return AgentTriggerFireResult.rejected(AgentTriggerFireResult.NOT_FOUND, "触发器不存在");
        }
        if (trigger.getEnabled() == null || trigger.getEnabled() != 1) {
            return AgentTriggerFireResult.rejected(AgentTriggerFireResult.DISABLED, "触发器已停用");
        }

        // 幂等键：同键已触发过则拒绝（CRON计划时刻/事件载荷摘要）
        if (dedupKey != null && !dedupKey.isBlank()) {
            AgentTriggerLogEntity last = logRepository.findLastByDedupKey(trigger.getId(), dedupKey);
            if (last != null) {
                return AgentTriggerFireResult.rejected(AgentTriggerFireResult.DUPLICATED, "幂等键已触发");
            }
        }

        // 同源去重窗口：窗口内重复触发拒绝，恰好等于窗口放行
        int windowSeconds = trigger.getDedupWindowSeconds() == null ? 0 : trigger.getDedupWindowSeconds();
        if (windowSeconds > 0) {
            AgentTriggerLogEntity lastAny = logRepository.findLastByTrigger(trigger.getId());
            if (lastAny != null && lastAny.getCreateTime() != null) {
                long elapsed = Duration.between(lastAny.getCreateTime(), LocalDateTime.now()).getSeconds();
                if (elapsed < windowSeconds) {
                    return AgentTriggerFireResult.rejected(AgentTriggerFireResult.DUPLICATED,
                            "去重窗口内重复触发");
                }
            }
        }

        // 每日配额：空或0为不限
        if (trigger.getDailyQuota() != null && trigger.getDailyQuota() > 0) {
            LocalDate today = LocalDate.now();
            long firedToday = logRepository.countByTriggerAndTimeRange(trigger.getId(),
                    today.atStartOfDay(), today.plusDays(1).atStartOfDay());
            if (firedToday >= trigger.getDailyQuota()) {
                return AgentTriggerFireResult.rejected(AgentTriggerFireResult.QUOTA_EXCEEDED,
                        "每日配额已耗尽(" + trigger.getDailyQuota() + ")");
            }
        }

        String taskId = enqueueTriggerTask(trigger, payload);
        recordLog(trigger, dedupKey, fireSource, taskId, payload);
        eventPublisher.publishEvent(new AgentTriggerFiredEvent(trigger.getTriggerCode(), trigger.getAgentCode(),
                fireSource, taskId, payload));
        notifyService.notifyFire(trigger, taskId, fireSource, payload);
        log.info("触发器执行入队: trigger={}, task={}, source={}", triggerCode, taskId, fireSource);
        return AgentTriggerFireResult.fired(taskId);
    }

    /**
     * WEBHOOK令牌触发（令牌即凭证，仅匹配启用中的WEBHOOK规则）
     * @param webhookToken 回调令牌
     * @param dedupKey 幂等键
     * @param payload 触发载荷
     * @param fireSource 触发来源
     * @return
     */
    public AgentTriggerFireResult fireByWebhookToken(String webhookToken, String dedupKey,
                                                     String payload, String fireSource) {
        if (webhookToken == null || webhookToken.isBlank()) {
            return AgentTriggerFireResult.rejected(AgentTriggerFireResult.NOT_FOUND, "触发器不存在");
        }
        AgentTriggerEntity trigger = triggerRepository.findEnabledByWebhookToken(webhookToken);
        if (trigger == null) {
            return AgentTriggerFireResult.rejected(AgentTriggerFireResult.NOT_FOUND, "触发器不存在");
        }
        return fire(trigger.getTriggerCode(), dedupKey, payload, fireSource);
    }

    /**
     * 按事件源触发全部匹配的EVENT规则（内置治理事件入口）
     * <p>
     * 单条触发异常不阻断其余规则，保证事件流不被监听方拖垮。
     * @param eventSource 事件源标识
     * @param dedupKey 幂等键（一般为载荷摘要）
     * @param payload 触发载荷
     * @return 触发成功的触发器编码列表
     */
    public List<String> fireEventSource(String eventSource, String dedupKey, String payload) {
        List<AgentTriggerEntity> triggers = triggerRepository.findEnabledByEventSource(eventSource);
        List<String> fired = new java.util.ArrayList<>();
        for (AgentTriggerEntity trigger : triggers) {
            try {
                AgentTriggerFireResult result = fire(trigger.getTriggerCode(), dedupKey, payload,
                        FIRE_SOURCE_BUILTIN_EVENT);
                if (result.isFired()) {
                    fired.add(trigger.getTriggerCode());
                }
            } catch (Exception e) {
                log.warn("事件触发规则执行异常: eventSource={}, trigger={}", eventSource,
                        trigger.getTriggerCode(), e);
            }
        }
        return fired;
    }

    /**
     * 触发任务入队（写QUEUED记录，由队列调度器抢占执行）
     * @param trigger
     * @param payload
     * @return
     */
    private String enqueueTriggerTask(AgentTriggerEntity trigger, String payload) {
        String input = renderInput(trigger.getPayloadTemplate(), payload);
        AgentRequest request = new AgentRequest()
                .agentCode(trigger.getAgentCode())
                .sessionId("trigger-" + trigger.getTriggerCode())
                .userId(trigger.getUserAnchor())
                .scopeId(trigger.getScopeId() == null ? "default" : trigger.getScopeId())
                .input(input);
        String taskId = StringUtils.generateCompactId();
        AgentTaskInfo info = new AgentTaskInfo();
        info.setTaskId(taskId);
        info.setAgentCode(trigger.getAgentCode());
        info.setSessionId(request.getSessionId());
        info.setUserId(request.getUserId());
        info.setScopeId(request.getScopeId());
        info.setTaskSource(TASK_SOURCE_TRIGGER);
        info.setTaskStatus("QUEUED");
        info.setPriority(DEFAULT_PRIORITY);
        info.setQueuedTime(LocalDateTime.now());
        info.setUserInput(input);
        info.setBody(serializeRequest(request));
        agentTaskRepository.createTask(info);
        return taskId;
    }

    /**
     * 记录触发留痕（配额统计/幂等判定/审计依据）
     * @param trigger
     * @param dedupKey
     * @param fireSource
     * @param taskId
     * @param payload
     */
    private void recordLog(AgentTriggerEntity trigger, String dedupKey, String fireSource,
                           String taskId, String payload) {
        AgentTriggerLogEntity logEntity = new AgentTriggerLogEntity();
        logEntity.setTriggerId(trigger.getId());
        logEntity.setTriggerCode(trigger.getTriggerCode());
        logEntity.setDedupKey(dedupKey);
        logEntity.setFireSource(fireSource);
        logEntity.setTaskId(taskId);
        logEntity.setPayloadDigest(abbreviate(payload));
        logEntity.setCreateTime(LocalDateTime.now());
        logRepository.insert(logEntity);
    }

    /**
     * 渲染输入指令：模板含{payload}占位符时替换为载荷，无模板时直接使用载荷
     * @param template
     * @param payload
     * @return
     */
    private String renderInput(String template, String payload) {
        if (template == null || template.isBlank()) {
            return payload == null ? "" : payload;
        }
        return template.replace("{payload}", payload == null ? "" : payload);
    }

    /**
     * 序列化队列请求体（抢占执行后反序列化恢复链路）
     * @param request
     * @return
     */
    private String serializeRequest(AgentRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalStateException("序列化触发请求失败: " + triggerDesc(request), e);
        }
    }

    private String triggerDesc(AgentRequest request) {
        return request.getAgentCode() == null ? "unknown" : request.getAgentCode();
    }

    private String abbreviate(String text) {
        if (text == null) {
            return null;
        }
        return text.length() > PAYLOAD_DIGEST_LENGTH ? text.substring(0, PAYLOAD_DIGEST_LENGTH) : text;
    }

    /**
     * 校验触发规则必填项与类型约束
     * @param entity
     */
    private void validate(AgentTriggerEntity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("触发器不能为空");
        }
        if (entity.getTriggerType() == null || entity.getTriggerType().isBlank()) {
            throw new IllegalArgumentException("触发类型不能为空");
        }
        List<String> supported = List.of(AgentTriggerEntity.TYPE_CRON, AgentTriggerEntity.TYPE_EVENT,
                AgentTriggerEntity.TYPE_WEBHOOK, AgentTriggerEntity.TYPE_FILE);
        if (!supported.contains(entity.getTriggerType())) {
            throw new IllegalArgumentException("触发类型不合法: " + entity.getTriggerType());
        }
        if (entity.getAgentCode() == null || entity.getAgentCode().isBlank()) {
            throw new IllegalArgumentException("目标Agent编码不能为空");
        }
        if (entity.getTriggerType().equals(AgentTriggerEntity.TYPE_CRON)) {
            if (entity.getCronExpr() == null || entity.getCronExpr().isBlank()) {
                throw new IllegalArgumentException("CRON触发器必须配置cron表达式");
            }
            try {
                CronExpression.parse(entity.getCronExpr());
            } catch (Exception e) {
                throw new IllegalArgumentException("CRON表达式不合法: " + entity.getCronExpr());
            }
        }
        if (entity.getTriggerType().equals(AgentTriggerEntity.TYPE_EVENT)
                && (entity.getEventSource() == null || entity.getEventSource().isBlank())) {
            throw new IllegalArgumentException("事件触发器必须配置事件源");
        }
        if (entity.getTriggerType().equals(AgentTriggerEntity.TYPE_FILE)
                && (entity.getWatchDir() == null || entity.getWatchDir().isBlank())) {
            throw new IllegalArgumentException("文件触发器必须配置监听目录");
        }
        if (entity.getDailyQuota() != null && entity.getDailyQuota() < 0) {
            throw new IllegalArgumentException("每日配额不能为负数");
        }
        if (entity.getDedupWindowSeconds() != null && entity.getDedupWindowSeconds() < 0) {
            throw new IllegalArgumentException("去重窗口不能为负数");
        }
    }

    /**
     * 计算载荷SHA-256摘要（事件源幂等键）
     * @param text
     * @return
     */
    public static String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256算法不可用", e);
        }
    }
}
