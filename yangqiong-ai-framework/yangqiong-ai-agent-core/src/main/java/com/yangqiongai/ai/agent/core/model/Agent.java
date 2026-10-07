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
package com.yangqiongai.ai.agent.core.model;

import java.time.LocalDateTime;

/**
 * Agent（平台上可运行、可通过code调用的Agent，生效配置+路由元数据视图）
 * <p>
 * 承载ai_agent表的生效配置，通过agentCode路由调用，由注册中心发布时物化生成；
 * 不是执行中的会话实例。治理层AgentDefinition的agentCode ≡ 运行时agentCode。
 * @author yangqiong
 */
public class Agent {

    /**
     * 主键
     */
    private Long id;

    /**
     * Agent编码(唯一标识，路由键)
     */
    private String agentCode;

    /**
     * Agent名称(中文显示名)
     */
    private String agentName;

    /**
     * 描述
     */
    private String description;

    /**
     * 图标
     */
    private String icon;

    /**
     * 分类
     */
    private String category;

    /**
     * 所属目录编码(空为未分类，目录树管理用)
     */
    private String directoryCode;

    /**
     * 状态(0-禁用 1-启用)
     */
    private Integer status;

    /**
     * 排序号
     */
    private Integer sortOrder;

    /**
     * 会话类型(CHAT/KB_QA/DOC_QA/ORCHESTRATION)
     */
    private String sessionType;

    /**
     * Agent配置(JSON: model, systemPrompt, maxIterations, temperature，tools，skills,knowledgeBase,bindingMode)
     * <p>
     * model: 模型名称
     * systemPrompt: 系统提示词
     * maxIterations: 最大迭代次数
     * temperature: 温度参数
     * tools: 工具配置
     * skills: 能力配置
     * knowledgeBase: 知识库配置[{"kbCode": "budget-kb","kbName": "预算知识库"}]（数组对象形式；兼容旧格式{"kbCodes": ["budget-kb"],"topK": 5}，topK可配置在顶层，解析链路不支持scoreThreshold）
     * bindingMode: append,replace(append:追加配置，replace:替换配置)
     * </p>
     */
    private String agentConfig;

    /**
     * 平台下发模板标记（1-平台域共享模板，创建租户时复制到租户域；0-普通Agent）
     */
    private Integer shared;

    /**
     * 复制来源Agent编码（租户域副本专用，平台域模板为空）
     */
    private String originAgentCode;

    /**
     * 复制来源版本ID（租户域副本专用，指向平台域来源版本）
     */
    private Long originVersionId;

    /**
     * 作用域ID（default为平台域模板域；租户域为其租户ID，由入库自动填充，租户仅见本域）
     */
    private String scopeId;

    /**
     * 是否支持图片(1-支持 0-不支持，取自Agent绑定模型的支持能力)
     */
    private Integer supportImage;

    /**
     * 备注
     */
    private String remark;

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

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDirectoryCode() {
        return directoryCode;
    }

    public void setDirectoryCode(String directoryCode) {
        this.directoryCode = directoryCode;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getSessionType() {
        return sessionType;
    }

    public void setSessionType(String sessionType) {
        this.sessionType = sessionType;
    }

    public String getAgentConfig() {
        return agentConfig;
    }

    public void setAgentConfig(String agentConfig) {
        this.agentConfig = agentConfig;
    }

    public Integer getShared() {
        return shared;
    }

    public void setShared(Integer shared) {
        this.shared = shared;
    }

    public String getOriginAgentCode() {
        return originAgentCode;
    }

    public void setOriginAgentCode(String originAgentCode) {
        this.originAgentCode = originAgentCode;
    }

    public Long getOriginVersionId() {
        return originVersionId;
    }

    public void setOriginVersionId(Long originVersionId) {
        this.originVersionId = originVersionId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    public Integer getSupportImage() {
        return supportImage;
    }

    public void setSupportImage(Integer supportImage) {
        this.supportImage = supportImage;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
