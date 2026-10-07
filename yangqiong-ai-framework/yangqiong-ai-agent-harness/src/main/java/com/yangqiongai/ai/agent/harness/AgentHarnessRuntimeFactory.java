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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.HarnessRuntimeFactory;
import com.yangqiongai.agent.harness.memory.InMemorySessionMemory;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentSpecGenerator;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeFactory;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.durable.DistributedStores;

/**
 * Agent运行时工厂
 * @author yangqiong
 */
public class AgentHarnessRuntimeFactory implements AgentRuntimeFactory {

    /**
     * 独立引擎运行时工厂委托
     */
    private final HarnessRuntimeFactory delegate;

    /**
     * L3长期记忆存储（可选，注入后每个Agent自动携带记忆工具与持久化钩子）
     */
    private com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory longTermMemory;

    /**
     * 是否启用基于LLM摘要的上下文压缩
     */
    private boolean summaryCompactionEnabled;

    /**
     * 是否启用L3到L1检索注入
     */
    private boolean memoryRetrievalEnabled = true;

    /**
     * L3到L1检索注入条目上限
     */
    private int memoryRetrievalTopK = 3;

    /**
     * 工具下发模式（null时按FULL全量下发）
     */
    private HarnessAgentRuntimeBuilder.ToolLoadingMode toolLoadingMode;

    /**
     * 渐进模式下常驻工具名单
     */
    private java.util.Set<String> alwaysOnTools;

    /**
     * 追踪导出器（null时不采集）
     */
    private com.yangqiongai.ai.agent.runtime.trace.TraceEmitter traceEmitter;

    /**
     * Token预算策略（null时不启用run内Token硬控）
     */
    private com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy tokenBudgetPolicy;

    /**
     * 成本预算策略（null时不启用run内成本硬控）
     */
    private com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy costBudgetPolicy;

    /**
     * 模型调用限流器（null时不启用RPM限流）
     */
    private com.yangqiongai.ai.agent.runtime.budget.RateLimiter modelRateLimiter;

    /**
     * 统一权限策略门（null时使用运行时按名单自动装配的默认策略）
     */
    private com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate toolPolicyGate;

    /**
     * 运行时事件监听器列表（空时不挂载）
     */
    private java.util.List<com.yangqiongai.ai.agent.runtime.event.AgentEventListener> eventListeners;

    /**
     * 上下文快照监听器（null时不挂载）
     */
    private com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener contextSnapshotListener;

    /**
     * 分布式共享存储（null时使用引擎内存实现）
     */
    private DistributedStores distributedStores;

    /**
     * 本节点唯一标识（null时分布式锁归属节点不可用）
     */
    private String nodeId;

    public AgentHarnessRuntimeFactory() {
        this.delegate = new HarnessRuntimeFactory();
    }

    /**
     * 创建运行时构建器
     * @return
     */
    @Override
    public HarnessAgentRuntimeBuilder createBuilder() {
        HarnessRuntimeBuilder harnessBuilder =
                (HarnessRuntimeBuilder) delegate.createBuilder();
        AgentHarnessRuntimeBuilder builder = new AgentHarnessRuntimeBuilder(harnessBuilder);
        if (distributedStores != null) {
            builder.stores(distributedStores);
        }
        if (nodeId != null && !nodeId.isBlank()) {
            builder.nodeId(nodeId);
        }
        if (longTermMemory != null) {
            builder.longTermMemory(longTermMemory);
            builder.memoryRetrievalEnabled(memoryRetrievalEnabled);
            builder.memoryRetrievalTopK(memoryRetrievalTopK);
        }
        if (summaryCompactionEnabled) {
            builder.summaryCompactionEnabled(true);
            builder.getDelegate().sessionMemory(new InMemorySessionMemory());
        }
        if (toolLoadingMode != null) {
            builder.toolLoadingMode(toolLoadingMode);
        }
        if (alwaysOnTools != null && !alwaysOnTools.isEmpty()) {
            builder.alwaysOnTools(alwaysOnTools);
        }
        if (traceEmitter != null) {
            builder.traceEmitter(traceEmitter);
        }
        if (contextSnapshotListener != null) {
            builder.contextSnapshotListener(contextSnapshotListener);
        }
        if (tokenBudgetPolicy != null) {
            builder.tokenBudgetPolicy(tokenBudgetPolicy);
        }
        if (costBudgetPolicy != null) {
            builder.costBudgetPolicy(costBudgetPolicy);
        }
        if (modelRateLimiter != null) {
            builder.modelRateLimiter(modelRateLimiter);
        }
        if (toolPolicyGate != null) {
            builder.toolPolicyGate(toolPolicyGate);
        }
        if (eventListeners != null && !eventListeners.isEmpty()) {
            eventListeners.forEach(builder::addEventListener);
        }
        return builder;
    }

    /**
     * 设置运行时事件监听器列表
     * @param eventListeners
     */
    public void setEventListeners(java.util.List<com.yangqiongai.ai.agent.runtime.event.AgentEventListener> eventListeners) {
        this.eventListeners = eventListeners;
    }

    /**
     * 设置分布式共享存储
     * @param distributedStores
     */
    public void setDistributedStores(DistributedStores distributedStores) {
        this.distributedStores = distributedStores;
    }

    /**
     * 设置本节点唯一标识
     * @param nodeId
     */
    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    /**
     * 设置追踪导出器
     * @param traceEmitter
     */
    public void setTraceEmitter(com.yangqiongai.ai.agent.runtime.trace.TraceEmitter traceEmitter) {
        this.traceEmitter = traceEmitter;
    }

    /**
     * 设置上下文快照监听器
     * @param contextSnapshotListener
     */
    public void setContextSnapshotListener(
            com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener contextSnapshotListener) {
        this.contextSnapshotListener = contextSnapshotListener;
    }

    /**
     * 设置Token预算策略
     * @param tokenBudgetPolicy
     */
    public void setTokenBudgetPolicy(com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy tokenBudgetPolicy) {
        this.tokenBudgetPolicy = tokenBudgetPolicy;
    }

    /**
     * 设置成本预算策略
     * @param costBudgetPolicy
     */
    public void setCostBudgetPolicy(com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy costBudgetPolicy) {
        this.costBudgetPolicy = costBudgetPolicy;
    }

    /**
     * 设置模型调用限流器
     * @param modelRateLimiter 模型调用限流器
     */
    public void setModelRateLimiter(com.yangqiongai.ai.agent.runtime.budget.RateLimiter modelRateLimiter) {
        this.modelRateLimiter = modelRateLimiter;
    }

    /**
     * 设置统一权限策略门
     * @param toolPolicyGate 策略门
     */
    public void setToolPolicyGate(com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate toolPolicyGate) {
        this.toolPolicyGate = toolPolicyGate;
    }

    /**
     * 设置工具下发模式
     * @param toolLoadingMode FULL全量下发 / PROGRESSIVE渐进加载，null按FULL处理
     */
    public void setToolLoadingMode(HarnessAgentRuntimeBuilder.ToolLoadingMode toolLoadingMode) {
        this.toolLoadingMode = toolLoadingMode;
    }

    /**
     * 设置渐进模式下常驻工具名单
     * @param alwaysOnTools 工具名集合
     */
    public void setAlwaysOnTools(java.util.Set<String> alwaysOnTools) {
        this.alwaysOnTools = alwaysOnTools;
    }

    /**
     * 设置L3长期记忆存储
     * @param longTermMemory
     */
    public void setLongTermMemory(com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory longTermMemory) {
        this.longTermMemory = longTermMemory;
    }

    /**
     * 设置是否启用摘要压缩
     * @param summaryCompactionEnabled
     */
    public void setSummaryCompactionEnabled(boolean summaryCompactionEnabled) {
        this.summaryCompactionEnabled = summaryCompactionEnabled;
    }

    /**
     * 设置是否启用L3到L1检索注入
     * @param memoryRetrievalEnabled
     */
    public void setMemoryRetrievalEnabled(boolean memoryRetrievalEnabled) {
        this.memoryRetrievalEnabled = memoryRetrievalEnabled;
    }

    /**
     * 设置L3到L1检索注入条目上限
     * @param memoryRetrievalTopK
     */
    public void setMemoryRetrievalTopK(int memoryRetrievalTopK) {
        this.memoryRetrievalTopK = memoryRetrievalTopK;
    }

    /**
     * 设置模型工厂
     * @param modelFactory
     */
    public void setModelFactory(com.yangqiongai.ai.agent.runtime.model.AgentModelFactory modelFactory) {
        if (modelFactory != null) {
            delegate.setModelFactory(new AgentModelFactoryAdapter(modelFactory));
        }
    }

    /**
     * 设置自动编排开关
     * @param autoOrchestrationEnabled
     */
    public void setAutoOrchestrationEnabled(boolean autoOrchestrationEnabled) {
        delegate.setAutoOrchestrationEnabled(autoOrchestrationEnabled);
    }

    /**
     * 设置最大嵌套深度
     * @param subagentMaxDepth
     */
    public void setSubagentMaxDepth(int subagentMaxDepth) {
        delegate.setSubagentMaxDepth(subagentMaxDepth);
    }

    /**
     * 设置是否允许递归编排
     * @param allowRecursiveOrchestration
     */
    public void setAllowRecursiveOrchestration(boolean allowRecursiveOrchestration) {
        delegate.setAllowRecursiveOrchestration(allowRecursiveOrchestration);
    }

    /**
     * 设置子代理声明生成器
     * @param specGenerator
     */
    public void setSpecGenerator(SubagentSpecGenerator specGenerator) {
        delegate.setSpecGenerator(specGenerator);
    }

    /**
     * 设置最大子代理数
     * @param maxSubagents
     */
    public void setMaxSubagents(int maxSubagents) {
        delegate.setMaxSubagents(maxSubagents);
    }

    /**
     * 设置声明生成模型编码
     * @param generationModelCode
     */
    public void setGenerationModelCode(String generationModelCode) {
        delegate.setGenerationModelCode(generationModelCode);
    }

    /**
     * 获取被包装的独立引擎工厂
     * @return
     */
    HarnessRuntimeFactory getDelegate() {
        return delegate;
    }
}
