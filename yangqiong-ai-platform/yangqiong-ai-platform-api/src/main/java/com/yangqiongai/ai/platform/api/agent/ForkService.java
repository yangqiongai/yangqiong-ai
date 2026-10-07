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
import com.yangqiongai.ai.agent.data.trace.entity.ContextSnapshotEntity;
import com.yangqiongai.ai.agent.data.trace.repository.ContextSnapshotRepository;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 轨迹分叉调试
 * <p>
 * 从指定任务的某次模型调用快照分叉出新任务调试：
 * 校验来源任务与快照存在后，将快照中非系统提示消息构造为种子上下文（body.seedMessages），
 * 引擎从该上下文继续执行；快照解析失败时降级为原输入复现重跑。
 * 恢复原任务body并携带溯源标记，经引擎提交新任务（同样过配额闸）。
 * </p>
 * @author yangqiong
 */
@Service
public class ForkService {

    private static final Logger log = LoggerFactory.getLogger(ForkService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 分叉来源标记body键
     */
    private static final String BODY_FORK_OF = "_forkOf";

    @Autowired
    private AgentTaskRepository agentTaskRepository;

    @Autowired
    private ContextSnapshotRepository contextSnapshotRepository;

    @Autowired
    private AgentEngine agentEngine;

    /**
     * 从指定快照分叉新任务
     * @param taskId 来源任务ID
     * @param forkRequest 分叉参数(调用序号/覆盖输入/备注)
     * @return 新任务ID
     */
    public Map<String, Object> fork(String taskId, ForkRequest forkRequest) {
        AgentTaskInfo task = agentTaskRepository.queryTask(taskId);
        if (task == null) {
            throw new AiException(AiErrorCode.AGENT_TASK_NOT_FOUND, "任务不存在: " + taskId);
        }
        ContextSnapshotEntity snapshot = resolveSnapshot(taskId, forkRequest.getCallSeq());
        if (snapshot == null) {
            throw new AiException(AiErrorCode.NOT_FOUND,
                    "上下文快照不存在: taskId=" + taskId + ", callSeq=" + forkRequest.getCallSeq());
        }

        String newTaskId = submitForkTask(task, snapshot, forkRequest);
        log.info("轨迹分叉完成: sourceTaskId={}, forkCallSeq={}, newTaskId={}",
                taskId, snapshot.getCallSeq(), newTaskId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("originalTaskId", taskId);
        result.put("forkCallSeq", snapshot.getCallSeq());
        result.put("newTaskId", newTaskId);
        return result;
    }

    /**
     * 解析分叉点快照：指定序号查精确快照，未指定取最后一次调用
     * @param taskId 来源任务ID
     * @param callSeq 调用序号(可空)
     * @return 快照实体
     */
    private ContextSnapshotEntity resolveSnapshot(String taskId, Integer callSeq) {
        if (callSeq != null) {
            return contextSnapshotRepository.findByTaskIdAndCallSeq(taskId, callSeq);
        }
        List<ContextSnapshotEntity> snapshots = contextSnapshotRepository.findByTaskId(taskId);
        return snapshots.isEmpty() ? null : snapshots.get(snapshots.size() - 1);
    }

    /**
     * 提交分叉任务：恢复原任务body，快照解析成功时构造种子上下文继续执行，失败时降级复现重跑(同样过配额闸)
     * @param task 来源任务
     * @param snapshot 分叉点快照
     * @param forkRequest 分叉参数
     * @return 新任务ID
     */
    private String submitForkTask(AgentTaskInfo task, ContextSnapshotEntity snapshot, ForkRequest forkRequest) {
        AgentRequest request = new AgentRequest();
        request.setAgentCode(task.getAgentCode());
        request.setUserId(task.getUserId());
        List<Map<String, Object>> seeds = buildSeedMessages(snapshot, forkRequest.getUserInputOverride());
        if (!seeds.isEmpty()) {
            // 种子模式：快照上下文作为种子继续执行，覆盖输入追加到种子末尾，不再重复注入input；
            // 不复用原会话，避免会话历史恢复与种子上下文重复注入
            request.addBody(AgentRequest.BodyKeys.SEED_MESSAGES, seeds);
        } else {
            // 降级模式：无可用种子时按原任务输入复现重跑，沿用原会话以恢复上下文
            request.setSessionId(task.getSessionId());
            String input = forkRequest.getUserInputOverride() != null && !forkRequest.getUserInputOverride().isBlank()
                    ? forkRequest.getUserInputOverride() : task.getUserInput();
            if (input != null && !input.isBlank()) {
                request.setInput(input);
            }
        }
        restoreBody(request, task.getBody());
        request.addBody(AgentRequest.BodyKeys.PARENT_TASK_ID, task.getTaskId());
        request.addBody(AgentRequest.BodyKeys.FORK_CALL_SEQ, snapshot.getCallSeq());
        if (forkRequest.getRemark() != null && !forkRequest.getRemark().isBlank()) {
            request.addBody(BODY_FORK_OF, forkRequest.getRemark());
        }
        return agentEngine.submitTask(request);
    }

    /**
     * 从快照构造种子上下文消息
     * <p>
     * 解析快照snapshot_json，取source非system_prompt的条目映射为{role,content}（role统一小写），
     * 覆盖输入非空时在末尾追加user条目。解析失败或无有效条目时返回空列表并warn，由调用方降级复现重跑。
     * </p>
     * @param snapshot 分叉点快照
     * @param overrideInput 覆盖输入(可空)
     * @return
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> buildSeedMessages(ContextSnapshotEntity snapshot, String overrideInput) {
        String snapshotJson = snapshot.getSnapshotJson();
        if (snapshotJson == null || snapshotJson.isBlank()) {
            log.warn("分叉快照消息为空, 降级复现重跑: taskId={}, callSeq={}",
                    snapshot.getTaskId(), snapshot.getCallSeq());
            return List.of();
        }
        List<Map<String, Object>> seeds = new ArrayList<>();
        try {
            List<Object> messages = OBJECT_MAPPER.readValue(snapshotJson, List.class);
            for (Object message : messages) {
                if (!(message instanceof Map)) {
                    continue;
                }
                Map<String, Object> item = (Map<String, Object>) message;
                Object source = item.get("source");
                if (source instanceof String s && "system_prompt".equals(s)) {
                    continue;
                }
                Object role = item.get("role");
                Object content = item.get("content");
                if (!(role instanceof String roleText) || content == null) {
                    continue;
                }
                Map<String, Object> seed = new LinkedHashMap<>();
                seed.put("role", roleText.toLowerCase());
                seed.put("content", content);
                seeds.add(seed);
            }
        } catch (Exception e) {
            log.warn("分叉快照解析失败, 降级复现重跑: taskId={}, callSeq={}, {}",
                    snapshot.getTaskId(), snapshot.getCallSeq(), e.getMessage());
            return List.of();
        }
        if (overrideInput != null && !overrideInput.isBlank() && !seeds.isEmpty()) {
            Map<String, Object> override = new LinkedHashMap<>();
            override.put("role", "user");
            override.put("content", overrideInput);
            seeds.add(override);
        }
        return seeds;
    }

    /**
     * 恢复原请求body(重置任务追踪字段)
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
            body.remove(AgentRequest.BodyKeys.PARENT_TASK_ID);
            body.remove(AgentRequest.BodyKeys.FORK_CALL_SEQ);
            request.setBody(body);
        } catch (Exception e) {
            log.warn("分叉恢复body失败, 按无body分叉: {}", e.getMessage());
        }
    }
}
