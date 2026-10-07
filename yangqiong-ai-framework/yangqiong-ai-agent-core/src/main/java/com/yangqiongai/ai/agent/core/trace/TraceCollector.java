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
package com.yangqiongai.ai.agent.core.trace;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 追踪采集器（适配AgentScope 2.0 Middleware）
 * <p>
 * 适配AgentScope 2.0中间件钩子：onAgent/onReasoning/onActing/onModelCall/onSystemPrompt，
 * 采集全链路追踪数据用于可观测性
 * </p>
 * @author yangqiong
 */
@Slf4j
@Service
public class TraceCollector {

    private static final int MAX_TRACE_ENTRIES = 100;

    /**
     * 配置追踪上下文
     * @param context
     * @param traceCtx
     */
    public void configure(AgentContext context, TraceContext traceCtx) {
        if (traceCtx == null) {
            traceCtx = TraceContext.fromMdc(
                    UUID.randomUUID().toString(),
                    context.getRequest().getAgentCode(),
                    context.getRequest().getSessionId());
        }
        if (traceCtx.getTraceId() == null || traceCtx.getTraceId().isEmpty()) {
            traceCtx.setTraceId(UUID.randomUUID().toString());
        }
        MDC.put("traceId", traceCtx.getTraceId());
        MDC.put("runId", traceCtx.getRunId());
        MDC.put("agentCode", traceCtx.getAgentCode());
        MDC.put("sessionId", traceCtx.getSessionId());
        context.setTraceId(traceCtx.getTraceId());
        context.setRunId(traceCtx.getRunId());
        context.setAttribute("traceContext", traceCtx);
        context.setAttribute("traceEntries", new ConcurrentHashMap<String, Map<String, Object>>());
    }

    /**
     * Agent生命周期追踪
     * @param context
     * @param phase
     */
    public void onAgent(AgentContext context, String phase) {
        log.info("Agent生命周期: phase={}, traceId={}", phase, context.getTraceId());
        context.setAttribute("lastAgentPhase", phase);
        context.setAttribute("lastAgentTime", System.currentTimeMillis());
        addTraceEntry(context, "agent", Map.of(
                "phase", phase,
                "traceId", StringUtils.getOrDefault(context.getTraceId()),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 推理步骤追踪
     * @param context
     * @param content
     */
    public void onReasoning(AgentContext context, String content) {
        log.debug("推理步骤: phase={}, reasoningLength={}",
                context.getAttribute("lastAgentPhase"),
                content != null ? content.length() : 0);
        context.setAttribute("lastReasoning", content);
        addTraceEntry(context, "reasoning", Map.of(
                "contentLength", content != null ? content.length() : 0,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 工具调用追踪
     * @param context
     * @param toolName
     * @param params
     */
    public void onActing(AgentContext context, String toolName, Object params) {
        log.info("工具调用: phase={}, tool={}, traceId={}",
                context.getAttribute("lastAgentPhase"), toolName, context.getTraceId());
        context.setAttribute("lastToolName", toolName);
        context.setAttribute("lastToolTime", System.currentTimeMillis());
        addTraceEntry(context, "acting", Map.of(
                "toolName", StringUtils.getOrDefault(toolName),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 模型调用追踪
     * @param context
     * @param modelId
     * @param latencyMs
     */
    public void onModelCall(AgentContext context, String modelId, long latencyMs) {
        log.info("模型调用: phase={}, modelId={}, latency={}ms, traceId={}",
                context.getAttribute("lastAgentPhase"), modelId, latencyMs, context.getTraceId());
        context.setAttribute("lastModelId", modelId);
        context.setAttribute("lastModelLatencyMs", latencyMs);
        addTraceEntry(context, "modelCall", Map.of(
                "modelId", StringUtils.getOrDefault(modelId),
                "latencyMs", latencyMs,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 系统提示词注入追踪
     * @param context
     * @param prompt
     */
    public void onSystemPrompt(AgentContext context, String prompt) {
        log.debug("系统提示词注入: phase={}, promptLength={}",
                context.getAttribute("lastAgentPhase"),
                prompt != null ? prompt.length() : 0);
        context.setAttribute("lastSystemPrompt", prompt);
        addTraceEntry(context, "systemPrompt", Map.of(
                "promptLength", prompt != null ? prompt.length() : 0,
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 技能使用追踪
     * @param context
     * @param skillName
     * @param version
     * @param fingerprint
     */
    public void onSkillUsed(AgentContext context, String skillName, Integer version, String fingerprint) {
        log.info("技能使用: skill={}, version={}, fingerprint={}, traceId={}",
                skillName, version, fingerprint, context.getTraceId());
        addTraceEntry(context, "skillUsed", Map.of(
                "skillName", StringUtils.getOrDefault(skillName),
                "version", version != null ? version : 0,
                "fingerprint", StringUtils.getOrDefault(fingerprint),
                "timestamp", System.currentTimeMillis()
        ));
    }

    /**
     * 获取追踪条目
     * @param context
     * @return
     */
    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Object>> getTraceEntries(AgentContext context) {
        Object entries = context.getAttribute("traceEntries");
        if (entries instanceof Map) {
            return (Map<String, Map<String, Object>>) entries;
        }
        return new ConcurrentHashMap<>();
    }

    /**
     * 添加追踪条目
     * @param context
     * @param type
     * @param data
     */
    @SuppressWarnings("unchecked")
    private void addTraceEntry(AgentContext context, String type, Map<String, Object> data) {
        Object entries = context.getAttribute("traceEntries");
        if (!(entries instanceof Map)) {
            entries = new ConcurrentHashMap<String, Map<String, Object>>();
            context.setAttribute("traceEntries", entries);
        }
        Map<String, Map<String, Object>> traceEntries = (Map<String, Map<String, Object>>) entries;

        // 限制追踪条目数量
        if (traceEntries.size() >= MAX_TRACE_ENTRIES) {
            String oldestKey = traceEntries.keySet().iterator().next();
            traceEntries.remove(oldestKey);
        }

        String key = type + "-" + System.nanoTime();
        traceEntries.put(key, data);
    }
}
