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
package com.yangqiongai.ai.platform.api.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.AgentTaskInfo;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.repository.AgentTaskRepository;
import com.yangqiongai.ai.agent.core.repository.AgentTaskStepRepository;
import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;
import com.yangqiongai.ai.agent.data.trace.entity.TraceSpanEntity;
import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.agent.data.trace.repository.TraceSpanRepository;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Agent运行Trace查询接口
 * @author yangqiong
 */
@Tag(name = "Agent运行Trace接口")
@RestController
@RequestMapping("/api/agent/trace")
@ConditionalOnProperty(prefix = "ai.agent.trace", name = "enabled", havingValue = "true")
public class AgentTraceController {

    private static final Logger log = LoggerFactory.getLogger(AgentTraceController.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 重放来源标记body键
     */
    private static final String BODY_REPLAY_OF = "_replayOf";

    @Autowired
    private TraceSpanRepository traceSpanRepository;

    @Autowired
    private ContextSnapshotRepository contextSnapshotRepository;

    @Autowired
    private ForkService forkService;

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private AgentTaskStepRepository agentTaskStepRepository;

    @Autowired
    private AgentEngine agentEngine;

    /**
     * 分页查询运行列表(根Span联查任务信息)
     * @param agentCode Agent编码(可空)
     * @param taskId 任务ID(可空)
     * @param status Span状态(可空)
     * @param start 开始时间下界(可空)
     * @param end 开始时间上界(可空)
     * @param pageNum 页码(从1开始)
     * @param pageSize 每页条数
     * @return
     */
    @Operation(summary = "运行列表", description = "按Agent/任务/状态/时间范围分页查询运行记录，联查任务输入摘要与Token")
    @GetMapping("/runs")
    public ApiResult<List<Map<String, Object>>> runs(
            @Parameter(name = "agentCode", description = "Agent编码") @RequestParam(required = false) String agentCode,
            @Parameter(name = "taskId", description = "任务ID") @RequestParam(required = false) String taskId,
            @Parameter(name = "status", description = "Span状态(OK/ERROR)") @RequestParam(required = false) String status,
            @Parameter(name = "start", description = "开始时间下界(yyyy-MM-dd HH:mm:ss)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime start,
            @Parameter(name = "end", description = "开始时间上界(yyyy-MM-dd HH:mm:ss)")
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime end,
            @Parameter(name = "pageNum", description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(1, pageSize), 100);
        int offset = (Math.max(1, pageNum) - 1) * size;
        List<TraceSpanEntity> rootSpans = traceSpanRepository.findRootSpans(
                agentCode, taskId, status, start, end, offset, size);
        long total = traceSpanRepository.countRootSpans(agentCode, taskId, status, start, end);
        List<String> traceIds = rootSpans.stream().map(TraceSpanEntity::getTraceId).toList();
        Map<String, Long> spanCounts = traceSpanRepository.countByTraceIds(traceIds);

        List<Map<String, Object>> rows = new ArrayList<>(rootSpans.size());
        for (TraceSpanEntity root : rootSpans) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("traceId", root.getTraceId());
            row.put("taskId", root.getTaskId());
            row.put("agentCode", root.getAgentCode());
            row.put("sessionId", root.getSessionId());
            row.put("status", root.getStatus());
            row.put("errorMessage", root.getErrorMessage());
            row.put("durationMs", root.getDurationMs());
            row.put("startTime", root.getStartTime());
            row.put("spanCount", spanCounts.getOrDefault(root.getTraceId(), 0L));
            AgentTaskInfo task = root.getTaskId() != null ? agentTaskRepository.queryTask(root.getTaskId()) : null;
            if (task != null) {
                row.put("taskStatus", task.getTaskStatus());
                row.put("userInput", task.getUserInput());
                row.put("inputTokens", task.getInputTokens());
                row.put("outputTokens", task.getOutputTokens());
                row.put("totalTokens", task.getTotalTokens());
                // 轨迹分叉溯源：前端"来源"列展示"分叉自 {来源任务}·第N轮"并支持跳转
                row.put("parentTaskId", task.getParentTaskId());
                row.put("forkCallSeq", task.getForkCallSeq());
            }
            rows.add(row);
        }
        return ApiResult.okPage(rows, total, pageNum, size);
    }

    /**
     * 按追踪ID查询Span树
     * @param traceId
     * @return
     */
    @Operation(summary = "Span树", description = "返回一次运行的完整Span树(含耗时/状态/属性)")
    @GetMapping("/spans")
    public ApiResult<List<TraceSpanNode>> spans(
            @Parameter(name = "traceId", description = "追踪ID") @RequestParam String traceId) {
        List<TraceSpanEntity> spans = traceSpanRepository.findByTraceId(traceId);
        if (spans.isEmpty()) {
            return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(), "Trace不存在: " + traceId);
        }
        return ApiResult.ok(buildSpanTree(spans));
    }

    /**
     * 按任务ID查询步骤时间线
     * @param taskId
     * @return
     */
    @Operation(summary = "步骤时间线", description = "返回任务的LLM调用/工具调用步骤序列，与Span树互补")
    @GetMapping("/steps")
    public ApiResult<?> steps(
            @Parameter(name = "taskId", description = "任务ID") @RequestParam String taskId) {
        var steps = agentTaskStepRepository.querySteps(taskId);
        if (steps == null || steps.isEmpty()) {
            return ApiResult.fail(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode(), "任务步骤不存在: " + taskId);
        }
        // LLM_CALL步骤补callSeq：stepOrder混排工具步骤不等于模型调用序号，按LLM步骤出现序次动态计算，
        // 口径与上下文快照callSeq一致(任务内模型调用序号，从1递增)，供前端对齐分叉点快照
        int callSeq = 0;
        for (var step : steps) {
            if ("LLM_CALL".equals(step.getStepType())) {
                step.setCallSeq(++callSeq);
            }
        }
        return ApiResult.ok(steps);
    }

    /**
     * 删除一次运行的全部Span
     * @param traceId
     * @return
     */
    @Operation(summary = "删除运行Trace", description = "删除指定追踪ID的全部Span(合规要求)")
    @DeleteMapping("/runs")
    public ApiResult<Void> deleteRuns(
            @Parameter(name = "traceId", description = "追踪ID") @RequestParam String traceId) {
        int deleted = traceSpanRepository.deleteByTraceId(traceId);
        if (deleted == 0) {
            return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(), "Trace不存在: " + traceId);
        }
        return ApiResult.ok();
    }

    /**
     * 失败重放(仅重放输入，不恢复原会话记忆与中间状态；经引擎提交，同样过配额闸)
     * @param taskId 原任务ID
     * @return
     */
    @Operation(summary = "失败重放", description = "按原任务输入提交一次新运行(生成新taskId)，仅重放输入，不恢复会话状态")
    @PostMapping("/replay")
    public ApiResult<Map<String, Object>> replay(
            @Parameter(name = "taskId", description = "原任务ID") @RequestParam String taskId) {
        AgentTaskInfo task = agentTaskRepository.queryTask(taskId);
        if (task == null) {
            return ApiResult.fail(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode(), "任务不存在: " + taskId);
        }
        AgentRequest request = new AgentRequest();
        request.setAgentCode(task.getAgentCode());
        request.setUserId(task.getUserId());
        if (task.getUserInput() != null && !task.getUserInput().isBlank()) {
            request.setInput(task.getUserInput());
        }
        restoreBody(request, task.getBody());
        request.addBody(BODY_REPLAY_OF, taskId);
        String newTaskId = agentEngine.submitTask(request);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("originalTaskId", taskId);
        data.put("newTaskId", newTaskId);
        return ApiResult.ok(data);
    }

    /**
     * 按任务ID查询上下文快照摘要列表(不含消息全文)
     * @param taskId 任务ID
     * @return
     */
    @Operation(summary = "上下文快照列表", description = "按调用序号升序返回任务的模型调用上下文快照摘要(不含消息全文)")
    @GetMapping("/{taskId}/contexts")
    public ApiResult<List<Map<String, Object>>> contexts(
            @Parameter(name = "taskId", description = "任务ID") @PathVariable String taskId) {
        List<ContextSnapshotEntity> snapshots = contextSnapshotRepository.findByTaskId(taskId);
        List<Map<String, Object>> rows = new ArrayList<>(snapshots.size());
        for (ContextSnapshotEntity snapshot : snapshots) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskId", snapshot.getTaskId());
            row.put("traceId", snapshot.getTraceId());
            row.put("sessionId", snapshot.getSessionId());
            row.put("agentCode", snapshot.getAgentCode());
            row.put("modelCode", snapshot.getModelCode());
            row.put("callSeq", snapshot.getCallSeq());
            row.put("msgCount", snapshot.getMsgCount());
            row.put("totalChars", snapshot.getTotalChars());
            row.put("createdAt", snapshot.getCreatedAt());
            rows.add(row);
        }
        return ApiResult.ok(rows);
    }

    /**
     * 按任务ID与调用序号查询上下文快照明细(含消息全文)
     * @param taskId 任务ID
     * @param callSeq 调用序号
     * @return
     */
    @Operation(summary = "上下文快照明细", description = "返回指定调用序号注入模型的完整上下文消息(含来源标注与截断标记)")
    @GetMapping("/{taskId}/contexts/{callSeq}")
    public ApiResult<Map<String, Object>> contextDetail(
            @Parameter(name = "taskId", description = "任务ID") @PathVariable String taskId,
            @Parameter(name = "callSeq", description = "调用序号") @PathVariable int callSeq) {
        ContextSnapshotEntity snapshot = contextSnapshotRepository.findByTaskIdAndCallSeq(taskId, callSeq);
        if (snapshot == null) {
            return ApiResult.fail(AiErrorCode.NOT_FOUND.getCode(),
                    "上下文快照不存在: taskId=" + taskId + ", callSeq=" + callSeq);
        }
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", snapshot.getTaskId());
        detail.put("traceId", snapshot.getTraceId());
        detail.put("sessionId", snapshot.getSessionId());
        detail.put("agentCode", snapshot.getAgentCode());
        detail.put("scopeId", snapshot.getScopeId());
        detail.put("modelCode", snapshot.getModelCode());
        detail.put("callSeq", snapshot.getCallSeq());
        detail.put("msgCount", snapshot.getMsgCount());
        detail.put("totalChars", snapshot.getTotalChars());
        detail.put("createdAt", snapshot.getCreatedAt());
        detail.put("messages", parseMessages(snapshot.getSnapshotJson()));
        return ApiResult.ok(detail);
    }

    /**
     * 轨迹分叉(从指定模型调用快照派生新任务复现调试)
     * @param taskId 来源任务ID
     * @param forkRequest 分叉参数(callSeq/userInputOverride/remark)
     * @return
     */
    @Operation(summary = "轨迹分叉", description = "从指定调用快照分叉新任务: 恢复原任务body并携带溯源标记(parentTaskId/forkCallSeq)，经引擎提交")
    @PostMapping("/{taskId}/fork")
    public ApiResult<Map<String, Object>> fork(
            @Parameter(name = "taskId", description = "来源任务ID") @PathVariable String taskId,
            @RequestBody(required = false) ForkRequest forkRequest) {
        ForkRequest request = forkRequest != null ? forkRequest : new ForkRequest();
        return ApiResult.ok(forkService.fork(taskId, request));
    }

    /**
     * 解析快照消息JSON(失败时返回原始字符串)
     * @param snapshotJson 快照JSON
     * @return
     */
    private Object parseMessages(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return List.of();
        }
        try {
            return OBJECT_MAPPER.readValue(snapshotJson, List.class);
        } catch (Exception e) {
            log.warn("快照消息解析失败, 返回原始内容: {}", e.getMessage());
            return snapshotJson;
        }
    }

    /**
     * 恢复原请求body(重置任务追踪字段，生成新run)
     * @param request 新请求
     * @param bodyJson 原任务body JSON
     */
    @SuppressWarnings("unchecked")
    private void restoreBody(AgentRequest request, String bodyJson) {
        if (bodyJson == null || bodyJson.isBlank()) {
            return;
        }
        try {
            Map<String, Object> body = OBJECT_MAPPER.readValue(bodyJson, Map.class);
            body.remove(AgentRequest.BodyKeys.TASK_ID);
            request.setBody(body);
        } catch (Exception e) {
            log.warn("重放恢复body失败, 按无body重放: {}", e.getMessage());
        }
    }

    /**
     * 内存组装Span树(多个根节点时返回多棵树)
     * @param spans 按开始时间升序的Span列表
     * @return
     */
    private List<TraceSpanNode> buildSpanTree(List<TraceSpanEntity> spans) {
        Map<String, TraceSpanNode> nodeMap = new HashMap<>(spans.size());
        for (TraceSpanEntity span : spans) {
            nodeMap.put(span.getSpanId(), toNode(span));
        }
        List<TraceSpanNode> roots = new ArrayList<>();
        for (TraceSpanEntity span : spans) {
            TraceSpanNode node = nodeMap.get(span.getSpanId());
            TraceSpanNode parent = span.getParentSpanId() != null ? nodeMap.get(span.getParentSpanId()) : null;
            if (parent != null && parent != node) {
                parent.getChildren().add(node);
            } else {
                roots.add(node);
            }
        }
        return roots;
    }

    /**
     * 实体转树节点
     * @param span
     * @return
     */
    private TraceSpanNode toNode(TraceSpanEntity span) {
        TraceSpanNode node = new TraceSpanNode();
        node.setSpanId(span.getSpanId());
        node.setParentSpanId(span.getParentSpanId());
        node.setTraceId(span.getTraceId());
        node.setOperation(span.getOperation());
        node.setStatus(span.getStatus());
        node.setErrorMessage(span.getErrorMessage());
        node.setDurationMs(span.getDurationMs());
        node.setStartTime(span.getStartTime() != null ? span.getStartTime().toString() : null);
        node.setAttributes(parseAttributes(span.getAttributes()));
        return node;
    }

    /**
     * 解析属性JSON(失败时返回原始字符串)
     * @param attributesJson
     * @return
     */
    private Object parseAttributes(String attributesJson) {
        if (attributesJson == null || attributesJson.isBlank()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(attributesJson, Map.class);
        } catch (Exception e) {
            return attributesJson;
        }
    }
}
