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
package com.yangqiongai.ai.agent.core.bootstrap;

import com.yangqiongai.ai.agent.core.AgentResultConverter;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;

import java.util.List;
import java.util.Map;

/**
 * Agent初始化
 * @author yangqiong
 */
public class AgentBootstrap {

    /**
     * Agent构建器
     */
    private HarnessAgentRuntimeBuilder agentBuilder;

    /**
     * 输入消息列表
     */
    private List<AgentMessage> inputMessages;

    /**
     * 结构化输出类型
     */
    private Class<?> structuredOutputType;

    /**
     * 结果转换器
     */
    private AgentResultConverter resultConverter;

    /**
     * 会话配置
     */
    private Map<String, Object> sessionConfig;

    /**
     * 额外上下文属性
     */
    private Map<String, Object> extraAttributes;

    /**
     * Agent上下文（由AgentBootstrapService设置，用于Hook注入模式传递SkillBox）
     */
    private AgentContext agentContext;

    /**
     * 请求
     */
    private AgentRequest request;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 最大迭代次数
     */
    private int maxIters = 10;

    /**
     * 工具箱
     */
    private AgentToolkit toolkit;

    /**
     * 技能箱
     */
    private AgentSkillBox skillBox;

    /**
     * 是否禁用长期记忆工具
     */
    private boolean disableLongTermMemoryTools;

    /**
     * 是否启用技能按需加载（注册load_skill_through_path工具，仅注入摘要到sysPrompt）
     */
    private boolean skillLoadOnDemand;

    /**
     * 是否启用技能Hook注入（通过onSystemPrompt中间件动态注入，不存入Memory）
     */
    private boolean skillHookInject;

    /**
     * 是否启用技能动态重建（通过HarnessSkillMiddleware每次call()动态重建技能箱）
     */
    private boolean skillDynamicRebuild;

    /**
     * 技能中间件（动态重建模式时使用）
     */
    private AgentMiddleware skillMiddleware;

    public AgentBootstrap agentBuilder(HarnessAgentRuntimeBuilder agentBuilder) {
        this.agentBuilder = agentBuilder;
        return this;
    }

    public AgentBootstrap inputMessages(List<AgentMessage> inputMessages) {
        this.inputMessages = inputMessages;
        return this;
    }

    public AgentBootstrap structuredOutputType(Class<?> structuredOutputType) {
        this.structuredOutputType = structuredOutputType;
        return this;
    }

    public AgentBootstrap resultConverter(AgentResultConverter resultConverter) {
        this.resultConverter = resultConverter;
        return this;
    }

    public AgentBootstrap sessionConfig(Map<String, Object> sessionConfig) {
        this.sessionConfig = sessionConfig;
        return this;
    }

    public AgentBootstrap extraAttributes(Map<String, Object> extraAttributes) {
        this.extraAttributes = extraAttributes;
        return this;
    }

    public AgentBootstrap request(AgentRequest request) {
        this.request = request;
        return this;
    }

    public AgentBootstrap systemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
        return this;
    }

    public AgentBootstrap maxIters(int maxIters) {
        this.maxIters = maxIters;
        return this;
    }

    public AgentBootstrap toolkit(AgentToolkit toolkit) {
        this.toolkit = toolkit;
        return this;
    }

    public AgentBootstrap skillBox(AgentSkillBox skillBox) {
        this.skillBox = skillBox;
        return this;
    }

    public AgentBootstrap disableLongTermMemoryTools(boolean disable) {
        this.disableLongTermMemoryTools = disable;
        return this;
    }

    public AgentBootstrap skillLoadOnDemand(boolean skillLoadOnDemand) {
        this.skillLoadOnDemand = skillLoadOnDemand;
        return this;
    }

    public AgentBootstrap skillHookInject(boolean skillHookInject) {
        this.skillHookInject = skillHookInject;
        return this;
    }

    public AgentBootstrap skillDynamicRebuild(boolean skillDynamicRebuild) {
        this.skillDynamicRebuild = skillDynamicRebuild;
        return this;
    }

    public AgentBootstrap skillMiddleware(AgentMiddleware skillMiddleware) {
        this.skillMiddleware = skillMiddleware;
        return this;
    }

    /**
     * 构建AgentRuntime
     * @return
     */
    public AgentRuntime buildAgent() {
        if (agentBuilder == null) {
            throw new IllegalStateException("agentBuilder未设置");
        }
        return agentBuilder.build();
    }

    /**
     * 获取Agent构建器
     * @return
     */
    public HarnessAgentRuntimeBuilder getAgentBuilder() {
        return agentBuilder;
    }

    /**
     * 获取输入消息列表
     * @return
     */
    public List<AgentMessage> getInputMessages() {
        return inputMessages;
    }

    /**
     * 获取结构化输出类型
     * @return
     */
    public Class<?> getStructuredOutputType() {
        return structuredOutputType;
    }

    /**
     * 获取结果转换器
     * @return
     */
    public AgentResultConverter getResultConverter() {
        return resultConverter;
    }

    /**
     * 获取会话配置
     * @return
     */
    public Map<String, Object> getSessionConfig() {
        return sessionConfig;
    }

    /**
     * 获取额外上下文属性
     * @return
     */
    public Map<String, Object> getExtraAttributes() {
        return extraAttributes;
    }

    /**
     * 获取请求
     * @return
     */
    public AgentRequest getRequest() {
        return request;
    }

    /**
     * 获取系统提示词
     * @return
     */
    public String getSystemPrompt() {
        return systemPrompt;
    }

    /**
     * 获取最大迭代次数
     * @return
     */
    public int getMaxIters() {
        return maxIters;
    }

    /**
     * 获取工具箱
     * @return
     */
    public AgentToolkit getToolkit() {
        return toolkit;
    }

    /**
     * 获取技能箱
     * @return
     */
    public AgentSkillBox getSkillBox() {
        return skillBox;
    }

    /**
     * 是否禁用长期记忆工具
     * @return
     */
    public boolean isDisableLongTermMemoryTools() {
        return disableLongTermMemoryTools;
    }

    /**
     * 是否启用技能按需加载
     * @return
     */
    public boolean isSkillLoadOnDemand() {
        return skillLoadOnDemand;
    }

    /**
     * 是否启用技能Hook注入
     * @return
     */
    public boolean isSkillHookInject() {
        return skillHookInject;
    }

    /**
     * 是否启用技能动态重建
     * @return
     */
    public boolean isSkillDynamicRebuild() {
        return skillDynamicRebuild;
    }

    /**
     * 获取技能中间件
     * @return
     */
    public AgentMiddleware getSkillMiddleware() {
        return skillMiddleware;
    }

    public AgentContext getAgentContext() {
        return agentContext;
    }

    public void setAgentContext(AgentContext agentContext) {
        this.agentContext = agentContext;
    }

    /**
     * @return
     */
    public boolean isToolkitEnabled() {
        return toolkit != null;
    }


}
