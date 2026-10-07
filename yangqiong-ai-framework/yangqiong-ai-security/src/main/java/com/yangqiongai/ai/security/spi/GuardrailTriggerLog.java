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
package com.yangqiongai.ai.security.spi;

import java.time.LocalDateTime;

/**
 * 护栏触发日志（SPI契约模型，商业触发审计落库时经此转换）
 *
 * @author yangqiong
 */
public class GuardrailTriggerLog {

    /**
     * 主键
     */
    private String id;

    /**
     * 触发的护栏规则名称
     */
    private String ruleName;

    /**
     * 挂载点
     */
    private String hookPoint;

    /**
     * 触发时被检查的内容摘要
     */
    private String inputSummary;

    /**
     * 护栏动作：BLOCK/MASK
     */
    private String action;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 操作人标识（租户用户为用户ID，平台管理员为用户名）
     */
    private String userId;

    /**
     * 触发时间
     */
    private LocalDateTime createTime;

    /**
     * 租户名称（商业触发审计关联回填，社区内存实现不填）
     */
    private String tenantName;

    /**
     * Agent名称（商业触发审计关联回填，社区内存实现不填）
     */
    private String agentName;

    /**
     * 操作人名称（商业触发审计关联回填，社区内存实现不填）
     */
    private String userName;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRuleName() {
        return ruleName;
    }

    public void setRuleName(String ruleName) {
        this.ruleName = ruleName;
    }

    public String getHookPoint() {
        return hookPoint;
    }

    public void setHookPoint(String hookPoint) {
        this.hookPoint = hookPoint;
    }

    public String getInputSummary() {
        return inputSummary;
    }

    public void setInputSummary(String inputSummary) {
        this.inputSummary = inputSummary;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public String getTenantName() {
        return tenantName;
    }

    public void setTenantName(String tenantName) {
        this.tenantName = tenantName;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}
