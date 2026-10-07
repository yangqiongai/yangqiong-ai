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
package com.yangqiongai.ai.agent.core.task;

import com.yangqiongai.ai.agent.core.model.AgentTaskStepInfo;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;
import com.yangqiongai.ai.common.util.SensitiveDataUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 任务步骤采集器
 * <p>
 * 通过 RuntimeContext 传递，由 Middleware 回调写入。
 * 每个任务/子代理一个实例，任务完成时通过 drainSteps 批量持久化。
 * </p>
 * @author yangqiong
 */
public class TaskStepRecorder {

    private static final Logger log = LoggerFactory.getLogger(TaskStepRecorder.class);

    /**
     * RuntimeContext 中的字符串键名
     * <p>
     * 必须使用字符串键而非类型键存储，因为 SDK 的 HarnessAgent.ensureSessionDefaults()
     * 重建 RuntimeContext 时仅通过 putAll(ctx.getExtra()) 保留 stringAttributes，
     * typedAttributes 中的类型键属性会丢失。
     * </p>
     */
    public static final String RUNTIME_CONTEXT_KEY = "taskStepRecorder";

    /**
     * 步骤输出截断长度（字符）
     */
    private static final int OUTPUT_TRUNCATE_LENGTH = 2000;

    private final String taskId;

    /**
     * 作用域ID（步骤落库归属）
     */
    private volatile String scopeId;

    private final AtomicInteger stepOrder = new AtomicInteger(0);

    private final List<AgentTaskStepInfo> steps = new CopyOnWriteArrayList<>();

    private volatile boolean enabled = true;

    public TaskStepRecorder(String taskId) {
        this.taskId = taskId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
    }

    /**
     * 记录 LLM_CALL 步骤（由 onReasoning 回调）
     * @param agentName
     * @param modelName
     * @param reasoningText
     * @param latencyMs
     * @param inputTokens
     * @param outputTokens
     * @param totalTokens
     */
    public void recordLlmCall(String agentName, String modelName, String reasoningText,
                              long latencyMs, Long inputTokens, Long outputTokens, Long totalTokens) {
        if (!enabled) {
            return;
        }
        try {
            AgentTaskStepInfo step = new AgentTaskStepInfo();
            step.setTaskId(taskId);
            step.setScopeId(scopeId);
            step.setStepOrder(stepOrder.incrementAndGet());
            step.setStepType("LLM_CALL");
            step.setAgentName(agentName);
            step.setStepContent(truncate(reasoningText));
            step.setInputTokens(inputTokens != null ? inputTokens.intValue() : 0);
            step.setOutputTokens(outputTokens != null ? outputTokens.intValue() : 0);
            step.setTotalTokens(totalTokens != null ? totalTokens.intValue() : 0);
            step.setDurationMs(latencyMs);
            steps.add(step);
        } catch (Exception e) {
            log.warn("记录LLM_CALL步骤失败: taskId={}", taskId, e);
        }
    }

    /**
     * 记录 TOOL_CALL + TOOL_RESULT 步骤（由 onActing 回调）
     * @param agentName
     * @param toolUse
     * @param toolOutput
     * @param latencyMs
     * @param permissionDecision
     */
    public void recordToolCall(String agentName, AgentToolUseBlock toolUse,
                               List<AgentContentBlock> toolOutput, long latencyMs,
                               String permissionDecision) {
        if (!enabled || toolUse == null) {
            return;
        }
        try {
            AgentTaskStepInfo step = new AgentTaskStepInfo();
            step.setTaskId(taskId);
            step.setScopeId(scopeId);
            step.setStepOrder(stepOrder.incrementAndGet());
            step.setStepType("TOOL_CALL");
            step.setAgentName(agentName);
            step.setToolName(toolUse.getToolName());
            step.setToolInput(truncate(toolUse.getInput() != null ? toolUse.getInput().toString() : ""));
            step.setStepContent("调用工具: " + toolUse.getToolName());
            step.setToolOutput(extractText(toolOutput));
            step.setDurationMs(latencyMs);
            step.setPermissionDecision(permissionDecision);
            steps.add(step);
        } catch (Exception e) {
            log.warn("记录TOOL_CALL步骤失败: taskId={}", taskId, e);
        }
    }

    /**
     * 记录 SUBAGENT_CALL 步骤（由 SubagentOrchestrator 回调）
     * @param agentName
     * @param subagentName
     * @param input
     * @param latencyMs
     * @param childTaskId
     */
    public void recordSubagentCall(String agentName, String subagentName,
                                   String input, long latencyMs, String childTaskId) {
        if (!enabled) {
            return;
        }
        try {
            AgentTaskStepInfo step = new AgentTaskStepInfo();
            step.setTaskId(taskId);
            step.setScopeId(scopeId);
            step.setStepOrder(stepOrder.incrementAndGet());
            step.setStepType("SUBAGENT_CALL");
            step.setAgentName(agentName);
            step.setToolName(subagentName);
            step.setStepContent("委派子代理: " + subagentName
                    + (childTaskId != null ? " (childTaskId=" + childTaskId + ")" : ""));
            step.setToolInput(truncate(input));
            step.setDurationMs(latencyMs);
            steps.add(step);
        } catch (Exception e) {
            log.warn("记录SUBAGENT_CALL步骤失败: taskId={}", taskId, e);
        }
    }

    /**
     * 更新最后一条TOOL_CALL步骤的工具输出、耗时和退出码
     * <p>
     * 在onToolResult回调中调用，补充工具执行结果、实际耗时和退出码。
     * </p>
     * @param toolOutput 工具输出文本
     * @param latencyMs 工具执行耗时（毫秒）
     * @param exitCode 工具退出码
     */
    public void updateLastToolCall(String toolOutput, long latencyMs, Integer exitCode) {
        if (!enabled) {
            return;
        }
        for (int i = steps.size() - 1; i >= 0; i--) {
            AgentTaskStepInfo step = steps.get(i);
            if ("TOOL_CALL".equals(step.getStepType())) {
                step.setToolOutput(truncate(toolOutput));
                step.setDurationMs(latencyMs);
                step.setExitCode(exitCode);
                break;
            }
        }
    }

    /**
     * 批量获取已采集步骤（任务完成时持久化）
     * @return
     */
    public List<AgentTaskStepInfo> drainSteps() {
        List<AgentTaskStepInfo> snapshot = new java.util.ArrayList<>(steps);
        steps.clear();
        return snapshot;
    }

    /**
     * 临时禁用（如结构化输出阶段）
     */
    public void disable() {
        this.enabled = false;
    }

    public String getTaskId() {
        return taskId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 从 ContentBlock 列表提取文本
     * @param blocks
     * @return
     */
    private String extractText(List<AgentContentBlock> blocks) {
        if (blocks == null || blocks.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentContentBlock block : blocks) {
            if (block instanceof AgentTextBlock tb) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(tb.getText());
            }
        }
        return truncate(sb.toString());
    }

    /**
     * 截断超长文本（先脱敏，再截断）
     * @param text
     * @return
     */
    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        String desensitized = SensitiveDataUtils.desensitize(text);
        return desensitized.length() > OUTPUT_TRUNCATE_LENGTH
                ? desensitized.substring(0, OUTPUT_TRUNCATE_LENGTH) + "...(truncated)"
                : desensitized;
    }

}
