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
package com.yangqiongai.ai.open.capability.engine;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.open.capability.spec.AgentOverrides;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 能力调用器
 * <p>
 * 将CapabilityRequest转换为AgentRequest，调用现有AgentEngine执行。
 * 复用现有的AgentProcessor路由机制，不引入新的Agent调用模型。
 * 当CapabilitySpec配置了agentOverrides时，通过AgentRequest的body参数传递覆盖配置，
 * 由AgentProcessor在执行时合并到Agent配置中。
 * </p>
 * @author yangqiong
 */
public class CapabilityInvoker {

    private static final Logger log = LoggerFactory.getLogger(CapabilityInvoker.class);

    private final AgentEngine agentEngine;

    public CapabilityInvoker(AgentEngine agentEngine) {
        this.agentEngine = agentEngine;
    }

    /**
     * 执行能力
     * @param spec 能力规格
     * @param prompt 渲染后的Prompt
     * @param request 原始请求
     * @return Agent执行结果
     */
    public AgentResult execute(CapabilitySpec spec, String prompt, CapabilityRequest request) {
        return execute(spec, prompt, request, 0);
    }

    /**
     * 执行能力（带超时）
     * @param spec 能力规格
     * @param prompt 渲染后的Prompt
     * @param request 原始请求
     * @param timeoutSeconds 超时秒数（0表示不限制）
     * @return Agent执行结果
     */
    public AgentResult execute(CapabilitySpec spec, String prompt, CapabilityRequest request,
                                long timeoutSeconds) {
        // 工作流执行体为企业版能力，社区版拒绝执行
        if (isWorkflowExecutor(spec)) {
            return AgentResult.failure("工作流执行体为企业版能力，社区版不支持调用: " + spec.getCode());
        }
        AgentRequest agentRequest = buildAgentRequest(spec, prompt, request);
        if (timeoutSeconds > 0) {
            agentRequest.getBody().put("_timeout", timeoutSeconds);
        }
        return agentEngine.run(agentRequest);
    }

    /**
     * 流式执行
     * @param spec
     * @param prompt
     * @param request
     * @return
     */
    public Flux<StreamEvent> stream(CapabilitySpec spec, String prompt, CapabilityRequest request) {
        if (isWorkflowExecutor(spec)) {
            return Flux.error(new UnsupportedOperationException(
                    "工作流执行体为企业版能力，社区版不支持流式调用: " + spec.getCode()));
        }
        AgentRequest agentRequest = buildAgentRequest(spec, prompt, request);
        return agentEngine.stream(agentRequest);
    }

    /**
     * 异步提交
     * @param spec
     * @param prompt
     * @param request
     * @return 任务ID
     */
    public String submitAsync(CapabilitySpec spec, String prompt, CapabilityRequest request) {
        if (isWorkflowExecutor(spec)) {
            throw new UnsupportedOperationException(
                    "工作流执行体为企业版能力，社区版不支持异步调用: " + spec.getCode());
        }
        AgentRequest agentRequest = buildAgentRequest(spec, prompt, request);
        return agentEngine.submitTask(agentRequest);
    }

    /**
     * 判断能力是否绑定工作流执行体
     * @param spec 能力规格
     * @return
     */
    private boolean isWorkflowExecutor(CapabilitySpec spec) {
        return spec != null && "WORKFLOW".equalsIgnoreCase(spec.getExecType());
    }

    /**
     * 查询异步任务状态
     * @param taskId
     * @return
     */
    public Map<String, Object> queryTask(String taskId) {
        return agentEngine.queryTask(taskId);
    }

    /**
     * 构建AgentRequest（统一构建逻辑，避免重复）
     */
    private AgentRequest buildAgentRequest(CapabilitySpec spec, String prompt, CapabilityRequest request) {
        AgentRequest agentRequest = new AgentRequest()
                .agentCode(spec.getAgentCode())
                .input(prompt)
                .userId(request.getCaller())
                .scopeId(request.getScopeId())
                .sessionId(generateSessionId(request));
        // 注入Agent参数覆盖配置
        applyAgentOverrides(agentRequest, spec.getAgentOverrides());
        return agentRequest;
    }

    /**
     * 注入Agent参数覆盖配置到AgentRequest.body
     * <p>
     * 通过body传递覆盖配置，由AgentProcessor在加载ai_agent配置后合并。
     * 约定body中的 _agentOverrides 键存储覆盖配置。
     * </p>
     */
    private void applyAgentOverrides(AgentRequest agentRequest, AgentOverrides overrides) {
        if (overrides == null) {
            return;
        }
        Map<String, Object> overrideMap = new HashMap<>();
        if (overrides.getModel() != null) {
            overrideMap.put("model", overrides.getModel());
        }
        if (overrides.getTemperature() != null) {
            overrideMap.put("temperature", overrides.getTemperature());
        }
        if (overrides.getMaxTokens() != null) {
            overrideMap.put("maxTokens", overrides.getMaxTokens());
        }
        if (overrides.getMaxIterations() != null) {
            overrideMap.put("maxIterations", overrides.getMaxIterations());
        }
        if (overrides.getTools() != null && !overrides.getTools().isEmpty()) {
            overrideMap.put("toolWhitelist", overrides.getTools());
        }
        if (overrides.getSkills() != null && !overrides.getSkills().isEmpty()) {
            agentRequest.getBody().put("skillIds", overrides.getSkills());
            if (overrides.isReplaceSkills()) {
                agentRequest.getBody().put("_replaceSkills", true);
            }
        }
        if (overrides.getKnowledgeBase() != null) {
            Map<String, Object> kbOverride = new HashMap<>();
            kbOverride.put("kbCodes", overrides.getKnowledgeBase().getKbCodes());
            kbOverride.put("topK", overrides.getKnowledgeBase().getTopK());
            kbOverride.put("scoreThreshold", overrides.getKnowledgeBase().getScoreThreshold());
            overrideMap.put("knowledgeBase", kbOverride);
        }
        if (!overrideMap.isEmpty()) {
            agentRequest.getBody().put("_agentOverrides", overrideMap);
        }
    }

    private String generateSessionId(CapabilityRequest request) {
        String prefix = "cap-" + request.getCapability() + "-";
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}