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

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.yangqiongai.ai.agent.runtime.event.ModelCallStartInfo;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock;

/**
 * 调用中悬置状态配对器
 * <p>
 * 通过 START/END 事件载荷中的稳定键（模型调用为 agent+model+iteration，
 * 工具调用为 toolUseId）配对计算耗时。事件流暂无会话标识，
 * 同名Agent并发运行时配对为 best-effort，配对失败仅跳过耗时统计不影响计数。
 * </p>
 * @author yangqiong
 */
final class PendingCallTracker {

    /**
     * 悬置表容量上限，超过时清空防止中断路径泄漏
     */
    private static final int MAX_PENDING = 1024;

    /**
     * 悬置模型调用：键 agent|model|iteration -> 开始纳秒
     */
    private final ConcurrentHashMap<String, AtomicLong> pendingModelCalls = new ConcurrentHashMap<>();

    /**
     * 悬置工具调用：键 toolUseId -> 工具调用起始状态
     */
    private final ConcurrentHashMap<String, ToolTiming> pendingToolCalls = new ConcurrentHashMap<>();

    /**
     * 工具调用配对结果
     * @param toolName 工具名，未配对到START时为unknown
     * @param durationNanos 耗时纳秒，未配对时为-1
     * @param error 是否错误结果
     */
    record ToolOutcome(String toolName, long durationNanos, boolean error) {
    }

    /**
     * 工具调用起始状态
     * @param toolName 工具名
     * @param startNanos 开始纳秒
     */
    private record ToolTiming(String toolName, long startNanos) {
    }

    /**
     * 记录模型调用开始
     * @param info
     */
    void onModelCallStart(ModelCallStartInfo info) {
        if (info == null) {
            return;
        }
        evictIfNeeded(pendingModelCalls);
        pendingModelCalls.put(modelKey(info.getAgentName(), info.getModelName(), info.getIteration()),
                new AtomicLong(System.nanoTime()));
    }

    /**
     * 取走模型调用耗时纳秒，未配对返回null
     * @param endInfo
     * @return
     */
    Long takeModelDurationNanos(ModelCallStartInfo endInfo) {
        return takeModelDurationNanos(endInfo.getAgentName(), endInfo.getModelName(), endInfo.getIteration());
    }

    /**
     * 按配对键取走模型调用耗时纳秒，未配对返回null
     * @param agentName
     * @param modelName
     * @param iteration
     * @return
     */
    Long takeModelDurationNanos(String agentName, String modelName, int iteration) {
        AtomicLong start = pendingModelCalls.remove(modelKey(agentName, modelName, iteration));
        return start != null ? System.nanoTime() - start.get() : null;
    }

    /**
     * 记录一批工具调用开始
     * @param toolCalls
     */
    void onToolCallStart(List<AgentToolUseBlock> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return;
        }
        evictIfNeeded(pendingToolCalls);
        long now = System.nanoTime();
        for (AgentToolUseBlock block : toolCalls) {
            if (block.getToolUseId() != null) {
                pendingToolCalls.put(block.getToolUseId(),
                        new ToolTiming(block.getToolName(), now));
            }
        }
    }

    /**
     * 取走一批工具调用配对结果，未配对到START的仅返回计数信息
     * @param results
     * @return
     */
    List<ToolOutcome> takeToolOutcomes(List<AgentToolResultBlock> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        long now = System.nanoTime();
        java.util.ArrayList<ToolOutcome> outcomes = new java.util.ArrayList<>(results.size());
        for (AgentToolResultBlock block : results) {
            String toolUseId = block.getToolUseId();
            ToolTiming timing = toolUseId != null ? pendingToolCalls.remove(toolUseId) : null;
            if (timing != null) {
                outcomes.add(new ToolOutcome(timing.toolName(), now - timing.startNanos(), block.isError()));
            } else {
                outcomes.add(new ToolOutcome("unknown", -1L, block.isError()));
            }
        }
        return outcomes;
    }

    /**
     * 悬置表超限时整体清空，防止中断路径的悬置项泄漏
     * @param pendingMap
     */
    private static void evictIfNeeded(ConcurrentHashMap<?, ?> pendingMap) {
        if (pendingMap.size() >= MAX_PENDING) {
            pendingMap.clear();
        }
    }

    /**
     * 模型调用配对键
     * @param agentName
     * @param modelName
     * @param iteration
     * @return
     */
    private static String modelKey(String agentName, String modelName, int iteration) {
        return agentName + "|" + modelName + "|" + iteration;
    }
}
