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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.spi.NotifySendCommand;
import com.yangqiongai.ai.workflow.spi.NotifySendResult;
import com.yangqiongai.ai.workflow.spi.WorkflowNotifySender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 通知节点处理
 * @author yangqiong
 */
@Service
public class NotifyNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(NotifyNodeHandler.class);

    /**
     * 通知结果固定变量名
     */
    public static final String RESULT_VARIABLE = "notifyOutput";

    private final ObjectProvider<WorkflowNotifySender> senderProvider;

    private final WorkflowStateService stateService;

    private final ObjectMapper objectMapper;

    /**
     * 异步发送线程池（发起即走，不阻塞流程推进）
     */
    private final ExecutorService asyncExecutor = new ThreadPoolExecutor(
            2, 4, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(100),
            r -> {
                Thread t = new Thread(r, "workflow-notify-async");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    public NotifyNodeHandler(ObjectProvider<WorkflowNotifySender> senderProvider,
                             WorkflowStateService stateService,
                             ObjectMapper objectMapper) {
        this.senderProvider = senderProvider;
        this.stateService = stateService;
        this.objectMapper = objectMapper;
    }

    @PreDestroy
    public void shutdown() {
        asyncExecutor.shutdown();
    }

    /**
     * 执行通知节点：输出主体为上游数据透传，同步模式把发送结果写入固定变量 notifyOutput
     * @param context
     * @param state
     * @param node
     * @return 发送失败且未忽略失败时返回failure，其余场景恒返回success保证主流程不受影响
     */
    public AgentResult executeNotifyNode(AgentContext context, WorkflowState state, WorkflowNode node) {
        Map<String, Object> passthrough = stateService.resolveNodeInput(node, state);
        String passthroughJson = toJson(passthrough);

        WorkflowNotifySender sender = senderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("WorkflowNotifySender未装配，通知节点降级跳过: nodeId={}", node.getId());
            storeResult(state, node, buildResult(node, false, true, null, "通知能力未启用"));
            return AgentResult.success(passthroughJson);
        }

        boolean async = getConfigBoolean(node, "async", false);
        boolean ignoreFailure = getConfigBoolean(node, "ignoreFailure", true);
        NotifySendCommand command = buildCommand(node, state);
        if (command.getContent() == null || command.getContent().isBlank()) {
            log.warn("通知内容为空: nodeId={}", node.getId());
            Map<String, Object> result = buildResult(node, false, false, null, "通知内容为空");
            return finish(state, node, passthroughJson, result, ignoreFailure);
        }

        if (async) {
            asyncExecutor.submit(() -> {
                try {
                    NotifySendResult sendResult = sender.send(command);
                    log.info("异步通知完成: nodeId={}, success={}", node.getId(), sendResult.isSuccess());
                } catch (Exception e) {
                    log.warn("异步通知发送异常（不影响主流程）: nodeId={}, error={}", node.getId(), e.getMessage());
                }
            });
            return AgentResult.success(passthroughJson);
        }

        NotifySendResult sendResult;
        try {
            sendResult = sender.send(command);
        } catch (Exception e) {
            log.warn("通知发送异常: nodeId={}, error={}", node.getId(), e.getMessage());
            sendResult = NotifySendResult.fail("发送异常: " + e.getMessage());
        }
        Map<String, Object> result = sendResult.isSuccess()
                ? buildResult(node, true, false, sendResult.getMessageId(), null)
                : buildResult(node, false, false, null, sendResult.getError());
        return finish(state, node, passthroughJson, result, ignoreFailure);
    }

    /**
     * 写入结果变量并按忽略失败策略组装返回值
     * @param state
     * @param node
     * @param passthroughJson
     * @param result
     * @param ignoreFailure
     * @return
     */
    private AgentResult finish(WorkflowState state, WorkflowNode node, String passthroughJson,
                               Map<String, Object> result, boolean ignoreFailure) {
        storeResult(state, node, result);
        if (!result.get("success").equals(true) && !ignoreFailure) {
            return AgentResult.failure("通知失败: " + result.get("error"));
        }
        return AgentResult.success(passthroughJson);
    }

    /**
     * 组装通知发送命令并渲染模板
     * @param node
     * @param state
     * @return
     */
    private NotifySendCommand buildCommand(WorkflowNode node, WorkflowState state) {
        NotifySendCommand command = new NotifySendCommand();
        command.setChannelId(node.getConfigString("channelId"));
        command.setChannelType(node.getConfigString("channelType"));
        command.setTitle(stateService.resolveTemplateString(node.getConfigString("title"), state));
        command.setContent(stateService.resolveTemplateString(node.getConfigString("content"), state));
        Object level = node.getConfigValue("level");
        command.setLevel(level != null ? level.toString() : "INFO");
        Object overrideObj = node.getConfigValue("override");
        if (overrideObj instanceof Map<?, ?> overrideMap && !overrideMap.isEmpty()) {
            Map<String, Object> overrides = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : overrideMap.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String text) {
                    value = stateService.resolveTemplateString(text, state);
                }
                overrides.put(String.valueOf(entry.getKey()), value);
            }
            command.setOverrides(overrides);
        }
        return command;
    }

    /**
     * 组装通知结果数据
     * @param node
     * @param success
     * @param skipped
     * @param messageId
     * @param error
     * @return
     */
    private Map<String, Object> buildResult(WorkflowNode node, boolean success, boolean skipped,
                                            String messageId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeId", node.getId());
        result.put("nodeName", node.getName());
        result.put("success", success);
        result.put("channelId", node.getConfigString("channelId"));
        result.put("channelType", node.getConfigString("channelType"));
        result.put("messageId", messageId);
        result.put("error", error);
        result.put("skipped", skipped);
        result.put("timestamp", System.currentTimeMillis());
        return result;
    }

    /**
     * 把发送结果写入固定变量与节点级变量
     * @param state
     * @param node
     * @param result
     */
    private void storeResult(WorkflowState state, WorkflowNode node, Map<String, Object> result) {
        String json = toJson(result);
        state.setVariable(RESULT_VARIABLE, json);
        state.setVariable(node.getId() + "." + RESULT_VARIABLE, json);
    }

    /**
     * 读取布尔配置
     * @param node
     * @param key
     * @param defaultValue
     * @return
     */
    private boolean getConfigBoolean(WorkflowNode node, String key, boolean defaultValue) {
        Object value = node.getConfigValue(key);
        return value instanceof Boolean b ? b : defaultValue;
    }

    /**
     * 序列化JSON，失败时降级为字符串描述
     * @param value
     * @return
     */
    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }
}
