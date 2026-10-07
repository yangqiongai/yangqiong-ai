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
package com.yangqiongai.ai.agent.observability.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import io.micrometer.core.instrument.MeterRegistry;

import com.yangqiongai.ai.agent.runtime.budget.ModelPricing;
import com.yangqiongai.ai.agent.runtime.budget.PricingProvider;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.AgentEventListener;
import com.yangqiongai.ai.agent.runtime.event.AgentEventType;
import com.yangqiongai.ai.agent.runtime.event.ModelCallEndInfo;
import com.yangqiongai.ai.agent.runtime.event.ModelCallStartInfo;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

/**
 * 指标采集事件监听器
 * <p>
 * 注册为Spring Bean后由 HarnessAutoConfiguration 装配进事件广播链，旁路消费Agent事件流，
 * 驱动运行/模型/工具/成本/审批全量指标。与主流程完全隔离：监听器异常不影响Agent执行。
 * </p>
 * @author yangqiong
 */
public final class MetricsEventListener implements AgentEventListener {

    /**
     * 关心的事件类型白名单
     */
    private static final Set<AgentEventType> INTERESTED = Set.of(
            AgentEventType.AGENT_START, AgentEventType.AGENT_END,
            AgentEventType.MODEL_CALL_START, AgentEventType.MODEL_CALL_END,
            AgentEventType.TOOL_CALL_START, AgentEventType.TOOL_CALL_END,
            AgentEventType.REQUIRE_USER_CONFIRM,
            AgentEventType.TOKEN_BUDGET_WARN, AgentEventType.TOKEN_BUDGET_EXCEEDED,
            AgentEventType.COST_BUDGET_WARN, AgentEventType.COST_BUDGET_EXCEEDED,
            AgentEventType.INTERRUPTED, AgentEventType.ERROR);

    /**
     * 指标集
     */
    private final AgentMetrics metrics;

    /**
     * 调用中悬置配对器
     */
    private final PendingCallTracker pendingCalls = new PendingCallTracker();

    /**
     * 活跃运行追踪器
     */
    private final ActiveRunTracker activeRuns = new ActiveRunTracker();

    /**
     * 模型定价提供方，null时跳过成本统计
     */
    private final PricingProvider pricingProvider;

    /**
     * 构造监听器
     * @param meterRegistry Micrometer指标注册表
     * @param pricingProvider 模型定价提供方，null时跳过成本指标
     */
    public MetricsEventListener(MeterRegistry meterRegistry, PricingProvider pricingProvider) {
        this.metrics = new AgentMetrics(meterRegistry);
        this.pricingProvider = pricingProvider;
    }

    /**
     * 仅处理白名单事件
     * @param type
     * @return
     */
    @Override
    public boolean isInterestedIn(AgentEventType type) {
        return INTERESTED.contains(type);
    }

    /**
     * 事件分发到各指标采集路径
     * @param event
     */
    @Override
    public void onEvent(AgentEvent event) {
        if (event == null) {
            return;
        }
        switch (event.getType()) {
            case AGENT_START -> onAgentStart(event);
            case AGENT_END -> onAgentEnd(event);
            case MODEL_CALL_START -> onModelCallStart(event);
            case MODEL_CALL_END -> onModelCallEnd(event);
            case TOOL_CALL_START -> onToolCallStart(event);
            case TOOL_CALL_END -> onToolCallEnd(event);
            case REQUIRE_USER_CONFIRM -> onApprovalRequested();
            case TOKEN_BUDGET_WARN, TOKEN_BUDGET_EXCEEDED,
                    COST_BUDGET_WARN, COST_BUDGET_EXCEEDED -> onBudgetWarning(event);
            case INTERRUPTED -> activeRuns.onInterrupted();
            case ERROR -> activeRuns.onError();
            default -> {
            }
        }
    }

    /**
     * 运行开始事件，载荷为Agent名称
     * @param event
     */
    private void onAgentStart(AgentEvent event) {
        if (event.getPayload() instanceof String agentName) {
            activeRuns.onRunStart(agentName);
            metrics.onRunStart();
        }
    }

    /**
     * 运行结束事件，载荷为Agent名称
     * @param event
     */
    private void onAgentEnd(AgentEvent event) {
        if (!(event.getPayload() instanceof String agentName)) {
            return;
        }
        ActiveRunTracker.RunEnd runEnd = activeRuns.takeRunEnd(agentName);
        if (runEnd != null) {
            metrics.onRunEnd(runEnd.agent(), runEnd.result(), runEnd.durationNanos());
            if (runEnd.approvalWaitNanos() > 0) {
                metrics.onApprovalWait(runEnd.agent(), runEnd.approvalWaitNanos());
            }
        }
    }

    /**
     * 模型调用开始事件，载荷为ModelCallStartInfo
     * @param event
     */
    private void onModelCallStart(AgentEvent event) {
        if (event.getPayload() instanceof ModelCallStartInfo info) {
            pendingCalls.onModelCallStart(info);
        }
    }

    /**
     * 模型调用结束事件，载荷为ModelCallEndInfo，含Token用量与成本换算
     * @param event
     */
    private void onModelCallEnd(AgentEvent event) {
        if (!(event.getPayload() instanceof ModelCallEndInfo info)) {
            return;
        }
        metrics.onModelCallEnd(info.getAgentName(), info.getModelName());
        Long duration = pendingCalls.takeModelDurationNanos(
                info.getAgentName(), info.getModelName(), info.getIteration());
        if (duration != null) {
            metrics.onModelLatency(info.getAgentName(), info.getModelName(), duration);
        }
        metrics.onTokens(info.getAgentName(), info.getModelName(), "prompt", info.getInputTokens());
        metrics.onTokens(info.getAgentName(), info.getModelName(), "completion", info.getOutputTokens());
        recordCost(info);
    }

    /**
     * 按定价表换算并累计本次调用成本
     * @param info
     */
    private void recordCost(ModelCallEndInfo info) {
        if (pricingProvider == null) {
            return;
        }
        ModelPricing pricing = pricingProvider.getPricing(info.getModelName());
        if (pricing == null) {
            return;
        }
        double cost = info.getInputTokens() / 1000.0 * pricing.promptUsdPer1k()
                + info.getOutputTokens() / 1000.0 * pricing.completionUsdPer1k();
        metrics.onCost(info.getAgentName(), info.getModelName(), cost);
    }

    /**
     * 工具调用开始事件，载荷为AgentToolUseBlock列表
     * @param event
     */
    private void onToolCallStart(AgentEvent event) {
        if (event.getPayload() instanceof List<?> list && !list.isEmpty()
                && list.get(0) instanceof AgentToolUseBlock) {
            @SuppressWarnings("unchecked")
            List<AgentToolUseBlock> blocks = (List<AgentToolUseBlock>) list;
            pendingCalls.onToolCallStart(blocks);
        }
    }

    /**
     * 工具调用结束事件，兼容两种载荷：AgentToolResultBlock列表（历史约定）
     * 与AgentMessage列表（引擎实际载荷，工具结果块位于消息content中）
     * @param event
     */
    private void onToolCallEnd(AgentEvent event) {
        List<AgentToolResultBlock> blocks = extractToolResultBlocks(event.getPayload());
        if (blocks.isEmpty()) {
            return;
        }
        for (PendingCallTracker.ToolOutcome outcome : pendingCalls.takeToolOutcomes(blocks)) {
            metrics.onToolCall(outcome.toolName(), outcome.error(), outcome.durationNanos());
        }
    }

    /**
     * 从事件载荷中提取工具结果块列表
     * @param payload
     * @return
     */
    private List<AgentToolResultBlock> extractToolResultBlocks(Object payload) {
        if (!(payload instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        Object first = list.get(0);
        if (first instanceof AgentToolResultBlock) {
            @SuppressWarnings("unchecked")
            List<AgentToolResultBlock> blocks = (List<AgentToolResultBlock>) list;
            return blocks;
        }
        if (first instanceof AgentMessage) {
            List<AgentToolResultBlock> blocks = new ArrayList<>();
            for (Object item : list) {
                AgentMessage message = (AgentMessage) item;
                if (message == null || message.getContent() == null) {
                    continue;
                }
                for (com.yangqiongai.ai.agent.runtime.message.AgentContentBlock block : message.getContent()) {
                    if (block instanceof AgentToolResultBlock resultBlock) {
                        blocks.add(resultBlock);
                    }
                }
            }
            return blocks;
        }
        return List.of();
    }

    /**
     * 审批请求事件：计数并开启等待计时
     */
    private void onApprovalRequested() {
        activeRuns.onApprovalRequested();
        List<String> agents = activeRuns.activeAgents();
        if (agents.isEmpty()) {
            metrics.onApprovalRequested("unknown");
        } else {
            agents.forEach(metrics::onApprovalRequested);
        }
    }

    /**
     * 预算告警/超限事件：按预算类型与级别计数
     * @param event
     */
    private void onBudgetWarning(AgentEvent event) {
        String name = event.getType().name();
        metrics.onBudgetWarning(name.startsWith("TOKEN") ? "TOKEN" : "COST",
                name.endsWith("EXCEEDED") ? "EXCEEDED" : "WARN");
    }
}
