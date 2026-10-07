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
package com.yangqiongai.ai.workflow.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeExecutionStatus;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowEdge;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.store.WorkflowStateStore;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流状态管理
 * @author yangqiong
 */
@Service
public class WorkflowStateService {

    private static final Logger log = LoggerFactory.getLogger(WorkflowStateService.class);

    private static final String ATTR_PERSIST_FAILURE_COUNT = "_persistFailureCount";

    private final WorkflowStateStore stateStore;

    private final ObjectMapper objectMapper;

    public WorkflowStateService(WorkflowStateStore stateStore, ObjectMapper objectMapper) {
        this.stateStore = stateStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化工作流状态
     * @param definition
     * @return
     */
    public WorkflowState initializeState(WorkflowDefinition definition) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(UUID.randomUUID().toString());
        state.setDefinitionName(definition.getName());
        state.setStatus(ExecutionStatus.PENDING);
        state.setNodeStates(new ConcurrentHashMap<>());
        state.setVariables(new ConcurrentHashMap<>());
        state.setCreateTime(System.currentTimeMillis());
        state.setUpdateTime(System.currentTimeMillis());
        state.setVariable("_nodeTimeoutSeconds", definition.getNodeTimeoutSeconds());
        return state;
    }

    /**
     * 从上下文注入初始变量到工作流状态
     * @param context
     * @param state
     */
    @SuppressWarnings("unchecked")
    public void injectInitialVariables(AgentContext context, WorkflowState state) {
        if (context == null) return;
        Object vars = context.getAttribute("initialVariables");
        if (vars instanceof Map) {
            Map<String, Object> initialVars = (Map<String, Object>) vars;
            initialVars.forEach(state::setVariable);
            log.info("注入初始变量: {}", initialVars.keySet());
        }
        // 用户输入写入input变量，供TRANSFORM/CONDITION/SCRIPT等节点通过${input}引用；不覆盖initialVariables显式传入的input
        if (context.getRequest() != null && context.getRequest().getInput() != null
                && !context.getRequest().getInput().isEmpty() && state.getVariable("input") == null) {
            String inputText = ContentBlockConverter.toInputText(context.getRequest().getInput());
            if (inputText != null && !inputText.isBlank()) {
                state.setVariable("input", inputText);
            }
        }
    }

    /**
     * 序列化工作流定义快照
     * @param definition
     * @return
     */
    public String serializeDefinitionSnapshot(WorkflowDefinition definition) {
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            log.warn("序列化工作流定义快照失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 持久化工作流状态
     * @param definition
     * @param state
     */
    public void persistState(WorkflowDefinition definition, WorkflowState state) {
        if (definition.getStateConfig() != null && !definition.getStateConfig().isPersistEnabled()) {
            return;
        }
        try {
            // 每次持久化都刷新心跳，供僵尸实例回收判定存活
            state.setLastHeartbeatTime(System.currentTimeMillis());
            stateStore.save(state);
            state.setVariable(ATTR_PERSIST_FAILURE_COUNT, 0);
        } catch (Exception e) {
            int failCount = 0;
            Object countObj = state.getVariable(ATTR_PERSIST_FAILURE_COUNT);
            if (countObj instanceof Number) {
                failCount = ((Number) countObj).intValue();
            }
            failCount++;
            state.setVariable(ATTR_PERSIST_FAILURE_COUNT, failCount);
            if (failCount >= 3) {
                log.error("工作流状态连续持久化失败{}次，标记工作流为FAILED: instanceId={}", failCount, state.getInstanceId(), e);
                state.setStatus(ExecutionStatus.FAILED);
                state.setVariable("errorMessage", "状态持久化连续失败");
            } else {
                log.error("工作流状态持久化失败({}/3): instanceId={}", failCount, state.getInstanceId(), e);
            }
        }
    }

    /**
     * 刷新运行中实例的心跳（不触碰其他状态，长节点执行期间保活）
     * @param instanceId
     * @return 实例存在且运行中返回true
     */
    public boolean refreshHeartbeat(String instanceId) {
        WorkflowState state = stateStore.load(instanceId);
        if (state == null || !state.isRunning()) {
            return false;
        }
        state.setLastHeartbeatTime(System.currentTimeMillis());
        stateStore.save(state);
        return true;
    }

    /**
     * 解析节点输入
     * @param node
     * @param state
     * @return
     */
    public Map<String, Object> resolveNodeInput(WorkflowNode node, WorkflowState state) {
        Map<String, Object> input = new LinkedHashMap<>();
        if (node.getInputMappings() != null && !node.getInputMappings().isEmpty()) {
            for (Map.Entry<String, String> entry : node.getInputMappings().entrySet()) {
                String paramName = entry.getKey();
                String varRef = entry.getValue();
                Object value = resolveVariableReference(varRef, state);
                input.put(paramName, value);
            }
        } else {
            // 默认输入：记录当前工作流变量的快照（排除内部变量与其他节点的输入变量，防止嵌套膨胀）
            if (state.getVariables() != null) {
                for (Map.Entry<String, Object> entry : state.getVariables().entrySet()) {
                    if (!entry.getKey().startsWith("_") && !entry.getKey().endsWith(".input")) {
                        input.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return input;
    }

    /**
     * 解析变量引用表达式
     * 支持：${varName}、${nodeId.field}、纯值
     * @param varRef
     * @param state
     * @return
     */
    public Object resolveVariableReference(String varRef, WorkflowState state) {
        if (varRef == null) return null;
        if (varRef.startsWith("${") && varRef.endsWith("}")) {
            String varName = varRef.substring(2, varRef.length() - 1);
            return state.getVariable(varName);
        }
        return varRef;
    }

    private static final Pattern TEMPLATE_VAR_PATTERN = Pattern.compile("\\$\\{(\\w[\\w.]*)}");

    /**
     * 模板字符串解析：将 ${varName} 替换为变量值（使用 Matcher 一次性替换，避免嵌套替换风险）
     * @param template
     * @param state
     * @return
     */
    public String resolveTemplateString(String template, WorkflowState state) {
        if (template == null) return null;
        Matcher matcher = TEMPLATE_VAR_PATTERN.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String varName = matcher.group(1);
            Object value = state.getVariable(varName);
            String replacement = value != null ? Matcher.quoteReplacement(value.toString()) : "";
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * 解析节点输出：根据outputMappings将结果写入工作流变量
     * @param node
     * @param result
     * @param state
     * @return
     */
    public Map<String, Object> resolveNodeOutput(WorkflowNode node, AgentResult result, WorkflowState state) {
        Map<String, Object> output = new LinkedHashMap<>();

        output.put("success", result.isSuccess());
        if (result.getOutputAsText() != null && !result.getOutputAsText().isEmpty()) {
            output.put("output", result.getOutputAsText());
        }
        if (!result.isSuccess() && result.getErrorMessage() != null) {
            output.put("errorMessage", result.getErrorMessage());
        }

        if (node.getOutputMappings() != null && !node.getOutputMappings().isEmpty()) {
            for (Map.Entry<String, String> entry : node.getOutputMappings().entrySet()) {
                String outputField = entry.getKey();
                String varName = entry.getValue();
                Object value = null;
                if ("output".equals(outputField) && result.getOutputAsText() != null) {
                    value = result.getOutputAsText();
                } else if (result.getFinalPayload() != null && result.getFinalPayload().containsKey(outputField)) {
                    value = result.getFinalPayload().get(outputField);
                }
                if (value != null) {
                    output.put(outputField, value);
                    state.setVariable(varName, value);
                }
            }
        } else {
            // 条件节点不写路由描述文字到output（透传内容由executeConditionNode写入），避免下游自动继承到描述文字
            if (result.isSuccess() && result.getOutputAsText() != null
                    && node.getType() != NodeType.CONDITION) {
                state.setVariable(node.getId() + ".output", result.getOutputAsText());
            }
        }

        if (result.getFinalPayload() != null) {
            for (Map.Entry<String, Object> entry : result.getFinalPayload().entrySet()) {
                if (!output.containsKey(entry.getKey())) {
                    output.put(entry.getKey(), entry.getValue());
                }
            }
        }

        return output;
    }

    /**
     * 构建输出文本（END前驱节点的输出汇总）
     * @param state
     * @param definition
     * @return
     */
    public String buildOutput(WorkflowState state, WorkflowDefinition definition) {
        StringBuilder output = new StringBuilder();

        Set<String> endPredecessorIds = new java.util.HashSet<>();
        if (definition != null && definition.getEdges() != null) {
            for (WorkflowEdge edge : definition.getEdges()) {
                WorkflowNode targetNode = definition.findNode(edge.getTargetId());
                if (targetNode != null && targetNode.getType() == NodeType.END) {
                    endPredecessorIds.add(edge.getSourceId());
                }
            }
        }

        if (state.getNodeStates() != null) {
            for (Map.Entry<String, NodeExecutionStatus> entry : state.getNodeStates().entrySet()) {
                NodeExecutionStatus nodeStatus = entry.getValue();
                if (nodeStatus.isCompleted() && nodeStatus.getOutput() != null
                        && nodeStatus.getOutput().getOutputAsText() != null
                        && !nodeStatus.getOutput().getOutputAsText().isEmpty()
                        && (endPredecessorIds.isEmpty() || endPredecessorIds.contains(entry.getKey()))) {
                    if (output.length() > 0) {
                        output.append("\n");
                    }
                    output.append(nodeStatus.getOutput().getOutputAsText());
                }
            }
        }

        return output.toString();
    }

    /**
     * 构建结构化工作流执行结果
     * @param state
     * @param definition
     * @param workflowStartTime
     * @return
     */
    public WorkflowExecuteResult buildWorkflowExecuteResult(WorkflowState state, WorkflowDefinition definition,
                                                             long workflowStartTime) {
        WorkflowExecuteResult result = new WorkflowExecuteResult();
        result.setSuccess(state.isCompleted());
        result.setInstanceId(state.getInstanceId());
        result.setDefinitionName(state.getDefinitionName());
        result.setStatus(state.getStatus());
        String aggregatedText = buildOutput(state, definition);
        result.setOutput(aggregatedText.isEmpty()
                ? List.of()
                : ContentBlockConverter.fromOutputText(aggregatedText));
        result.setTotalDurationMs(System.currentTimeMillis() - workflowStartTime);

        Map<String, Object> filteredVars = orderVariablesForDisplay(state);

        List<WorkflowExecuteResult.NodeSummary> summaries = new ArrayList<>();
        if (state.getNodeStates() != null) {
            // nodeStates为ConcurrentHashMap无序，按节点开始执行时间排序保证轨迹顺序稳定
            state.getNodeStates().entrySet().stream()
                    .sorted(java.util.Comparator.comparingLong(e ->
                            e.getValue().getStartTime() != null ? e.getValue().getStartTime() : Long.MAX_VALUE))
                    .forEach(entry -> {
                NodeExecutionStatus nodeStatus = entry.getValue();
                WorkflowExecuteResult.NodeSummary summary = new WorkflowExecuteResult.NodeSummary();
                summary.setNodeId(nodeStatus.getNodeId());
                summary.setNodeName(nodeStatus.getNodeName());
                summary.setStatus(nodeStatus.getStatus());
                summary.setDurationMs(nodeStatus.getDuration());
                summary.setErrorMessage(nodeStatus.getErrorMessage());
                summary.setRetryCount(nodeStatus.getRetryCount());
                summary.setIterationCount(nodeStatus.getIterationCount());

                WorkflowNode node = definition != null ? definition.findNode(entry.getKey()) : null;
                summary.setNodeType(node != null ? node.getType().name() : "UNKNOWN");
                summary.setInput(nodeStatus.getInput());

                if (nodeStatus.getOutputData() != null) {
                    summary.setOutput(nodeStatus.getOutputData());
                } else if (nodeStatus.getOutput() != null) {
                    Map<String, Object> legacyOutput = new LinkedHashMap<>();
                    legacyOutput.put("success", nodeStatus.getOutput().isSuccess());
                    if (nodeStatus.getOutput().getOutputAsText() != null) {
                        legacyOutput.put("output", nodeStatus.getOutput().getOutputAsText());
                    }
                    summary.setOutput(legacyOutput);
                }

                summaries.add(summary);
            });
        }
        result.setVariables(filteredVars);
        result.setNodeSummaries(summaries);

        return result;
    }

    /**
     * 按节点执行顺序整理流程变量展示（variables为ConcurrentHashMap无序，节点变量按节点开始时间置前，独立变量按名称殿后）
     * @param state
     * @return
     */
    public Map<String, Object> orderVariablesForDisplay(WorkflowState state) {
        Map<String, Object> ordered = new LinkedHashMap<>();
        if (state.getVariables() == null) {
            return ordered;
        }
        List<Map.Entry<String, NodeExecutionStatus>> sortedNodes = state.getNodeStates() != null
                ? state.getNodeStates().entrySet().stream()
                        .sorted(Comparator.comparingLong(e ->
                                e.getValue().getStartTime() != null ? e.getValue().getStartTime() : Long.MAX_VALUE))
                        .toList()
                : List.of();
        Set<String> placed = new HashSet<>();
        for (Map.Entry<String, NodeExecutionStatus> nodeEntry : sortedNodes) {
            String prefix = nodeEntry.getKey() + ".";
            List<String> nodeKeys = new ArrayList<>();
            for (String key : state.getVariables().keySet()) {
                if (!key.startsWith("_") && !placed.contains(key)
                        && (key.equals(nodeEntry.getKey()) || key.startsWith(prefix))) {
                    nodeKeys.add(key);
                }
            }
            // 节点内按input在前、其余字母序排列，保证展示顺序稳定
            nodeKeys.sort((a, b) -> {
                boolean aInput = a.endsWith(".input");
                boolean bInput = b.endsWith(".input");
                if (aInput != bInput) {
                    return aInput ? -1 : 1;
                }
                return a.compareTo(b);
            });
            for (String key : nodeKeys) {
                ordered.put(key, state.getVariables().get(key));
                placed.add(key);
            }
        }
        TreeMap<String, Object> rest = new TreeMap<>();
        for (Map.Entry<String, Object> entry : state.getVariables().entrySet()) {
            if (!entry.getKey().startsWith("_") && !placed.contains(entry.getKey())) {
                rest.put(entry.getKey(), entry.getValue());
            }
        }
        ordered.putAll(rest);
        return ordered;
    }

    /**
     * 节点类型转换
     * @param node
     * @param targetClass
     * @return
     */
    public <T extends WorkflowNode> T castNode(WorkflowNode node, Class<T> targetClass) {
        if (targetClass.isInstance(node)) {
            return targetClass.cast(node);
        }
        try {
            // convertValue完整拷贝所有字段（含inputMappings/outputMappings/approvalConfig等顶层字段）
            return objectMapper.convertValue(node, targetClass);
        } catch (Exception e) {
            throw new RuntimeException("节点类型转换失败: " + node.getId(), e);
        }
    }
}
