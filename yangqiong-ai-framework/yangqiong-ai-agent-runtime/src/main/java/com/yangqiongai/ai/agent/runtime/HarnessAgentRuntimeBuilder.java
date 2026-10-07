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
package com.yangqiongai.ai.agent.runtime;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy;
import com.yangqiongai.ai.agent.runtime.budget.ModelPricing;
import com.yangqiongai.ai.agent.runtime.budget.RateLimiter;
import com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy;
import com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode;
import com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig;
import com.yangqiongai.ai.agent.runtime.config.OrchestrationStrategy;
import com.yangqiongai.ai.agent.runtime.durable.AgentRunStore;
import com.yangqiongai.ai.agent.runtime.durable.ApprovalStore;
import com.yangqiongai.ai.agent.runtime.durable.CheckpointManager;
import com.yangqiongai.ai.agent.runtime.durable.CheckpointStore;
import com.yangqiongai.ai.agent.runtime.durable.DistributedStores;
import com.yangqiongai.ai.agent.runtime.durable.RunLockStore;
import com.yangqiongai.ai.agent.runtime.durable.ToolExecutionStore;
import com.yangqiongai.ai.agent.runtime.event.AgentEventListener;
import com.yangqiongai.ai.agent.runtime.guardrail.ContentModerationPolicy;
import com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory;
import com.yangqiongai.ai.agent.runtime.memory.AgentSessionMemory;
import com.yangqiongai.ai.agent.runtime.memory.ForgettingPolicy;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import com.yangqiongai.ai.agent.runtime.orchestration.SubagentDeclaration;
import com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate;
import com.yangqiongai.ai.agent.runtime.rag.Retriever;
import com.yangqiongai.ai.agent.runtime.spi.AgentLoop;
import com.yangqiongai.ai.agent.runtime.spi.ParadigmSpec;
import com.yangqiongai.ai.agent.runtime.spi.RuntimeCustomizer;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.agent.runtime.tool.DocumentParser;
import com.yangqiongai.ai.agent.runtime.trace.TraceEmitter;
import com.yangqiongai.ai.agent.runtime.web.WebFetcher;
import com.yangqiongai.ai.agent.runtime.web.WebSearchProvider;

/**
 * Agent运行时构建器（引擎增强）
 * <p>
 * 扩展高级构建器，提供自研引擎支持的高级能力配置：执行控制、子代理编排、
 * 记忆子系统SPI、审批、护栏、预算、RAG/Web工具、持久化与分布式、可观测性与扩展点。
 * 所有方法均提供默认空实现，适配器按引擎能力覆盖，调用方面向本接口编程即可。
 * </p>
 * @author yangqiong
 */
public interface HarnessAgentRuntimeBuilder extends AdvancedAgentRuntimeBuilder {

    // ========== 执行控制 ==========

    /**
     * 设置Agent模型并携带模型编码，供引擎调用级用量记录归属真实模型
     * @param model
     * @param modelCode
     * @return
     */
    default HarnessAgentRuntimeBuilder model(AgentModel model, String modelCode) {
        model(model);
        return this;
    }

    /**
     * 设置单次Agent执行总超时
     * @param timeout 超时时间，null时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder timeout(Duration timeout) {
        return this;
    }

    /**
     * 设置总超时模式
     * @param mode WALL_CLOCK硬墙钟 / IDLE空闲超时，默认WALL_CLOCK
     * @return
     */
    default HarnessAgentRuntimeBuilder timeoutMode(TimeoutMode mode) {
        return this;
    }

    /**
     * 设置单轮推理+工具执行超时
     * @param timeout 超时时间，null时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder iterationTimeout(Duration timeout) {
        return this;
    }

    /**
     * 设置单次工具调用超时，防止单工具卡死阻塞循环
     * @param timeout 超时时间，默认60s
     * @return
     */
    default HarnessAgentRuntimeBuilder toolCallTimeout(Duration timeout) {
        return this;
    }

    /**
     * 设置最大并行工具调用数
     * @param max 最大并行数，默认1
     * @return
     */
    default HarnessAgentRuntimeBuilder maxConcurrentToolCalls(int max) {
        return this;
    }

    /**
     * 设置工具下发模式
     * <p>
     * PROGRESSIVE渐进加载仅自研引擎支持；agentscope运行时忽略此配置保持全量下发。
     * </p>
     * @param mode 工具下发模式，默认FULL
     * @return
     */
    default HarnessAgentRuntimeBuilder toolLoadingMode(ToolLoadingMode mode) {
        return this;
    }

    /**
     * 设置渐进模式下常驻工具名单（始终下发完整schema）
     * @param toolNames 始终下发完整schema的工具名集合，空集表示全部工具进延迟池
     * @return
     */
    default HarnessAgentRuntimeBuilder alwaysOnTools(Set<String> toolNames) {
        return this;
    }

    // ========== 子代理与编排 ==========

    /**
     * 设置子代理开关
     * @param enabled false时禁用子代理，配置声明后自动启用
     * @return
     */
    default HarnessAgentRuntimeBuilder subagentsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置静态子代理声明
     * @param declarations
     * @return
     */
    default HarnessAgentRuntimeBuilder subagentDeclarations(List<SubagentDeclaration> declarations) {
        return this;
    }

    /**
     * 设置LLM动态生成子代理开关
     * @param enabled 默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder dynamicSubagentsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置自动编排全局开关
     * @param enabled 默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder autoOrchestrationEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置计划模式开关
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder planModeEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置编排策略默认值，LLM未显式指定时生效并自动转译为编排提示词指令
     * @param strategy
     * @return
     */
    default HarnessAgentRuntimeBuilder orchestrationStrategy(OrchestrationStrategy strategy) {
        return this;
    }

    /**
     * 设置编排轮次默认值
     * @param rounds null时由LLM自行决定
     * @return
     */
    default HarnessAgentRuntimeBuilder orchestrationRounds(Integer rounds) {
        return this;
    }

    /**
     * 设置编排轮次上限，防止无限迭代
     * @param maxRounds 默认8
     * @return
     */
    default HarnessAgentRuntimeBuilder maxOrchestrationRounds(int maxRounds) {
        return this;
    }

    /**
     * 设置子代理最大嵌套深度
     * @param maxDepth 默认3
     * @return
     */
    default HarnessAgentRuntimeBuilder maxSubagentDepth(int maxDepth) {
        return this;
    }

    /**
     * 设置是否允许子代理递归编排
     * @param allowed 默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder allowRecursiveOrchestration(boolean allowed) {
        return this;
    }

    /**
     * 设置LLM动态生成子代理声明数上限
     * @param max 默认5
     * @return
     */
    default HarnessAgentRuntimeBuilder maxSubagents(int max) {
        return this;
    }

    /**
     * 设置子代理声明生成使用的模型编码，null时使用主模型
     * @param modelCode
     * @return
     */
    default HarnessAgentRuntimeBuilder generationModelCode(String modelCode) {
        return this;
    }

    /**
     * 注册Handoff目标，主代理可将控制权移交至目标运行时
     * @param name 目标名称
     * @param runtime 目标运行时
     * @return
     */
    default HarnessAgentRuntimeBuilder handoffTarget(String name, AgentRuntime runtime) {
        return this;
    }

    // ========== 记忆子系统 ==========

    /**
     * 注入L3长期记忆，启用后自动装配记忆工具/持久化钩子/检索注入
     * @param longTermMemory
     * @return
     */
    default HarnessAgentRuntimeBuilder longTermMemory(AgentLongTermMemory longTermMemory) {
        return this;
    }

    /**
     * 设置记忆工具开关（memory_search/memory_get/session_search）
     * @param enabled 默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryToolsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置记忆自动持久化钩子开关
     * @param enabled 默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryHooksEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置L3检索注入开关，仅配置长期记忆时生效
     * @param enabled 默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryRetrievalEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置检索注入条目上限
     * @param topK 默认3
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryRetrievalTopK(int topK) {
        return this;
    }

    /**
     * 设置LLM摘要式上下文压缩开关，与截断式压缩互斥，由引擎裁决
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder summaryCompactionEnabled(boolean enabled) {
        return this;
    }

    /**
     * 注入L2会话级短期记忆，null时使用引擎内置内存实现
     * @param sessionMemory
     * @return
     */
    default HarnessAgentRuntimeBuilder sessionMemory(AgentSessionMemory sessionMemory) {
        return this;
    }

    /**
     * 设置记忆遗忘策略，配合向量记忆清理过期条目
     * @param policy
     * @return
     */
    default HarnessAgentRuntimeBuilder memoryForgettingPolicy(ForgettingPolicy policy) {
        return this;
    }

    // ========== 上下文管理 ==========

    /**
     * 设置上下文窗口Token上限，超出时触发截断或压缩
     * @param maxTokens 最大Token数，0或不设置时由模型自行决定
     * @return
     */
    default HarnessAgentRuntimeBuilder maxContextTokens(int maxTokens) {
        return this;
    }

    /**
     * 设置历史截断策略
     * @param strategy 策略枚举：HEAD保留最早 / TAIL保留最新 / SUMMARY摘要压缩
     * @return
     */
    default HarnessAgentRuntimeBuilder historyTruncationStrategy(TruncationStrategy strategy) {
        return this;
    }

    // ========== 工具安全与审批 ==========

    /**
     * 设置工具白名单（仅允许调用的工具名称集合）
     * @param toolNames 工具名称集合，null或空时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder allowedTools(Set<String> toolNames) {
        return this;
    }

    /**
     * 设置工具黑名单（禁止调用的工具名称集合）
     * @param toolNames 工具名称集合，null或空时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder deniedTools(Set<String> toolNames) {
        return this;
    }

    /**
     * 设置需要人工审批才能执行的工具名称集合
     * @param toolNames 工具名称集合，null或空时不限制
     * @return
     */
    default HarnessAgentRuntimeBuilder requireApproval(Set<String> toolNames) {
        return this;
    }

    /**
     * 设置统一审批模式
     * @param mode MANUAL人工 / AUTO AI判定 / FULL_ACCESS全放行 / CUSTOM细粒度，默认CUSTOM
     * @return
     */
    default HarnessAgentRuntimeBuilder approvalMode(AgentApprovalMode mode) {
        return this;
    }

    /**
     * 设置AUTO模式的审批判定模型，必填校验在build时执行
     * @param judgeModel
     * @return
     */
    default HarnessAgentRuntimeBuilder approvalJudgeModel(AgentModel judgeModel) {
        return this;
    }

    /**
     * 设置AI判定失败的回退行为
     * @param fallbackAsk true回退人工审批 / false直接拒绝，默认true
     * @return
     */
    default HarnessAgentRuntimeBuilder aiApprovalFallbackAsk(boolean fallbackAsk) {
        return this;
    }

    /**
     * 设置AI审批附加策略描述，拼入判定提示词
     * @param guidance
     * @return
     */
    default HarnessAgentRuntimeBuilder aiApprovalGuidance(String guidance) {
        return this;
    }

    /**
     * 注入统一权限策略门，覆盖运行时按名单自动装配的默认策略
     * @param gate
     * @return
     */
    default HarnessAgentRuntimeBuilder toolPolicyGate(ToolPolicyGate gate) {
        return this;
    }

    /**
     * 注入工具执行记录存储，用于恢复场景跨节点幂等去重
     * @param store null时使用引擎内存实现
     * @return
     */
    default HarnessAgentRuntimeBuilder toolExecutionStore(ToolExecutionStore store) {
        return this;
    }

    /**
     * 设置工具失败后处理策略
     * @param strategy 策略枚举：RETRY重试 / SKIP跳过继续 / ABORT中止整个Agent
     * @return
     */
    default HarnessAgentRuntimeBuilder toolFailureStrategy(ToolFailureStrategy strategy) {
        return this;
    }

    /**
     * 设置单个工具最大重试次数
     * @param maxRetries 最大重试次数，默认0
     * @return
     */
    default HarnessAgentRuntimeBuilder maxToolRetries(int maxRetries) {
        return this;
    }

    /**
     * 设置工具结果最大字符数，超出截断并附加截断标记
     * @param maxChars 默认10000
     * @return
     */
    default HarnessAgentRuntimeBuilder maxToolResultChars(int maxChars) {
        return this;
    }

    /**
     * 设置最大连续工具失败次数，超限中止执行
     * @param maxFailures 默认3
     * @return
     */
    default HarnessAgentRuntimeBuilder maxConsecutiveToolFailures(int maxFailures) {
        return this;
    }

    // ========== 模型调用链 ==========

    /**
     * 设置模型调用指数退避重试配置
     * @param config null时不重试
     * @return
     */
    default HarnessAgentRuntimeBuilder modelRetryConfig(AgentModelRetryConfig config) {
        return this;
    }

    /**
     * 设置成本计价模型编码，对应定价注册
     * @param modelCode
     * @return
     */
    default HarnessAgentRuntimeBuilder modelCode(String modelCode) {
        return this;
    }

    /**
     * 添加自定义模型装饰层（熔断/审计/影子流量），注册顺序即外到内
     * @param layer
     * @return
     */
    default HarnessAgentRuntimeBuilder addModelCallerLayer(Function<AgentModel, AgentModel> layer) {
        return this;
    }

    /**
     * 设置上下文缓存（Anthropic协议cache_control）
     * @param enabled 总开关，默认关闭
     * @param cacheSystemPrompt 是否缓存系统提示词
     * @param cacheTools 是否缓存工具定义
     * @return
     */
    default HarnessAgentRuntimeBuilder contextCaching(boolean enabled, boolean cacheSystemPrompt, boolean cacheTools) {
        return this;
    }

    // ========== 预算与成本 ==========

    /**
     * 设置Token预算策略（真实usage累计告警+硬控）
     * @param policy
     * @return
     */
    default HarnessAgentRuntimeBuilder tokenBudgetPolicy(TokenBudgetPolicy policy) {
        return this;
    }

    /**
     * 设置成本预算策略（usage×定价累计）
     * @param policy
     * @return
     */
    default HarnessAgentRuntimeBuilder costBudgetPolicy(CostBudgetPolicy policy) {
        return this;
    }

    /**
     * 注册模型定价，未注册的模型按0计价
     * @param pricings
     * @return
     */
    default HarnessAgentRuntimeBuilder modelPricing(ModelPricing... pricings) {
        return this;
    }

    /**
     * 设置模型调用令牌桶限流（RPM级）
     * @param limiter
     * @return
     */
    default HarnessAgentRuntimeBuilder modelRateLimiter(RateLimiter limiter) {
        return this;
    }

    // ========== 安全护栏 ==========

    /**
     * 设置输入护栏开关（注入关键词/敏感词检测，命中即中止）
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder inputGuardrailEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置全链路工具护栏开关（参数+结果入规则链，结果附加围栏标记）
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder toolGuardrailEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置内容审查策略，null时放行
     * @param policy
     * @return
     */
    default HarnessAgentRuntimeBuilder contentModerationPolicy(ContentModerationPolicy policy) {
        return this;
    }

    // ========== 检索增强RAG与Web工具 ==========

    /**
     * 注册命名检索器，作为rag_search工具数据源
     * @param name 检索器名称
     * @param retriever
     * @return
     */
    default HarnessAgentRuntimeBuilder retriever(String name, Retriever retriever) {
        return this;
    }

    /**
     * 设置RAG自动检索注入中间件开关，仅对最后一条USER消息注入围栏结果
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder ragRetrievalEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置RAG自动检索条数
     * @param topK 默认3
     * @return
     */
    default HarnessAgentRuntimeBuilder ragTopK(int topK) {
        return this;
    }

    /**
     * 设置RAG注入模板前缀
     * @param template null时使用引擎默认模板
     * @return
     */
    default HarnessAgentRuntimeBuilder ragInjectionTemplate(String template) {
        return this;
    }

    /**
     * 设置语义缓存开关（嵌入相似度命中直接返回缓存响应，依赖embeddingModel）
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder semanticCachingEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置语义缓存命中相似度阈值
     * @param threshold
     * @return
     */
    default HarnessAgentRuntimeBuilder semanticCacheThreshold(double threshold) {
        return this;
    }

    /**
     * 设置语义缓存最大条目数
     * @param maxEntries
     * @return
     */
    default HarnessAgentRuntimeBuilder semanticCacheMaxEntries(int maxEntries) {
        return this;
    }

    /**
     * 设置Web工具强制启用，未配置提供方时调用返回未配置提示
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder webToolsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置Web检索提供方（web_search工具）
     * @param provider
     * @return
     */
    default HarnessAgentRuntimeBuilder webSearchProvider(WebSearchProvider provider) {
        return this;
    }

    /**
     * 设置网页抓取提供方（web_fetch工具）
     * @param fetcher
     * @return
     */
    default HarnessAgentRuntimeBuilder webFetcher(WebFetcher fetcher) {
        return this;
    }

    /**
     * 设置向用户提问工具开关（ask_user，模型缺信息时主动澄清）
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder askUserEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置文件工具开关（file_read/file_list沙箱读取）
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder fileToolsEnabled(boolean enabled) {
        return this;
    }

    /**
     * 设置文件工具箱参数
     * @param sandboxRoot 沙箱根目录，null时使用引擎默认值
     * @param parser 文档解析器，null时仅支持纯文本
     * @return
     */
    default HarnessAgentRuntimeBuilder fileToolkit(String sandboxRoot, DocumentParser parser) {
        return this;
    }

    // ========== 持久化与分布式 ==========

    /**
     * 一次性注入共享存储，单项setter注入优先于本聚合
     * @param stores
     * @return
     */
    default HarnessAgentRuntimeBuilder stores(DistributedStores stores) {
        return this;
    }

    /**
     * 注入运行记录存储（持久执行SPI一）
     * @param store
     * @return
     */
    default HarnessAgentRuntimeBuilder agentRunStore(AgentRunStore store) {
        return this;
    }

    /**
     * 注入完整检查点存储（SPI二）
     * @param store
     * @return
     */
    default HarnessAgentRuntimeBuilder checkpointStore(CheckpointStore store) {
        return this;
    }

    /**
     * 注入审批存储（SPI三）
     * @param store
     * @return
     */
    default HarnessAgentRuntimeBuilder approvalStore(ApprovalStore store) {
        return this;
    }

    /**
     * 注入检查点管理器（每轮快照，超限LRU淘汰）
     * @param manager
     * @return
     */
    default HarnessAgentRuntimeBuilder checkpointManager(CheckpointManager manager) {
        return this;
    }

    /**
     * 注入运行锁存储，防止多节点并发续跑
     * @param store
     * @return
     */
    default HarnessAgentRuntimeBuilder runLockStore(RunLockStore store) {
        return this;
    }

    /**
     * 设置本节点唯一标识，多节点部署必配
     * @param nodeId
     * @return
     */
    default HarnessAgentRuntimeBuilder nodeId(String nodeId) {
        return this;
    }

    // ========== 可观测性 ==========

    /**
     * 注册事件监听器，运行时事件自动广播
     * @param listener
     * @return
     */
    default HarnessAgentRuntimeBuilder addEventListener(AgentEventListener listener) {
        return this;
    }

    /**
     * 设置追踪导出器（span生命周期回调导出）
     * @param emitter
     * @return
     */
    default HarnessAgentRuntimeBuilder traceEmitter(TraceEmitter emitter) {
        return this;
    }

    /**
     * 设置上下文快照监听器（每次模型调用前回调采集快照）
     * @param listener
     * @return
     */
    default HarnessAgentRuntimeBuilder contextSnapshotListener(
            com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener listener) {
        return this;
    }

    // ========== 扩展点 ==========

    /**
     * 注册工具调用守卫（before拦截/after审计，多次调用累积）
     * @param guard
     * @return
     */
    default HarnessAgentRuntimeBuilder guard(com.yangqiongai.ai.agent.runtime.spi.ToolInvocationGuard guard) {
        return this;
    }

    /**
     * 注册运行时定制器，build前按order回调
     * @param customizer
     * @return
     */
    default HarnessAgentRuntimeBuilder addCustomizer(RuntimeCustomizer customizer) {
        return this;
    }

    /**
     * 替换默认ReAct执行循环（plan-and-execute/reflexion等范式）
     * @param loop
     * @return
     */
    default HarnessAgentRuntimeBuilder agentLoop(AgentLoop loop) {
        return this;
    }

    /**
     * 按执行范式规格替换默认ReAct执行循环
     * <p>
     * type为react、null或未识别值时不替换（走默认ReAct），保证配置错误不阻断对话主链路。
     * </p>
     * @param spec
     * @return
     */
    default HarnessAgentRuntimeBuilder executionParadigm(ParadigmSpec spec) {
        return this;
    }

    /**
     * 设置JDK SPI插件自动发现开关（中间件/工具/存储/检测器）
     * @param enabled 默认false
     * @return
     */
    default HarnessAgentRuntimeBuilder autoDiscoverEnabled(boolean enabled) {
        return this;
    }

    // ========== 枚举定义 ==========

    /**
     * 总超时模式
     */
    enum TimeoutMode {
        /**
         * 硬墙钟总超时：到点无论是否有输出都截断并发ERROR
         */
        WALL_CLOCK,
        /**
         * 空闲超时：仅在无事件发射超过超时时长时触发，持续出词不掐断
         */
        IDLE
    }

    /**
     * 工具失败处理策略
     */
    enum ToolFailureStrategy {
        /** 重试工具调用 */
        RETRY,
        /** 跳过失败工具，继续推理 */
        SKIP,
        /** 中止整个Agent执行 */
        ABORT
    }

    /**
     * 历史截断策略
     */
    enum TruncationStrategy {
        /** 保留最早的消息 */
        HEAD,
        /** 保留最新的消息 */
        TAIL,
        /** 对历史消息进行摘要压缩 */
        SUMMARY
    }

    /**
     * 工具下发模式
     */
    enum ToolLoadingMode {
        /** 全量下发（默认，全部工具schema随每轮模型请求下发） */
        FULL,
        /** 渐进加载：常驻工具 + 目录注入 + 按需启用（对齐skill渐进模式） */
        PROGRESSIVE
    }
}
