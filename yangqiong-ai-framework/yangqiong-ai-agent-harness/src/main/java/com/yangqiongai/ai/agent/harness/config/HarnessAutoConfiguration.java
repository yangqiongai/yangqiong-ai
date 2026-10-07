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
package com.yangqiongai.ai.agent.harness.config;

import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.agent.harness.subagent.orchestration.AutoOrchestrationEngine;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentResultAggregator;
import com.yangqiongai.agent.harness.subagent.orchestration.SubagentSpecGenerator;
import com.yangqiongai.ai.agent.harness.HarnessModelFactoryBridge;
import com.yangqiongai.ai.agent.harness.AgentHarnessRuntimeFactory;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeFactory;
import com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.agent.runtime.model.spi.ModelCredentialResolver;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import java.util.List;

/**
 * Harness运行时自动配置
 * <p>
 * 当provider=harness时，自动注册harness自满足的模型工厂，
 * 无需依赖ai-agent-reference或AgentScope SDK即可连接大模型。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnClass(AgentRuntime.class)
@ConditionalOnProperty(prefix = "ai.agent.runtime", name = "provider", havingValue = "harness",matchIfMissing = true)
@EnableConfigurationProperties(AutoHarnessModelProperties.class)
public class HarnessAutoConfiguration {

    /**
     * 注册Harness内置模型工厂
     * <p>
     * 当外部未提供AgentModelFactory时，使用harness内置的多协议模型工厂，
     * 通过HarnessModelFactoryBridge桥接为framework的AgentModelFactory类型。
     * </p>
     * @param properties
     * @param registry
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AgentModelFactory.class)
    public AgentModelFactory harnessModelFactory(HarnessModelProperties properties,
                                                   AgentModelRegistry registry,
                                                   ObjectProvider<ModelInfoRepository> modelInfoRepositoryProvider,
                                                   ObjectProvider<ModelCredentialResolver> credentialResolverProvider,
                                                   ObjectProvider<com.yangqiongai.ai.common.scope.FeatureGuard> featureGuardProvider) {
        HarnessModelFactory harnessFactory = new HarnessModelFactory(properties, registry);
        return new HarnessModelFactoryBridge(harnessFactory, registry, properties,
                modelInfoRepositoryProvider.getIfAvailable(),
                credentialResolverProvider.getIfAvailable(),
                featureGuardProvider.getIfAvailable());
    }

    /**
     * 注册Harness运行时工厂
     * @param modelFactoryProvider
     * @param autoOrchestrationEnabled 自动编排全局开关
     * @param subagentMaxDepth 子代理最大嵌套深度
     * @param allowRecursiveOrchestration 是否允许递归编排
     * @param specGeneratorProvider 子代理声明生成器（可选，启用LLM动态任务分解）
     * @param maxSubagents LLM动态生成声明的最大子代理数
     * @param generationModelCode 声明生成使用的模型编码
     * @param longTermMemoryProvider L3长期记忆桥接器（可选，ai-memory在场时由HarnessMemoryAutoConfiguration注册）
     * @param summaryCompactionEnabled 是否启用LLM摘要压缩
     * @param retrievalEnabled 是否启用L3到L1检索注入
     * @param retrievalTopK L3到L1检索注入条目上限
     * @param toolLoadingMode 工具下发模式：FULL全量下发 / PROGRESSIVE渐进加载
     * @param alwaysOnToolsCsv 渐进模式常驻工具名单（逗号分隔）
     * @param traceEmitterProvider 追踪导出器（可选，多个时组合广播）
     * @param tokenBudgetPolicyProvider Token预算策略（可选，配额模块注入）
     * @param costBudgetPolicyProvider 成本预算策略（可选，配额模块注入）
     * @param modelRateLimiterProvider 模型调用限流器（可选，配额模块注入）
     * @return
     */
    @Bean
    public AgentRuntimeFactory harnessRuntimeFactory(ObjectProvider<AgentModelFactory> modelFactoryProvider,
                                                        @Value("${ai.agent.orchestration.auto.enabled:true}") boolean autoOrchestrationEnabled,
                                                        @Value("${ai.agent.orchestration.subagent.max-depth:3}") int subagentMaxDepth,
                                                        @Value("${ai.agent.orchestration.subagent.allow-recursive-orchestration:true}") boolean allowRecursiveOrchestration,
                                                        ObjectProvider<SubagentSpecGenerator> specGeneratorProvider,
                                                        @Value("${ai.agent.orchestration.dynamic.generation.max-subagents:5}") int maxSubagents,
                                                        @Value("${ai.agent.orchestration.dynamic.generation.model-code:}") String generationModelCode,
                                                        ObjectProvider<AgentLongTermMemory> longTermMemoryProvider,
                                                        @Value("${ai.agent.harness.memory.summary-compaction.enabled:false}") boolean summaryCompactionEnabled,
                                                        @Value("${ai.agent.harness.memory.retrieval.enabled:true}") boolean retrievalEnabled,
                                                        @Value("${ai.agent.harness.memory.retrieval.top-k:3}") int retrievalTopK,
                                                        @Value("${ai.agent.tool-loading.mode:FULL}") String toolLoadingMode,
                                                        @Value("${ai.agent.tool-loading.always-on-tools:}") String alwaysOnToolsCsv,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.trace.TraceEmitter> traceEmitterProvider,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy> tokenBudgetPolicyProvider,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy> costBudgetPolicyProvider,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.budget.RateLimiter> modelRateLimiterProvider,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate> toolPolicyGateProvider,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.event.AgentEventListener> eventListenerProvider,
                                                        ObjectProvider<com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener> contextSnapshotListenerProvider) {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        AgentModelFactory modelFactory = modelFactoryProvider.getIfAvailable();
        if (modelFactory != null) {
            factory.setModelFactory(modelFactory);
        }
        factory.setAutoOrchestrationEnabled(autoOrchestrationEnabled);
        factory.setSubagentMaxDepth(subagentMaxDepth);
        factory.setAllowRecursiveOrchestration(allowRecursiveOrchestration);
        SubagentSpecGenerator specGenerator = specGeneratorProvider.getIfAvailable();
        if (specGenerator != null) {
            factory.setSpecGenerator(specGenerator);
        }
        factory.setMaxSubagents(maxSubagents);
        if (generationModelCode != null && !generationModelCode.isBlank()) {
            factory.setGenerationModelCode(generationModelCode);
        }
        AgentLongTermMemory longTermMemory = longTermMemoryProvider.getIfAvailable();
        if (longTermMemory != null) {
            factory.setLongTermMemory(longTermMemory);
            factory.setMemoryRetrievalEnabled(retrievalEnabled);
            factory.setMemoryRetrievalTopK(retrievalTopK);
        }
        if (summaryCompactionEnabled) {
            factory.setSummaryCompactionEnabled(true);
        }
        factory.setToolLoadingMode(parseToolLoadingMode(toolLoadingMode));
        factory.setAlwaysOnTools(parseCsv(alwaysOnToolsCsv));
        List<com.yangqiongai.ai.agent.runtime.trace.TraceEmitter> traceEmitters =
                traceEmitterProvider.orderedStream().toList();
        if (traceEmitters.size() == 1) {
            factory.setTraceEmitter(traceEmitters.get(0));
        } else if (traceEmitters.size() > 1) {
            factory.setTraceEmitter(
                    new com.yangqiongai.ai.agent.runtime.trace.CompositeTraceEmitter(traceEmitters));
        }
        com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy tokenBudgetPolicy =
                tokenBudgetPolicyProvider.getIfAvailable();
        if (tokenBudgetPolicy != null) {
            factory.setTokenBudgetPolicy(tokenBudgetPolicy);
        }
        com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy costBudgetPolicy =
                costBudgetPolicyProvider.getIfAvailable();
        if (costBudgetPolicy != null) {
            factory.setCostBudgetPolicy(costBudgetPolicy);
        }
        com.yangqiongai.ai.agent.runtime.budget.RateLimiter modelRateLimiter =
                modelRateLimiterProvider.getIfAvailable();
        if (modelRateLimiter != null) {
            factory.setModelRateLimiter(modelRateLimiter);
        }
        com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate toolPolicyGate =
                toolPolicyGateProvider.getIfAvailable();
        if (toolPolicyGate != null) {
            factory.setToolPolicyGate(toolPolicyGate);
        }
        List<com.yangqiongai.ai.agent.runtime.event.AgentEventListener> eventListeners =
                eventListenerProvider.orderedStream().toList();
        if (!eventListeners.isEmpty()) {
            factory.setEventListeners(eventListeners);
        }
        com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener contextSnapshotListener =
                contextSnapshotListenerProvider.getIfAvailable();
        if (contextSnapshotListener != null) {
            factory.setContextSnapshotListener(contextSnapshotListener);
        }
        return factory;
    }

    /**
     * 解析工具下发模式配置，非法值回退FULL
     * @param mode 配置值
     * @return
     */
    private com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode parseToolLoadingMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode.FULL;
        }
        try {
            return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode
                    .valueOf(mode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder.ToolLoadingMode.FULL;
        }
    }

    /**
     * 解析逗号分隔工具名单为集合
     * @param csv 逗号分隔字符串
     * @return
     */
    private java.util.Set<String> parseCsv(String csv) {
        if (csv == null || csv.isBlank()) {
            return java.util.Set.of();
        }
        java.util.Set<String> names = new java.util.LinkedHashSet<>();
        for (String name : csv.split(",")) {
            if (!name.isBlank()) {
                names.add(name.trim());
            }
        }
        return names;
    }

    /**
     * 注册子代理结果汇总器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(SubagentResultAggregator.class)
    public SubagentResultAggregator subagentResultAggregator() {
        return new SubagentResultAggregator();
    }

    /**
     * 注册自动编排引擎
     * @param aggregator 子代理结果汇总器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(AutoOrchestrationEngine.class)
    public AutoOrchestrationEngine autoOrchestrationEngine(SubagentResultAggregator aggregator) {
        return new AutoOrchestrationEngine(aggregator);
    }

}
