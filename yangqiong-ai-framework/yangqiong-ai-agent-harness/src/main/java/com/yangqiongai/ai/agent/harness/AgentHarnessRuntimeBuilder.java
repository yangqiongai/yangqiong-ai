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
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.budget.CostBudgetPolicy;
import com.yangqiongai.ai.agent.runtime.budget.ModelPricing;
import com.yangqiongai.ai.agent.runtime.budget.RateLimiter;
import com.yangqiongai.ai.agent.runtime.budget.TokenBudgetPolicy;
import com.yangqiongai.ai.agent.runtime.config.AgentApprovalMode;
import com.yangqiongai.ai.agent.runtime.config.AgentCompactionConfig;
import com.yangqiongai.ai.agent.runtime.config.AgentMemoryConfig;
import com.yangqiongai.ai.agent.runtime.config.AgentModelRetryConfig;
import com.yangqiongai.ai.agent.runtime.config.OrchestrationStrategy;
import com.yangqiongai.ai.agent.runtime.config.AgentPermissionContextState;
import com.yangqiongai.ai.agent.runtime.config.AgentPermissionMode;
import com.yangqiongai.ai.agent.runtime.config.AgentPermissionRule;
import com.yangqiongai.ai.agent.runtime.config.AgentResponseFormat;
import com.yangqiongai.ai.agent.runtime.config.AgentToolChoice;
import com.yangqiongai.ai.agent.runtime.config.AgentToolResultEvictionConfig;
import com.yangqiongai.ai.agent.runtime.durable.ApprovalStore;
import com.yangqiongai.ai.agent.runtime.durable.CheckpointManager;
import com.yangqiongai.ai.agent.runtime.durable.CheckpointStore;
import com.yangqiongai.ai.agent.runtime.durable.DistributedStores;
import com.yangqiongai.ai.agent.runtime.durable.RunLockStore;
import com.yangqiongai.ai.agent.runtime.durable.ToolExecutionStore;
import com.yangqiongai.ai.agent.runtime.durable.AgentRunStore;
import com.yangqiongai.ai.agent.runtime.event.AgentEventListener;
import com.yangqiongai.ai.agent.runtime.guardrail.ContentModerationPolicy;
import com.yangqiongai.ai.agent.runtime.memory.AgentSessionMemory;
import com.yangqiongai.ai.agent.runtime.memory.ForgettingPolicy;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import com.yangqiongai.ai.agent.runtime.orchestration.SubagentDeclaration;
import com.yangqiongai.ai.agent.runtime.permission.ToolPolicyGate;
import com.yangqiongai.ai.agent.runtime.rag.Retriever;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.spi.AgentLoop;
import com.yangqiongai.ai.agent.runtime.spi.ParadigmSpec;
import com.yangqiongai.ai.agent.runtime.spi.RuntimeCustomizer;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;
import com.yangqiongai.ai.agent.runtime.tool.DocumentParser;
import com.yangqiongai.ai.agent.runtime.trace.TraceEmitter;
import com.yangqiongai.ai.agent.runtime.web.WebFetcher;
import com.yangqiongai.ai.agent.runtime.web.WebSearchProvider;
import com.yangqiongai.agent.harness.paradigms.PlanExecuteEngine;
import com.yangqiongai.agent.harness.paradigms.ReWooEngine;
import com.yangqiongai.agent.harness.paradigms.ReflexionEngine;
import com.yangqiongai.agent.harness.paradigms.RouterEngine;
import com.yangqiongai.agent.harness.paradigms.SelfAskEngine;
import com.yangqiongai.agent.harness.paradigms.SelfRefineEngine;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;

/**
 * Agent运行时构建器
 * <p>
 * 委托独立引擎HarnessRuntimeBuilder，框架防腐类型在此转换为引擎类型。
 * </p>
 * @author yangqiong
 */
public class AgentHarnessRuntimeBuilder implements HarnessAgentRuntimeBuilder {

    /**
     * 日志
     */
    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(AgentHarnessRuntimeBuilder.class);

    /**
     * 独立引擎构建器委托
     */
    private final HarnessRuntimeBuilder delegate;

    /**
     * RAG自动检索条数
     */
    private int ragTopK = 3;

    /**
     * RAG注入模板前缀
     */
    private String ragInjectionTemplate;

    /**
     * 语义缓存命中相似度阈值
     */
    private double semanticCacheThreshold;

    /**
     * 语义缓存最大条目数
     */
    private int semanticCacheMaxEntries;

    /**
     * 文件工具沙箱根目录
     */
    private String fileSandboxRoot;

    /**
     * 文件工具文档解析器
     */
    private DocumentParser fileParser;

    public AgentHarnessRuntimeBuilder(HarnessRuntimeBuilder delegate) {
        this.delegate = delegate;
    }

    /**
     * 设置Agent名称
     * @param name
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder name(String name) {
        delegate.name(name);
        return this;
    }

    /**
     * 设置Agent模型
     * @param model
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder model(AgentModel model) {
        if (model != null) {
            delegate.model(new AgentModelAdapter(model));
        }
        return this;
    }

    /**
     * 设置Agent模型并携带模型编码，供调用级用量记录归属模型
     * @param model
     * @param modelCode
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder model(AgentModel model, String modelCode) {
        if (model != null) {
            delegate.model(new AgentModelAdapter(model, modelCode));
        }
        return this;
    }

    /**
     * 设置系统提示词
     * @param systemPrompt
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder systemPrompt(String systemPrompt) {
        delegate.systemPrompt(systemPrompt);
        return this;
    }

    /**
     * 设置最大迭代次数
     * @param maxIters
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxIters(int maxIters) {
        delegate.maxIters(maxIters);
        return this;
    }

    /**
     * 设置工具箱
     * @param toolkit
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolkit(AgentToolkit toolkit) {
        if (toolkit != null) {
            delegate.toolkit(new AgentToolkitAdapter(toolkit));
        } else {
            delegate.toolkit(null);
        }
        return this;
    }

    /**
     * 设置中间件
     * @param middleware
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder middleware(AgentMiddleware middleware) {
        if (middleware != null) {
            delegate.middleware(new AgentMiddlewareAdapter(middleware));
        }
        return this;
    }

    /**
     * 设置模型生成选项
     * @param options
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder generateOptions(AgentGenerateOptions options) {
        if (options != null) {
            delegate.generateOptions(RuntimeTypeConverter.toHarness(options));
        }
        return this;
    }

    /**
     * 设置技能箱
     * @param skillBox
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder skillBox(AgentSkillBox skillBox) {
        if (skillBox != null) {
            delegate.skillBox(new AgentSkillBoxAdapter(skillBox));
        }
        return this;
    }

    /**
     * 设置响应格式
     * @param format
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder responseFormat(AgentResponseFormat format) {
        if (format != null) {
            delegate.responseFormat(RuntimeTypeConverter.toHarness(format));
        }
        return this;
    }

    /**
     * 设置结构化输出类型
     * @param type
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder structuredOutputType(Class<?> type) {
        delegate.structuredOutputType(type);
        return this;
    }

    /**
     * 设置权限模式与规则
     * @param mode
     * @param rule
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder permission(AgentPermissionMode mode, AgentPermissionRule rule) {
        delegate.permission(RuntimeTypeConverter.toHarness(mode), RuntimeTypeConverter.toHarness(rule));
        return this;
    }

    /**
     * 设置权限上下文状态
     * @param state
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder permissionContextState(AgentPermissionContextState state) {
        if (state != null) {
            delegate.permissionContextState(RuntimeTypeConverter.toHarness(state));
        }
        return this;
    }

    /**
     * 设置记忆配置
     * @param config
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder memoryConfig(AgentMemoryConfig config) {
        if (config != null) {
            delegate.memoryConfig(RuntimeTypeConverter.toHarness(config));
        }
        return this;
    }

    /**
     * 设置压缩配置
     * @param config
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder compactionConfig(AgentCompactionConfig config) {
        if (config != null) {
            delegate.compactionConfig(RuntimeTypeConverter.toHarness(config));
        }
        return this;
    }

    /**
     * 设置工具结果驱逐配置
     * @param config
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolResultEvictionConfig(AgentToolResultEvictionConfig config) {
        if (config != null) {
            delegate.toolResultEvictionConfig(RuntimeTypeConverter.toHarness(config));
        }
        return this;
    }

    /**
     * 设置工具选择策略
     * @param choice
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolChoice(AgentToolChoice choice) {
        if (choice != null) {
            delegate.toolChoice(RuntimeTypeConverter.toHarness(choice));
        }
        return this;
    }

    /**
     * 设置子代理开关（默认启用）
     * @param enabled false时禁用子代理
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder subagentsEnabled(boolean enabled) {
        if (!enabled) {
            delegate.disableSubagents();
        } else {
            delegate.enableSubagents();
        }
        return this;
    }

    /**
     * 设置动态子代理开关（默认启用）
     * @param enabled false时禁用动态子代理
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder dynamicSubagentsEnabled(boolean enabled) {
        delegate.dynamicSubagentsEnabled(enabled);
        return this;
    }

    /**
     * 设置自动编排全局开关
     * @param enabled 默认true
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder autoOrchestrationEnabled(boolean enabled) {
        delegate.autoOrchestrationEnabled(enabled);
        return this;
    }

    /**
     * 设置计划模式开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder planModeEnabled(boolean enabled) {
        delegate.planModeEnabled(enabled);
        return this;
    }

    /**
     * 设置静态子代理声明列表
     * @param declarations
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder subagentDeclarations(List<SubagentDeclaration> declarations) {
        if (declarations != null && !declarations.isEmpty()) {
            List<com.yangqiongai.agent.harness.subagent.orchestration.SubagentDeclaration> harnessDeclarations =
                    new ArrayList<>(declarations.size());
            for (SubagentDeclaration declaration : declarations) {
                harnessDeclarations.add(RuntimeTypeConverter.toHarness(declaration));
            }
            delegate.subagentDeclarations(harnessDeclarations);
        }
        return this;
    }

    /**
     * 设置编排策略默认值
     * @param strategy
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder orchestrationStrategy(OrchestrationStrategy strategy) {
        if (strategy != null) {
            delegate.orchestrationStrategy(RuntimeTypeConverter.toHarness(strategy));
        }
        return this;
    }

    /**
     * 设置编排轮次默认值
     * @param rounds null时由LLM自行决定
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder orchestrationRounds(Integer rounds) {
        delegate.orchestrationRounds(rounds);
        return this;
    }

    /**
     * 设置编排轮次上限
     * @param maxRounds 默认8
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxOrchestrationRounds(int maxRounds) {
        delegate.maxOrchestrationRounds(maxRounds);
        return this;
    }

    /**
     * 设置子代理最大嵌套深度
     * @param maxDepth 默认3
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxSubagentDepth(int maxDepth) {
        delegate.setSubagentMaxDepth(maxDepth);
        return this;
    }

    /**
     * 设置是否允许子代理递归编排
     * @param allowed 默认true
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder allowRecursiveOrchestration(boolean allowed) {
        delegate.setAllowRecursiveOrchestration(allowed);
        return this;
    }

    /**
     * 设置LLM动态生成子代理声明数上限
     * @param max 默认5
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxSubagents(int max) {
        delegate.setMaxSubagents(max);
        return this;
    }

    /**
     * 设置子代理声明生成使用的模型编码
     * @param modelCode null时使用主模型
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder generationModelCode(String modelCode) {
        delegate.setGenerationModelCode(modelCode);
        return this;
    }

    /**
     * 注册Handoff目标，仅支持本适配器包装的引擎运行时
     * @param name 目标名称
     * @param runtime 目标运行时
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder handoffTarget(String name, AgentRuntime runtime) {
        if (runtime instanceof AgentHarnessRuntime harnessRuntime) {
            delegate.handoffTarget(name, harnessRuntime.getDelegate());
        }
        return this;
    }

    /**
     * 设置记忆工具开关（默认启用）
     * @param enabled false时禁用记忆工具
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder memoryToolsEnabled(boolean enabled) {
        delegate.memoryToolsEnabled(enabled);
        return this;
    }

    /**
     * 设置记忆刷新和维护中间件开关（默认启用）
     * @param enabled false时禁用记忆刷新和维护中间件
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder memoryHooksEnabled(boolean enabled) {
        delegate.memoryHooksEnabled(enabled);
        return this;
    }

    /**
     * 注入L3长期记忆存储，启用后自动装配记忆工具与持久化钩子
     * @param longTermMemory
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder longTermMemory(com.yangqiongai.ai.agent.runtime.memory.AgentLongTermMemory longTermMemory) {
        if (longTermMemory != null) {
            delegate.longTermMemory(RuntimeSpiBridge.toHarness(longTermMemory));
        }
        return this;
    }

    /**
     * 注入L2会话级短期记忆
     * @param sessionMemory null时使用引擎内置内存实现
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder sessionMemory(AgentSessionMemory sessionMemory) {
        if (sessionMemory != null) {
            delegate.sessionMemory(RuntimeSpiBridge.toHarness(sessionMemory));
        }
        return this;
    }

    /**
     * 设置L3到L1检索注入开关
     * @param enabled 默认true
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder memoryRetrievalEnabled(boolean enabled) {
        delegate.memoryRetrievalEnabled(enabled);
        return this;
    }

    /**
     * 设置L3到L1检索注入条目上限
     * @param topK 默认3
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder memoryRetrievalTopK(int topK) {
        delegate.memoryRetrievalTopK(topK);
        return this;
    }

    /**
     * 设置基于LLM摘要的上下文压缩开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder summaryCompactionEnabled(boolean enabled) {
        delegate.summaryCompactionEnabled(enabled);
        return this;
    }

    /**
     * 设置记忆遗忘策略
     * @param policy
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder memoryForgettingPolicy(ForgettingPolicy policy) {
        if (policy != null) {
            delegate.memoryForgettingPolicy(RuntimeSpiBridge.toHarness(policy));
        }
        return this;
    }

    /**
     * 设置单次Agent执行总超时
     * @param timeout
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder timeout(Duration timeout) {
        delegate.timeout(timeout);
        return this;
    }

    /**
     * 设置总超时模式
     * @param mode WALL_CLOCK硬墙钟 / IDLE空闲超时
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder timeoutMode(TimeoutMode mode) {
        if (mode != null) {
            delegate.timeoutMode(RuntimeTypeConverter.toHarness(mode));
        }
        return this;
    }

    /**
     * 设置单轮推理与工具执行超时
     * @param timeout
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder iterationTimeout(Duration timeout) {
        delegate.iterationTimeout(timeout);
        return this;
    }

    /**
     * 设置单次工具调用超时
     * @param timeout 默认60s
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolCallTimeout(Duration timeout) {
        delegate.toolCallTimeout(timeout);
        return this;
    }

    /**
     * 设置最大并行工具调用数
     * @param max 默认1
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxConcurrentToolCalls(int max) {
        delegate.maxConcurrentToolCalls(max);
        return this;
    }

    /**
     * 设置工具下发模式
     * @param mode FULL全量下发（默认）；PROGRESSIVE渐进加载
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolLoadingMode(ToolLoadingMode mode) {
        if (mode != null) {
            delegate.toolLoadingMode(RuntimeTypeConverter.toHarness(mode));
        }
        return this;
    }

    /**
     * 设置渐进模式下常驻工具名单（始终下发完整schema）
     * @param toolNames 工具名集合
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder alwaysOnTools(Set<String> toolNames) {
        if (toolNames != null) {
            delegate.alwaysOnTools(toolNames);
        }
        return this;
    }

    /**
     * 设置工具白名单
     * @param toolNames
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder allowedTools(Set<String> toolNames) {
        delegate.allowedTools(toolNames);
        return this;
    }

    /**
     * 设置工具黑名单
     * @param toolNames
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder deniedTools(Set<String> toolNames) {
        delegate.deniedTools(toolNames);
        return this;
    }

    /**
     * 设置需要人工审批的工具名称集合
     * @param toolNames
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder requireApproval(Set<String> toolNames) {
        delegate.requireApproval(toolNames);
        return this;
    }

    /**
     * 设置统一审批模式
     * @param mode MANUAL人工 / AUTO AI判定 / FULL_ACCESS全放行 / CUSTOM细粒度
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder approvalMode(AgentApprovalMode mode) {
        if (mode != null) {
            delegate.approvalMode(RuntimeTypeConverter.toHarness(mode));
        }
        return this;
    }

    /**
     * 设置AUTO模式的审批判定模型
     * @param judgeModel
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder approvalJudgeModel(AgentModel judgeModel) {
        if (judgeModel != null) {
            delegate.approvalJudgeModel(new AgentModelAdapter(judgeModel));
        }
        return this;
    }

    /**
     * 设置AI判定失败的回退行为
     * @param fallbackAsk true回退人工审批 / false直接拒绝
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder aiApprovalFallbackAsk(boolean fallbackAsk) {
        delegate.aiApprovalFallbackAsk(fallbackAsk);
        return this;
    }

    /**
     * 设置AI审批附加策略描述
     * @param guidance
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder aiApprovalGuidance(String guidance) {
        delegate.aiApprovalGuidance(guidance);
        return this;
    }

    /**
     * 注入统一权限策略门
     * @param gate
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolPolicyGate(ToolPolicyGate gate) {
        if (gate != null) {
            delegate.toolPolicyGate(RuntimeSpiBridge.toHarness(gate));
        }
        return this;
    }

    /**
     * 注入工具执行记录存储
     * @param store null时使用引擎内存实现
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolExecutionStore(ToolExecutionStore store) {
        if (store != null) {
            delegate.toolExecutionStore(RuntimeSpiBridge.toHarness(store));
        }
        return this;
    }

    /**
     * 设置工具失败后处理策略
     * @param strategy
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolFailureStrategy(HarnessAgentRuntimeBuilder.ToolFailureStrategy strategy) {
        if (strategy != null) {
            delegate.toolFailureStrategy(RuntimeTypeConverter.toHarness(strategy));
        }
        return this;
    }

    /**
     * 设置单个工具最大重试次数
     * @param maxRetries 默认0
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxToolRetries(int maxRetries) {
        delegate.maxToolRetries(maxRetries);
        return this;
    }

    /**
     * 设置工具结果最大字符数
     * @param maxChars 默认10000
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxToolResultChars(int maxChars) {
        delegate.maxToolResultChars(maxChars);
        return this;
    }

    /**
     * 设置最大连续工具失败次数
     * @param maxFailures 默认3
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxConsecutiveToolFailures(int maxFailures) {
        delegate.maxConsecutiveToolFailures(maxFailures);
        return this;
    }

    /**
     * 设置上下文窗口Token上限
     * @param maxTokens
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder maxContextTokens(int maxTokens) {
        delegate.maxContextTokens(maxTokens);
        return this;
    }

    /**
     * 设置历史截断策略
     * @param strategy
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder historyTruncationStrategy(HarnessAgentRuntimeBuilder.TruncationStrategy strategy) {
        if (strategy != null) {
            delegate.historyTruncationStrategy(RuntimeTypeConverter.toHarness(strategy));
        }
        return this;
    }

    /**
     * 设置模型调用指数退避重试配置
     * @param config null时不重试
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder modelRetryConfig(AgentModelRetryConfig config) {
        if (config != null) {
            delegate.modelRetryConfig(RuntimeTypeConverter.toHarness(config));
        }
        return this;
    }

    /**
     * 设置成本计价模型编码
     * @param modelCode
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder modelCode(String modelCode) {
        delegate.modelCode(modelCode);
        return this;
    }

    /**
     * 添加自定义模型装饰层，框架模型视图与引擎模型互转后注册
     * @param layer
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder addModelCallerLayer(Function<AgentModel, AgentModel> layer) {
        if (layer != null) {
            delegate.addModelCallerLayer(engineModel -> {
                AgentModel decorated = layer.apply(new AgentHarnessModelBridge(engineModel));
                return decorated != null ? new AgentModelAdapter(decorated) : null;
            });
        }
        return this;
    }

    /**
     * 设置上下文缓存
     * @param enabled 总开关
     * @param cacheSystemPrompt 是否缓存系统提示词
     * @param cacheTools 是否缓存工具定义
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder contextCaching(boolean enabled, boolean cacheSystemPrompt, boolean cacheTools) {
        delegate.contextCaching(enabled, cacheSystemPrompt, cacheTools);
        return this;
    }

    /**
     * 设置Token预算策略
     * @param policy
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder tokenBudgetPolicy(TokenBudgetPolicy policy) {
        if (policy != null) {
            delegate.tokenBudgetPolicy(RuntimeSpiBridge.toHarness(policy));
        }
        return this;
    }

    /**
     * 设置成本预算策略
     * @param policy
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder costBudgetPolicy(CostBudgetPolicy policy) {
        if (policy != null) {
            delegate.costBudgetPolicy(RuntimeSpiBridge.toHarness(policy));
        }
        return this;
    }

    /**
     * 注册模型定价
     * @param pricings
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder modelPricing(ModelPricing... pricings) {
        List<com.yangqiongai.agent.harness.model.ModelPricing> converted =
                RuntimeTypeConverter.toHarnessPricings(pricings);
        if (converted != null && !converted.isEmpty()) {
            delegate.modelPricing(converted.toArray(new com.yangqiongai.agent.harness.model.ModelPricing[0]));
        }
        return this;
    }

    /**
     * 设置模型调用令牌桶限流
     * @param limiter
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder modelRateLimiter(RateLimiter limiter) {
        if (limiter != null) {
            delegate.modelRateLimiter(RuntimeSpiBridge.toHarness(limiter));
        }
        return this;
    }

    /**
     * 设置输入护栏开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder inputGuardrailEnabled(boolean enabled) {
        if (enabled) {
            delegate.enableInputGuardrail();
        }
        return this;
    }

    /**
     * 设置全链路工具护栏开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder toolGuardrailEnabled(boolean enabled) {
        if (enabled) {
            delegate.enableToolGuardrail();
        }
        return this;
    }

    /**
     * 设置内容审查策略
     * @param policy null时放行
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder contentModerationPolicy(ContentModerationPolicy policy) {
        if (policy != null) {
            delegate.contentModeration(RuntimeSpiBridge.toHarness(policy));
        }
        return this;
    }

    /**
     * 注册命名检索器
     * @param name 检索器名称
     * @param retriever
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder retriever(String name, Retriever retriever) {
        if (retriever != null) {
            delegate.retriever(name, RuntimeSpiBridge.toHarness(retriever));
        }
        return this;
    }

    /**
     * 设置RAG自动检索注入中间件开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder ragRetrievalEnabled(boolean enabled) {
        if (enabled) {
            delegate.enableRagRetrieval(ragTopK, ragInjectionTemplate);
        } else {
            delegate.disableRagRetrieval();
        }
        return this;
    }

    /**
     * 设置RAG自动检索条数
     * @param topK 默认3
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder ragTopK(int topK) {
        this.ragTopK = topK;
        return this;
    }

    /**
     * 设置RAG注入模板前缀
     * @param template null时使用引擎默认模板
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder ragInjectionTemplate(String template) {
        this.ragInjectionTemplate = template;
        return this;
    }

    /**
     * 设置语义缓存开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder semanticCachingEnabled(boolean enabled) {
        if (enabled) {
            delegate.enableSemanticCaching(semanticCacheThreshold, semanticCacheMaxEntries);
        } else {
            delegate.disableSemanticCaching();
        }
        return this;
    }

    /**
     * 设置语义缓存命中相似度阈值
     * @param threshold
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder semanticCacheThreshold(double threshold) {
        this.semanticCacheThreshold = threshold;
        return this;
    }

    /**
     * 设置语义缓存最大条目数
     * @param maxEntries
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder semanticCacheMaxEntries(int maxEntries) {
        this.semanticCacheMaxEntries = maxEntries;
        return this;
    }

    /**
     * 设置Web工具强制启用
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder webToolsEnabled(boolean enabled) {
        delegate.webEnabled(enabled);
        return this;
    }

    /**
     * 设置Web检索提供方
     * @param provider
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder webSearchProvider(WebSearchProvider provider) {
        if (provider != null) {
            delegate.webSearchProvider(RuntimeSpiBridge.toHarness(provider));
        }
        return this;
    }

    /**
     * 设置网页抓取提供方
     * @param fetcher
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder webFetcher(WebFetcher fetcher) {
        if (fetcher != null) {
            delegate.webFetcher(RuntimeSpiBridge.toHarness(fetcher));
        }
        return this;
    }

    /**
     * 设置向用户提问工具开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder askUserEnabled(boolean enabled) {
        delegate.askUserEnabled(enabled);
        return this;
    }

    /**
     * 设置文件工具开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder fileToolsEnabled(boolean enabled) {
        if (enabled) {
            if (fileSandboxRoot != null || fileParser != null) {
                delegate.enableFileToolkit(fileSandboxRoot, RuntimeSpiBridge.toHarness(fileParser));
            } else {
                delegate.enableFileToolkit();
            }
        }
        return this;
    }

    /**
     * 设置文件工具箱参数
     * @param sandboxRoot 沙箱根目录，null时使用引擎默认值
     * @param parser 文档解析器，null时仅支持纯文本
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder fileToolkit(String sandboxRoot, DocumentParser parser) {
        this.fileSandboxRoot = sandboxRoot;
        this.fileParser = parser;
        return this;
    }

    /**
     * 一次性注入共享存储
     * @param stores
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder stores(DistributedStores stores) {
        if (stores != null) {
            delegate.stores(RuntimeSpiBridge.toHarness(stores));
        }
        return this;
    }

    /**
     * 注入运行记录存储
     * @param store
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder agentRunStore(AgentRunStore store) {
        if (store != null) {
            delegate.agentRunStore(RuntimeSpiBridge.toHarness(store));
        }
        return this;
    }

    /**
     * 注入完整检查点存储
     * @param store
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder checkpointStore(CheckpointStore store) {
        if (store != null) {
            delegate.checkpointStore(RuntimeSpiBridge.toHarness(store));
        }
        return this;
    }

    /**
     * 注入审批存储
     * @param store
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder approvalStore(ApprovalStore store) {
        if (store != null) {
            delegate.approvalStore(RuntimeSpiBridge.toHarness(store));
        }
        return this;
    }

    /**
     * 注入检查点管理器
     * @param manager
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder checkpointManager(CheckpointManager manager) {
        if (manager != null) {
            delegate.checkpointManager(RuntimeSpiBridge.toHarness(manager));
        }
        return this;
    }

    /**
     * 注入运行锁存储
     * @param store
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder runLockStore(RunLockStore store) {
        if (store != null) {
            delegate.runLockStore(RuntimeSpiBridge.toHarness(store));
        }
        return this;
    }

    /**
     * 设置本节点唯一标识
     * @param nodeId
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder nodeId(String nodeId) {
        delegate.nodeId(nodeId);
        return this;
    }

    /**
     * 注册事件监听器
     * @param listener
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder addEventListener(AgentEventListener listener) {
        if (listener != null) {
            delegate.addEventListener(RuntimeSpiBridge.toHarness(listener));
        }
        return this;
    }

    /**
     * 设置追踪导出器
     * @param emitter
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder traceEmitter(TraceEmitter emitter) {
        if (emitter != null) {
            delegate.traceEmitter(RuntimeSpiBridge.toHarness(emitter));
        }
        return this;
    }

    /**
     * 设置上下文快照监听器，每次模型调用前回调采集快照
     * @param listener
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder contextSnapshotListener(
            com.yangqiongai.ai.agent.runtime.trace.ContextSnapshotListener listener) {
        if (listener != null) {
            delegate.contextSnapshotListener(RuntimeSpiBridge.toHarness(listener));
        }
        return this;
    }

    /**
     * 注册工具调用守卫，防腐转换为引擎守卫
     * @param guard
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder guard(com.yangqiongai.ai.agent.runtime.spi.ToolInvocationGuard guard) {
        if (guard != null) {
            delegate.guard(new com.yangqiongai.agent.harness.spi.ToolInvocationGuard() {

                @Override
                public com.yangqiongai.agent.harness.spi.ToolInvocationGuard.Decision before(
                        com.yangqiongai.agent.harness.spi.ToolInvocation invocation) {
                    com.yangqiongai.ai.agent.runtime.spi.ToolInvocationGuard.Decision decision =
                            guard.before(new com.yangqiongai.ai.agent.runtime.spi.ToolInvocation(
                                    invocation.getAgentCode(), invocation.getToolName(), invocation.getToolUseId(),
                                    invocation.getScopeId(), invocation.getUserId(),
                                    invocation.getRunId(), invocation.getInput()));
                    return decision != null && decision.isDenied()
                            ? com.yangqiongai.agent.harness.spi.ToolInvocationGuard.Decision.deny(decision.getReason())
                            : com.yangqiongai.agent.harness.spi.ToolInvocationGuard.Decision.allow();
                }

                @Override
                public void after(com.yangqiongai.agent.harness.spi.ToolInvocationOutcome outcome) {
                    guard.after(new com.yangqiongai.ai.agent.runtime.spi.ToolInvocationOutcome(
                            outcome.getAgentCode(), outcome.getToolName(), outcome.getToolUseId(),
                            outcome.getScopeId(), outcome.getUserId(), outcome.getRunId(), outcome.isDenied(),
                            outcome.isSuccess(), outcome.getSummary(), outcome.getDurationMillis()));
                }

                @Override
                public int order() {
                    return guard.getOrder();
                }
            });
        }
        return this;
    }

    /**
     * 注册运行时定制器，以本适配器为定制入口回调
     * @param customizer
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder addCustomizer(RuntimeCustomizer customizer) {
        if (customizer != null) {
            delegate.addCustomizer(new com.yangqiongai.agent.harness.spi.RuntimeCustomizer() {

                @Override
                public void customize(HarnessRuntimeBuilder builder) {
                    customizer.customize(AgentHarnessRuntimeBuilder.this);
                }

                @Override
                public int order() {
                    return customizer.getOrder();
                }
            });
        }
        return this;
    }

    /**
     * 替换默认ReAct执行循环
     * @param loop
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder agentLoop(AgentLoop loop) {
        if (loop != null) {
            delegate.agentLoop(RuntimeSpiBridge.toHarness(loop));
        }
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
    @Override
    public AgentHarnessRuntimeBuilder executionParadigm(ParadigmSpec spec) {
        com.yangqiongai.agent.harness.engine.AgentLoop loop = createParadigmLoop(spec);
        if (loop != null) {
            delegate.agentLoop(loop);
        }
        return this;
    }

    /**
     * 按范式类型构造执行引擎，react/null/未识别返回null表示不替换
     * @param spec
     * @return
     */
    private com.yangqiongai.agent.harness.engine.AgentLoop createParadigmLoop(ParadigmSpec spec) {
        if (spec == null || spec.getType() == null || spec.getType().isBlank()) {
            return null;
        }
        String type = spec.getType().trim().toLowerCase(Locale.ROOT);
        if (ParadigmSpec.REACT.equals(type)) {
            return null;
        }
        com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions options =
                new com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions();
        if (spec.getMaxSteps() != null) {
            options.maxSteps(spec.getMaxSteps());
        }
        return switch (type) {
            case ParadigmSpec.REFLEXION -> {
                if (spec.getMaxReflections() != null) {
                    options.maxReflections(spec.getMaxReflections());
                }
                yield new ReflexionEngine(options);
            }
            case ParadigmSpec.SELF_REFINE -> {
                if (spec.getMaxRefinements() != null) {
                    options.maxRefinements(spec.getMaxRefinements());
                }
                yield new SelfRefineEngine(options);
            }
            case ParadigmSpec.PLAN_EXECUTE -> new PlanExecuteEngine(options);
            case ParadigmSpec.REWOO -> new ReWooEngine(options);
            case ParadigmSpec.SELF_ASK -> new SelfAskEngine(options);
            case ParadigmSpec.AUTO -> new RouterEngine();
            default -> {
                // 未识别的范式类型回退默认ReAct并告警
                log.warn("未识别的执行范式类型: {}，回退默认ReAct执行循环", spec.getType());
                yield null;
            }
        };
    }

    /**
     * 设置JDK SPI插件自动发现开关
     * @param enabled 默认false
     * @return
     */
    @Override
    public AgentHarnessRuntimeBuilder autoDiscoverEnabled(boolean enabled) {
        if (enabled) {
            delegate.autoDiscover();
        }
        return this;
    }

    /**
     * 构建Agent运行时
     * @return
     */
    @Override
    public AgentRuntime build() {
        com.yangqiongai.agent.harness.core.AgentRuntime harnessRuntime = delegate.build();
        return new AgentHarnessRuntime(harnessRuntime);
    }

    /**
     * 获取被包装的独立引擎构建器
     * @return
     */
    HarnessRuntimeBuilder getDelegate() {
        return delegate;
    }
}
