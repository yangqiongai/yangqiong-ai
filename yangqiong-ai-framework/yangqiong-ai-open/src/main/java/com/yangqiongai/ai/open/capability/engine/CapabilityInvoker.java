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
import com.yangqiongai.ai.agent.core.model.content.Base64InputSource;
import com.yangqiongai.ai.agent.core.model.content.ImageInputBlock;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextInputBlock;
import com.yangqiongai.ai.agent.core.model.content.UrlInputSource;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.open.capability.interaction.OpenInteractionPolicy;
import com.yangqiongai.ai.open.capability.spec.AgentOverrides;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    /**
     * arguments中图片Base64数据的约定键名
     */
    public static final String ARG_IMAGE_BASE64 = "imageBase64";

    /**
     * arguments中图片媒体类型的约定键名（缺省image/jpeg）
     */
    public static final String ARG_IMAGE_MEDIA_TYPE = "imageMediaType";

    /**
     * arguments中图片URL的约定键名
     */
    public static final String ARG_IMAGE_URL = "imageUrl";

    private final AgentEngine agentEngine;

    /**
     * 交互策略（可选，关闭交互语义处理，未注入时保持原有透传行为）
     */
    private final OpenInteractionPolicy interactionPolicy;

    public CapabilityInvoker(AgentEngine agentEngine) {
        this(agentEngine, null);
    }

    public CapabilityInvoker(AgentEngine agentEngine, OpenInteractionPolicy interactionPolicy) {
        this.agentEngine = agentEngine;
        this.interactionPolicy = interactionPolicy;
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
        boolean closedInteraction = isClosedInteraction(spec);
        AgentRequest agentRequest = buildAgentRequest(spec, prompt, request);
        if (timeoutSeconds > 0) {
            agentRequest.getBody().put("_timeout", timeoutSeconds);
        }
        if (closedInteraction) {
            interactionPolicy.registerInvocation(agentRequest.getSessionId(), null);
        } else if (interactionPolicy != null && interactionPolicy.isInteractionEnabled(spec)) {
            // 开启交互：登记调用上下文，供交互增强联动挂起登记（作用域以凭证绑定上下文为准）
            interactionPolicy.registerOpenInvocation(agentRequest.getSessionId(), null, ScopeContext.getScopeId());
        }
        try {
            AgentResult result = agentEngine.run(agentRequest);
            // 关闭交互兜底：同步调用中模型仍发起澄清时，转换为失败结果并给出明确文案
            if (closedInteraction && interactionPolicy.isSuspended(agentRequest.getSessionId())) {
                return AgentResult.failure(OpenInteractionPolicy.CLOSED_INTERACTION_MESSAGE);
            }
            return result;
        } finally {
            if (closedInteraction) {
                interactionPolicy.unregister(agentRequest.getSessionId());
            }
        }
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
        boolean closedInteraction = isClosedInteraction(spec);
        AgentRequest agentRequest = buildAgentRequest(spec, prompt, request);
        Flux<StreamEvent> flux = agentEngine.stream(agentRequest);
        if (closedInteraction) {
            String sessionId = agentRequest.getSessionId();
            interactionPolicy.registerInvocation(sessionId, null);
            // 关闭交互兜底：澄清/确认事件不透传，流截断后自然结束
            flux = interactionPolicy.filterSuspendEvents(flux)
                    .doFinally(signal -> interactionPolicy.unregister(sessionId));
        } else if (interactionPolicy != null && interactionPolicy.isInteractionEnabled(spec)) {
            // 开启交互：登记调用上下文，供交互增强联动挂起登记（作用域以凭证绑定上下文为准）
            interactionPolicy.registerOpenInvocation(agentRequest.getSessionId(), null, ScopeContext.getScopeId());
        }
        return flux;
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
        String taskId = agentEngine.submitTask(agentRequest);
        if (isClosedInteraction(spec)) {
            interactionPolicy.registerInvocation(agentRequest.getSessionId(), taskId);
        } else if (interactionPolicy != null && interactionPolicy.isInteractionEnabled(spec)) {
            // 开启交互：登记调用上下文，供交互增强联动挂起登记（作用域以凭证绑定上下文为准）
            interactionPolicy.registerOpenInvocation(agentRequest.getSessionId(), taskId, ScopeContext.getScopeId());
        }
        return taskId;
    }

    /**
     * 判断能力是否关闭交互（策略未注入时不拦截，保持原有透传行为）
     * @param spec 能力规格
     * @return
     */
    private boolean isClosedInteraction(CapabilitySpec spec) {
        return interactionPolicy != null && !interactionPolicy.isInteractionEnabled(spec);
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
        boolean closedInteraction = isClosedInteraction(spec);
        // 关闭交互时追加Prompt约束段，降低模型发起澄清的概率（非100%由兜底层保证不外发）
        if (closedInteraction) {
            String constraint = interactionPolicy.buildClosedConstraint();
            prompt = prompt == null || prompt.isBlank() ? constraint : prompt + constraint;
        }
        AgentRequest agentRequest = new AgentRequest()
                .agentCode(spec.getAgentCode())
                .input(buildInputBlocks(prompt, request))
                .userId(request.getCaller())
                .scopeId(ScopeContext.getScopeId())
                .sessionId(generateSessionId(request));
        // 注入Agent参数覆盖配置
        applyAgentOverrides(agentRequest, spec.getAgentOverrides());
        // 关闭交互时源头排除审批工具（LLM不可见审批工具即不会发起审批）
        if (closedInteraction) {
            interactionPolicy.applyClosedMode(agentRequest);
        }
        return agentRequest;
    }

    /**
     * 构建多模态输入块：arguments携带imageUrl/imageBase64时以图片块随Prompt文本透传给Agent，
     * 图片数据不参与Prompt模板渲染，避免兆级Base64进入文本
     * @param prompt 渲染后的Prompt
     * @param request 原始请求
     * @return
     */
    private List<InputBlock> buildInputBlocks(String prompt, CapabilityRequest request) {
        List<InputBlock> blocks = new ArrayList<>();
        if (prompt != null && !prompt.isBlank()) {
            blocks.add(new TextInputBlock(prompt));
        }
        Map<String, Object> arguments = request == null ? null : request.getArguments();
        if (arguments == null) {
            return blocks;
        }
        if (arguments.get(ARG_IMAGE_URL) instanceof String url && !url.isBlank()) {
            blocks.add(new ImageInputBlock(new UrlInputSource(url), null, null));
        }
        if (arguments.get(ARG_IMAGE_BASE64) instanceof String base64 && !base64.isBlank()) {
            String mediaType = arguments.get(ARG_IMAGE_MEDIA_TYPE) instanceof String mt && !mt.isBlank()
                    ? mt : "image/jpeg";
            blocks.add(new ImageInputBlock(new Base64InputSource(mediaType, base64), null, null));
        }
        return blocks;
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