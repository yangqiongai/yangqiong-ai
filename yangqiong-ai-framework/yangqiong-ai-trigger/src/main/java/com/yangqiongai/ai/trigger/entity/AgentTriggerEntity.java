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
 * Agent触发规则
 * @author yangqiong
 */
@TableName("ai_agent_trigger")
public class AgentTriggerEntity extends ScopeEntity {

    /**
     * 触发类型:CRON定时/EVENT内置事件/WEBHOOK回调/FILE文件监听
     */
    public static final String TYPE_CRON = "CRON";

    /**
     * 触发类型:内置治理事件
     */
    public static final String TYPE_EVENT = "EVENT";

    /**
     * 触发类型:WEBHOOK回调(企业增强)
     */
    public static final String TYPE_WEBHOOK = "WEBHOOK";

    /**
     * 触发类型:文件监听(企业增强)
     */
    public static final String TYPE_FILE = "FILE";

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 触发器编码(唯一)
     */
    private String triggerCode;

    /**
     * 触发器名称
     */
    private String name;

    /**
     * 触发类型(CRON/EVENT/WEBHOOK/FILE)
     */
    private String triggerType;

    /**
     * 目标Agent编码
     */
    private String agentCode;

    /**
     * 归属用户锚点
     */
    private String userAnchor;

    /**
     * CRON表达式(CRON类型必填,复用引擎CronExpression语法)
     */
    private String cronExpr;

    /**
     * WEBHOOK回调令牌(WEBHOOK类型,企业增强)
     */
    private String webhookToken;

    /**
     * 内置事件源(EVENT类型:CONFIG_DRIFT/EVAL_REGRESSION/ACTION_ANOMALY/BUDGET_EXCEEDED/OUTPUT_CONTRACT_VIOLATION)
     */
    private String eventSource;

    /**
     * 输入指令模板,支持{payload}占位符替换为触发载荷
     */
    private String payloadTemplate;

    /**
     * 文件监听根目录(FILE类型必填,复用引擎FileWatchTrigger递归监听)
     */
    private String watchDir;

    /**
     * 文件后缀过滤(逗号分隔,空为监听全部文件)
     */
    private String fileSuffixes;

    /**
     * 结果回投WEBHOOK地址(空则不回投)
     */
    private String notifyWebhook;

    /**
     * 每日触发配额(空或0为不限)
     */
    private Integer dailyQuota;

    /**
     * 同源去重窗口秒数(0为不去重)
     */
    private Integer dedupWindowSeconds;

    /**
     * 是否启用(1启用/0停用)
     */
    private Integer enabled;

    /**
     * CRON最近一次计划触发时刻(幂等推进基准)
     */
    private LocalDateTime lastFireTime;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTriggerCode() {
        return triggerCode;
    }

    public void setTriggerCode(String triggerCode) {
        this.triggerCode = triggerCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getUserAnchor() {
        return userAnchor;
    }

    public void setUserAnchor(String userAnchor) {
        this.userAnchor = userAnchor;
    }

    public String getCronExpr() {
        return cronExpr;
    }

    public void setCronExpr(String cronExpr) {
        this.cronExpr = cronExpr;
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public void setWebhookToken(String webhookToken) {
        this.webhookToken = webhookToken;
    }

    public String getEventSource() {
        return eventSource;
    }

    public void setEventSource(String eventSource) {
        this.eventSource = eventSource;
    }

    public String getPayloadTemplate() {
        return payloadTemplate;
    }

    public void setPayloadTemplate(String payloadTemplate) {
        this.payloadTemplate = payloadTemplate;
    }

    public String getWatchDir() {
        return watchDir;
    }

    public void setWatchDir(String watchDir) {
        this.watchDir = watchDir;
    }

    public String getFileSuffixes() {
        return fileSuffixes;
    }

    public void setFileSuffixes(String fileSuffixes) {
        this.fileSuffixes = fileSuffixes;
    }

    public String getNotifyWebhook() {
        return notifyWebhook;
    }

    public void setNotifyWebhook(String notifyWebhook) {
        this.notifyWebhook = notifyWebhook;
    }

    public Integer getDailyQuota() {
        return dailyQuota;
    }

    public void setDailyQuota(Integer dailyQuota) {
        this.dailyQuota = dailyQuota;
    }

    public Integer getDedupWindowSeconds() {
        return dedupWindowSeconds;
    }

    public void setDedupWindowSeconds(Integer dedupWindowSeconds) {
        this.dedupWindowSeconds = dedupWindowSeconds;
    }

    public Integer getEnabled() {
        return enabled;
    }

    public void setEnabled(Integer enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastFireTime() {
        return lastFireTime;
    }

    public void setLastFireTime(LocalDateTime lastFireTime) {
        this.lastFireTime = lastFireTime;
    }
}
