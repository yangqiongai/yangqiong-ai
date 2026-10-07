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
package com.yangqiongai.ai.agent.core.context;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent上下文
 * @author yangqiong
 */
public class AgentContext {

    /**
     * 执行Agent属性键
     */
    public static final String CTX_EXECUTION_AGENT = "executionAgent";

    /**
     * 输入属性键
     */
    public static final String CTX_INPUTS = "inputs";

    /**
     * 结果转换器属性键
     */
    public static final String CTX_RESULT_CONVERTER = "resultConverter";

    /**
     * 会话配置属性键
     */
    public static final String CTX_SESSION_CONFIG = "sessionConfig";

    /**
     * 取消令牌属性键
     */
    public static final String CTX_CANCELLATION_TOKEN = "cancellationToken";

    /**
     * 结构化输出类型属性键
     */
    public static final String CTX_STRUCTURED_OUTPUT_TYPE = "structuredOutputType";

    /**
     * 子代理声明列表属性键
     */
    public static final String CTX_SUBAGENT_DECLARATIONS = "subagentDeclarations";

    /**
     * 规划模式开关属性键
     */
    public static final String CTX_PLAN_MODE_ENABLED = "planModeEnabled";

    /**
     * 知识上下文属性键
     */
    public static final String CTX_KNOWLEDGE_CONTEXT = "knowledgeContext";

    /**
     * 系统提示词属性键
     */
    public static final String CTX_SYSTEM_PROMPT = "systemPrompt";

    /**
     * 中断控制属性键
     */
    public static final String CTX_INTERRUPT_CONTROL = "interruptControl";

    /**
     * 技能箱属性键（Hook注入模式下由中间件读取）
     */
    public static final String CTX_SKILL_BOX = "skillBox";

    /**
     * 请求
     */
    private final AgentRequest request;

    /**
     * 追踪ID
     */
    private String traceId;

    /**
     * 运行ID
     */
    private String runId;

    /**
     * 知识范围控制
     */
    private String knowledgeScope;

    /**
     * 上下文属性
     */
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();

    public AgentContext(AgentRequest request) {
        this.request = request;
    }

    /**
     * 转换为运行时上下文
     * @return
     */
    public AgentRuntimeContext toRuntimeContext() {
        if (request == null) {
            return AgentRuntimeContext.empty();
        }
        AgentRuntimeContext.Builder builder = AgentRuntimeContext.builder();
        if (request.getUserId() != null) {
            builder.userId(request.getUserId());
        }
        if (request.getSessionId() != null) {
            builder.sessionId(request.getSessionId());
        }
        return builder.build();
    }

    /**
     * 获取请求
     * @return
     */
    public AgentRequest getRequest() {
        return request;
    }

    /**
     * 获取追踪ID，若未设置则从MDC降级获取
     * @return
     */
    public String getTraceId() {
        if (traceId == null || traceId.isBlank()) {
            String mdcTraceId = MDC.get("traceId");
            if (mdcTraceId != null && !mdcTraceId.isBlank()) {
                this.traceId = mdcTraceId;
            }
        }
        return traceId;
    }

    /**
     * 设置追踪ID
     * @param traceId
     */
    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    /**
     * 获取运行ID
     * @return
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 设置运行ID
     * @param runId
     */
    public void setRunId(String runId) {
        this.runId = runId;
    }

    /**
     * 获取知识范围控制
     * @return
     */
    public String getKnowledgeScope() {
        return knowledgeScope;
    }

    /**
     * 设置知识范围控制
     * @param knowledgeScope
     */
    public void setKnowledgeScope(String knowledgeScope) {
        this.knowledgeScope = knowledgeScope;
    }

    /**
     * 设置上下文属性
     * @param key
     * @param value
     */
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * 获取上下文属性
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String key) {
        T value = (T) attributes.get(key);
        if (value == null && request.getBody() != null){
            value = (T) request.getBody().get(key);
        }
        return value;
    }

    /**
     * 获取所有上下文属性
     * @return
     */
    public Map<String, Object> getAttributes() {
        if(request.getBody() != null){
            request.getBody().forEach(attributes::putIfAbsent);
        }
        return attributes;
    }

    /**
     * 设置结构化输出类型
     * @param outputType
     */
    public void setStructuredOutputType(Class<?> outputType) {
        attributes.put(CTX_STRUCTURED_OUTPUT_TYPE, outputType);
    }

    /**
     * 获取结构化输出类型
     * @return
     */
    public Class<?> getStructuredOutputType() {
        Object value = attributes.get(CTX_STRUCTURED_OUTPUT_TYPE);
        return value instanceof Class<?> cls ? cls : null;
    }
}
