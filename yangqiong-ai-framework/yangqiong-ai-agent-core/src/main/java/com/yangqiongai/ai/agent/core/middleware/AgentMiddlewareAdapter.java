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
package com.yangqiongai.ai.agent.core.middleware;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.core.provider.ToolConventions;
import com.yangqiongai.ai.agent.core.provider.SkillUsageTracker;
import com.yangqiongai.ai.agent.core.provider.ToolUsageTracker;
import com.yangqiongai.ai.agent.core.task.TaskStepRecorder;
import com.yangqiongai.ai.agent.core.trace.ErrorCategorizer;
import com.yangqiongai.ai.agent.core.trace.TraceCollector;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;
import com.yangqiongai.ai.agent.runtime.prompt.SystemPromptSections;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent中间件适配器
 * <p>
 * 同时负责任务步骤采集：onMessage 记录 LLM_CALL，onToolCall 记录 TOOL_CALL / SUBAGENT_CALL。
 * 委托模式的子代理调用（工具名以 "call_" 开头）自动识别为 SUBAGENT_CALL 步骤。
 * </p>
 * @author yangqiong
 */
public class AgentMiddlewareAdapter implements AgentMiddleware {

    private static final Logger log = LoggerFactory.getLogger(AgentMiddlewareAdapter.class);

    /**
     * 中文日期格式化器
     */
    private static final DateTimeFormatter CN_DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", Locale.CHINA);

    private final TraceCollector traceCollector;
    private final ErrorCategorizer errorCategorizer;
    private final SkillUsageTracker skillUsageTracker;
    private final ToolConventions toolConventions;
    private final ToolUsageTracker toolUsageTracker;

    /**
     * 运行时工具元数据，key=工具名，供使用量埋点记录名称与描述
     */
    private final Map<String, AgentTool> toolMetaMap;

    /**
     * 工具调用开始时间戳，key=toolName，用于计算工具执行耗时
     */
    private final Map<String, Long> toolStartTimes = new ConcurrentHashMap<>();

    /**
     * LLM调用开始时间戳（毫秒），用于计算LLM调用耗时
     */
    private volatile long llmCallStartTime = 0;

    public AgentMiddlewareAdapter(TraceCollector traceCollector, ErrorCategorizer errorCategorizer) {
        this(traceCollector, errorCategorizer, null, null, null, null);
    }

    public AgentMiddlewareAdapter(TraceCollector traceCollector, ErrorCategorizer errorCategorizer,
                                   SkillUsageTracker skillUsageTracker) {
        this(traceCollector, errorCategorizer, skillUsageTracker, null, null, null);
    }

    public AgentMiddlewareAdapter(TraceCollector traceCollector, ErrorCategorizer errorCategorizer,
                                   SkillUsageTracker skillUsageTracker, ToolConventions toolConventions) {
        this(traceCollector, errorCategorizer, skillUsageTracker, toolConventions, null, null);
    }

    public AgentMiddlewareAdapter(TraceCollector traceCollector, ErrorCategorizer errorCategorizer,
                                   SkillUsageTracker skillUsageTracker, ToolConventions toolConventions,
                                   ToolUsageTracker toolUsageTracker) {
        this(traceCollector, errorCategorizer, skillUsageTracker, toolConventions, toolUsageTracker, null);
    }

    public AgentMiddlewareAdapter(TraceCollector traceCollector, ErrorCategorizer errorCategorizer,
                                   SkillUsageTracker skillUsageTracker, ToolConventions toolConventions,
                                   ToolUsageTracker toolUsageTracker, Map<String, AgentTool> toolMetaMap) {
        this.traceCollector = traceCollector;
        this.errorCategorizer = errorCategorizer;
        this.skillUsageTracker = skillUsageTracker;
        this.toolConventions = toolConventions;
        this.toolUsageTracker = toolUsageTracker;
        this.toolMetaMap = toolMetaMap;
    }

    /**
     * 系统提示词拦截，编排技能分块注入与运行时上下文注入
     * <p>
     * 支持两种模式：
     * 1. 动态重建模式：技能中间件已注入# 可用技能分块，缺少使用指引时补齐
     * 2. Hook注入模式：SkillBox存在于AgentContext中，追加技能分块+使用指引
     * 运行时上下文（环境信息、工具使用规则）统一位于技能块之后。
     * </p>
     * @param systemPrompt
     * @param context
     * @return
     */
    @Override
    public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
        log.debug("Middleware onSystemPrompt, scopeId={}", SessionContext.getScopeId());
        String base = systemPrompt != null ? systemPrompt : "";

        // 动态重建模式兜底：技能摘要已注入但缺少使用指引时补齐
        if (base.contains(SystemPromptSections.SKILL_SECTION)
                && !base.contains(SystemPromptSections.SKILL_GUIDE_SECTION)) {
            log.debug("检测到技能摘要注入，补齐技能使用指引");
            base = base + SystemPromptSections.SKILL_USAGE_GUIDE;
        }

        // Hook注入模式：SkillBox存在于AgentContext，技能块紧随基础提示词注入
        AgentSkillBox skillBox = resolveSkillBox(context);
        if (skillBox != null) {
            String skillPrompt = skillBox.buildSystemPrompt();
            if (skillPrompt != null && !skillPrompt.isBlank()) {
                String separator = base.isEmpty() || base.endsWith("\n\n") ? "" : (base.endsWith("\n") ? "\n" : "\n\n");
                base = base + separator + skillPrompt + SystemPromptSections.SKILL_USAGE_GUIDE;
                log.debug("Hook注入技能提示词: skillPromptLength={}", skillPrompt.length());
            }
        }

        // 运行时上下文（环境信息、工具使用规则）位于技能块之后
        return enrichRuntimeContext(base, context);
    }

    /**
     * 注入运行时上下文信息（当前日期、工具使用规则），已存在的分块不重复追加
     * <p>
     * 环境信息与工具使用规则追加到系统提示词末尾，并附带明确的使用引导，避免LLM忽略。
     * 后续如需注入其他运行时信息（如用户偏好、会话元信息等），在此方法内追加即可。
     * </p>
     * @param base
     * @param context
     * @return
     */
    private String enrichRuntimeContext(String base, AgentRuntimeContext context) {
        String enriched = base;
        if (!enriched.contains(SystemPromptSections.ENV_SECTION)) {
            String today = LocalDate.now().format(CN_DATE_FORMATTER);
            log.debug("注入当前日期: {}", today);
            String dateHint = "\n" + SystemPromptSections.ENV_SECTION + "\n\n当前日期：" + today + "。\n"
                    + "重要：当用户提到\"今天\"、\"昨天\"、\"明天\"、\"本周\"等相对时间时，"
                    + "必须以上述日期为准，不要使用模型自身判断的日期。\n";
            enriched = enriched + dateHint;
        }
        if (!enriched.contains(SystemPromptSections.TOOL_RULE_SECTION)) {
            log.debug("注入工具使用规则");
            enriched = enriched + SystemPromptSections.TOOL_RULE_HINT;
        }
        return enriched;
    }

    /**
     * 工具调用前拦截，记录 TOOL_CALL / SUBAGENT_CALL 步骤并追踪技能使用量
     * <p>
     * 委托模式的子代理调用（工具名以 "call_" 开头）自动识别为 SUBAGENT_CALL 步骤，
     * 其他工具调用记录为普通 TOOL_CALL 步骤。
     * </p>
     * @param toolName
     * @param input
     * @param context
     * @return
     */
    @Override
    public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
        log.debug("Middleware onToolCall: tool={}", toolName);

        // 记录工具调用开始时间，供onToolResult计算耗时
        toolStartTimes.put(toolName, System.currentTimeMillis());

        // 工具使用量追踪（子代理委托调用不计入工具用量），附带运行时名称与描述
        if (toolUsageTracker != null && toolUsageTracker.isEnabled() && !isSubagentCall(toolName)) {
            AgentTool toolMeta = toolMetaMap != null ? toolMetaMap.get(toolName) : null;
            toolUsageTracker.bumpCall(toolName,
                    toolMeta != null ? toolMeta.getName() : null,
                    toolMeta != null ? toolMeta.getDescription() : null,
                    resolveContextScopeId(context));
        }

        // 技能使用量追踪
        trackSkillUsage(toolName, input);

        TaskStepRecorder recorder = getRecorder(context);
        if (recorder != null && recorder.isEnabled()) {
            // 区分子代理调用和普通工具调用
            if (isSubagentCall(toolName)) {
                // 委托模式子代理调用 → 记录为 SUBAGENT_CALL
                String subagentName = extractSubagentName(toolName);
                String subagentInput = extractSubagentInput(input);
                recorder.recordSubagentCall("agent", subagentName, subagentInput, 0, null);
            } else {
                // 普通工具调用 → 记录为 TOOL_CALL（工具输出和耗时由onToolResult补充）
                AgentToolUseBlock toolUse = new AgentToolUseBlock(toolName, null, input);
                recorder.recordToolCall("agent", toolUse, null, 0, null);
            }
        }
        return input;
    }

    /**
     * 工具调用后拦截
     * @param toolName
     * @param result
     * @param context
     * @return
     */
    @Override
    public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
        log.debug("Middleware onToolResult: tool={}", toolName);

        // 计算工具执行耗时
        Long startTime = toolStartTimes.remove(toolName);
        long latencyMs = startTime != null ? System.currentTimeMillis() - startTime : 0;

        // 工具结果计数（成功/失败）
        if (toolUsageTracker != null && toolUsageTracker.isEnabled() && !isSubagentCall(toolName)) {
            toolUsageTracker.bumpResult(toolName, result == null || !result.isError(),
                    resolveContextScopeId(context));
        }

        // 更新步骤记录中的工具输出、耗时和退出码
        TaskStepRecorder recorder = getRecorder(context);
        if (recorder != null && recorder.isEnabled()) {
            String resultText = result != null ? result.getTextContent() : "";
            recorder.updateLastToolCall(resultText, latencyMs, null);
        }

        return result;
    }

    /**
     * 错误处理钩子，按阶段分类异常并记录日志
     * <p>
     * phase取值：agent / reasoning / acting / modelCall
     * </p>
     * @param throwable
     * @param context
     * @param phase
     */
    @Override
    public void onError(Throwable throwable, AgentRuntimeContext context, String phase) {
        if (throwable == null || errorCategorizer == null) {
            return;
        }
        ErrorCategorizer.ErrorCategory category = errorCategorizer.categorize(throwable);
        log.warn("Agent执行异常, phase={}, category={}, error={}", phase, category, throwable.getMessage());
    }

    /**
     * 消息处理拦截，记录 LLM_CALL 步骤
     * <p>
     * 当消息携带ChatUsage时（即LLM响应消息），记录LLM_CALL步骤。
     * 原onReasoning中的token信息通过ModelCallEndEvent收集，迁移后从消息的ChatUsage直接获取。
     * latency由调用方（provider的onReasoning）在doOnNext时计算并传入。
     * </p>
     * @param message
     * @param context
     * @return
     */
    @Override
    public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
        log.debug("Middleware onMessage");
        if (message == null) {
            return null;
        }
        TaskStepRecorder recorder = getRecorder(context);
        if (recorder == null || !recorder.isEnabled()) {
            return message;
        }
        AgentChatUsage usage = message.getChatUsage();
        if (usage == null) {
            // 非LLM响应消息（用户输入等），记录当前时间作为下次LLM调用的起始时间
            llmCallStartTime = System.currentTimeMillis();
            return message;
        }
        // 计算LLM调用耗时：优先使用SDK提供的latency，否则使用本地计时
        long latencyMs = message.getLatency();
        if (latencyMs <= 0 && llmCallStartTime > 0) {
            latencyMs = System.currentTimeMillis() - llmCallStartTime;
        }
        // 重置LLM调用计时，为下次调用做准备
        llmCallStartTime = 0;
        // 使用消息的实际文本内容作为步骤内容
        String msgContent = message.getTextContent();
        recorder.recordLlmCall(
                "agent",
                null,
                msgContent != null ? msgContent : "",
                latencyMs,
                (long) usage.getPromptTokens(),
                (long) usage.getCompletionTokens(),
                (long) usage.getTotalTokens()
        );
        return message;
    }

    /**
     * 判断是否为委托模式子代理调用
     * <p>
     * SDK SubAgentTool 生成的工具名格式为 call_{agentName}
     * </p>
     * @param toolName
     * @return
     */
    private boolean isSubagentCall(String toolName) {
        String prefix = resolveSubagentToolPrefix();
        return prefix != null && toolName != null && toolName.startsWith(prefix);
    }

    /**
     * 从工具名提取子代理名称
     * <p>
     * 工具名 "call_researcher" → "researcher"
     * </p>
     * @param toolName
     * @return
     */
    private String extractSubagentName(String toolName) {
        String prefix = resolveSubagentToolPrefix();
        if (prefix != null && toolName != null && toolName.startsWith(prefix)) {
            return toolName.substring(prefix.length());
        }
        return toolName;
    }

    /**
     * 从工具调用参数提取子代理输入消息
     * <p>
     * SubAgentTool 的参数结构为 { "message": "...", "session_id": "..." }
     * </p>
     * @param input
     * @return
     */
    private String extractSubagentInput(Map<String, Object> input) {
        if (input == null) {
            return "";
        }
        Object message = input.get("message");
        return message != null ? message.toString() : input.toString();
    }

    /**
     * 从RuntimeContext解析SkillBox
     * @param context
     * @return
     */
    private AgentSkillBox resolveSkillBox(AgentRuntimeContext context) {
        if (context == null) {
            return null;
        }
        // 尝试从字符串键获取（兼容SDK重建RuntimeContext后的属性保留）
        Object obj = context.get(AgentContext.CTX_SKILL_BOX);
        if (obj instanceof AgentSkillBox skillBox) {
            return skillBox;
        }
        return null;
    }

    /**
     * 从RuntimeContext获取TaskStepRecorder
     * <p>
     * 优先使用字符串键检索（兼容SDK重建RuntimeContext后的属性保留）。
     * </p>
     * @param context
     * @return
     */
    private TaskStepRecorder getRecorder(AgentRuntimeContext context) {
        if (context == null) {
            return null;
        }
        // 字符串键：SDK ensureSessionDefaults 重建后仍保留
        Object obj = context.get(TaskStepRecorder.RUNTIME_CONTEXT_KEY);
        if (obj instanceof TaskStepRecorder recorder) {
            return recorder;
        }
        return null;
    }

    /**
     * 从运行时上下文解析作用域ID
     * @param context
     * @return
     */
    private String resolveContextScopeId(AgentRuntimeContext context) {
        if (context == null) {
            return null;
        }
        Object scopeId = context.get("scopeId");
        return scopeId != null ? String.valueOf(scopeId) : null;
    }

    /**
     * 技能使用量追踪
     * <p>
     * 检测技能相关工具调用：
     * - load_skill → bumpView + bumpUse（加载即使用，引擎无独立use_skill工具）
     * - read_skill_resource → bumpView
     * - use_skill → bumpUse（预留兼容，当前引擎未注册）
     * </p>
     * <p>
     * 当 ToolConventions 未注入或禁用时，跳过技能追踪逻辑。
     * </p>
     * @param toolName
     * @param input
     */
    private void trackSkillUsage(String toolName, Map<String, Object> input) {
        if (skillUsageTracker == null || !skillUsageTracker.isEnabled()) {
            return;
        }
        Set<String> skillViewTools = resolveSkillViewTools();
        String skillUseTool = resolveSkillUseTool();
        String skillLoadTool = resolveSkillLoadTool();
        if (skillViewTools == null || skillViewTools.isEmpty() || skillUseTool == null || skillLoadTool == null) {
            return;
        }
        if (skillViewTools.contains(toolName)) {
            extractSkillId(toolName, input).ifPresent(skillId -> {
                skillUsageTracker.bumpView(skillId);
                // load_skill_through_path 加载即使用：Agent加载技能内容后直接据此回答问题
                if (skillLoadTool.equals(toolName)) {
                    skillUsageTracker.bumpUse(skillId);
                }
            });
        } else if (skillUseTool.equals(toolName)) {
            extractSkillId(toolName, input).ifPresent(skillUsageTracker::bumpUse);
        }
    }

    /**
     * 从工具调用参数中提取技能ID
     * <p>
     * load_skill_through_path 的参数为 { skillId: "websearch-search_BUILTIN", path: "SKILL.md" }
     * use_skill 的参数为 { skill_id: "websearch-search" } 或 { name: "websearch-search" }
     * <p>
     * SDK注册技能时拼接 name_source 作为注册ID（如 code-review-assistant_UPLOADED），
     * 需要剥离 _SOURCE 后缀，还原为原始 skillId（如 code-review-assistant）。
     * </p>
     * @param toolName
     * @param args
     * @return
     */
    private Optional<String> extractSkillId(String toolName, Map<String, Object> args) {
        if (args == null) {
            return Optional.empty();
        }
        // load_skill / read_skill_resource → skill_name（引擎参数名，技能原始名）
        Object skillId = args.get("skill_name");
        if (skillId == null) {
            // 兼容旧SDK参数名：skillId（含 _SOURCE 后缀）/ skill_id / name
            skillId = args.get("skillId");
        }
        if (skillId == null) {
            skillId = args.get("skill_id");
        }
        if (skillId == null) {
            skillId = args.get("name");
        }
        if (skillId != null && !skillId.toString().isBlank()) {
            return Optional.of(stripSourceSuffix(skillId.toString()));
        }
        return Optional.empty();
    }

    /**
     * 剥离SDK注册时添加的 _SOURCE 后缀
     * <p>
     * SDK SkillBox.registerSkill() 会将 AgentSkill 注册为 {name}_{source}，
     * 如 "code-review-assistant_UPLOADED"、"websearch-search_BUILTIN"。
     * 已知的source类型：BUILTIN / UPLOADED / GENERATED / COMMUNITY / TRUSTED / AGENT_CREATED / custom
     * </p>
     * @param compositeId
     * @return
     */
    private String stripSourceSuffix(String compositeId) {
        if (compositeId == null) {
            return null;
        }
        // 匹配末尾的 _SOURCE 后缀（SDK已知source类型 + custom回退值）
        Set<String> knownSources = Set.of(
                "BUILTIN", "UPLOADED", "GENERATED", "COMMUNITY", "TRUSTED", "AGENT_CREATED", "custom"
        );
        int lastUnderscore = compositeId.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String suffix = compositeId.substring(lastUnderscore + 1);
            if (knownSources.contains(suffix)) {
                return compositeId.substring(0, lastUnderscore);
            }
        }
        return compositeId;
    }

    /**
     * 获取子代理工具名前缀，SPI不可用时返回null
     * @return
     */
    private String resolveSubagentToolPrefix() {
        if (toolConventions == null || !toolConventions.isEnabled()) {
            return null;
        }
        return toolConventions.subagentToolPrefix();
    }

    /**
     * 获取技能查看工具名集合，SPI不可用时返回null
     * @return
     */
    private Set<String> resolveSkillViewTools() {
        if (toolConventions == null || !toolConventions.isEnabled()) {
            return null;
        }
        return toolConventions.skillViewTools();
    }

    /**
     * 获取技能使用工具名，SPI不可用时返回null
     * @return
     */
    private String resolveSkillUseTool() {
        if (toolConventions == null || !toolConventions.isEnabled()) {
            return null;
        }
        return toolConventions.skillUseTool();
    }

    /**
     * 获取技能加载工具名，SPI不可用时返回null
     * @return
     */
    private String resolveSkillLoadTool() {
        if (toolConventions == null || !toolConventions.isEnabled()) {
            return null;
        }
        return toolConventions.skillLoadTool();
    }
}
