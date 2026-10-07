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
package com.yangqiongai.ai.agent.core.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 引擎确认待恢复注册表
 * <p>
 * 引擎审批暂停时按会话登记恢复现场，外部API提交批准/拒绝后弹出登记续跑；
 * 单机默认内存存储随登记携带内存句柄（原快照续跑路径），
 * 配置ai.agent.harness.distributed-store=jdbc时登记持久化，跨节点恢复凭登记数据重建现场。
 * </p>
 * @author yangqiong
 */
@Component
public class ConfirmPendingRegistry {

    private static final Logger log = LoggerFactory.getLogger(ConfirmPendingRegistry.class);

    /**
     * 登记过期时间
     */
    private static final Duration HANDLE_TTL = Duration.ofMinutes(30);

    /**
     * 引擎运行ID在运行时上下文中的属性键（引擎侧常量值，框架侧字符串对齐）
     */
    private static final String ATTR_RUN_ID = "harness.runId";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final PendingResumeStore pendingResumeStore;

    /**
     * 以登记存储装配注册表
     * @param pendingResumeStore
     */
    public ConfirmPendingRegistry(PendingResumeStore pendingResumeStore) {
        this.pendingResumeStore = pendingResumeStore;
    }

    /**
     * 登记待恢复现场
     * @param handle
     */
    public void register(ConfirmResumeHandle handle) {
        pendingResumeStore.register(buildEntry(handle));
        log.info("登记引擎确认待恢复现场: sessionId={}, pendingTools={}",
                handle.getSessionId(),
                handle.getPendingToolCalls().stream()
                        .map(call -> call.getToolName() + "#" + call.getToolUseId())
                        .toList());
    }

    /**
     * 弹出待恢复登记（取后即删，防重复恢复）
     * @param sessionId
     * @return 不存在或已过期返回null
     */
    public PendingResumeEntry popEntry(String sessionId) {
        PendingResumeEntry entry = pendingResumeStore.pop(requestId(sessionId));
        if (entry != null && entry.getCreatedAt() != null
                && entry.getCreatedAt().plus(HANDLE_TTL).isBefore(Instant.now())) {
            log.warn("引擎确认待恢复登记已过期: sessionId={}", sessionId);
            return null;
        }
        return entry;
    }

    /**
     * 弹出待恢复句柄（同节点快照续跑路径）
     * @param sessionId
     * @return 无内存句柄（跨节点登记）或不存在返回null
     */
    public ConfirmResumeHandle pop(String sessionId) {
        PendingResumeEntry entry = popEntry(sessionId);
        return entry != null ? entry.getConfirmHandle() : null;
    }

    /**
     * 检查登记是否存在
     * @param sessionId
     * @return
     */
    public boolean exists(String sessionId) {
        return pendingResumeStore.exists(requestId(sessionId));
    }

    /**
     * 构建确认登记（携带内存句柄与跨节点重建所需的序列化数据）
     * @param handle
     * @return
     */
    private PendingResumeEntry buildEntry(ConfirmResumeHandle handle) {
        PendingResumeEntry entry = new PendingResumeEntry();
        entry.setRequestId(requestId(handle.getSessionId()));
        entry.setSessionId(handle.getSessionId());
        entry.setResumeType("CONFIRM");
        entry.setExpireTime(Instant.now().plus(HANDLE_TTL));
        entry.setCreatedAt(Instant.now());
        entry.setConfirmHandle(handle);
        AgentRequest request = handle.getAgentContext() != null ? handle.getAgentContext().getRequest() : null;
        if (request != null) {
            entry.setAgentCode(request.getAgentCode());
            entry.setUserId(request.getUserId());
            entry.setScopeId(request.getScopeId());
            entry.setRequestData(serializeRequest(request));
        }
        entry.setPendingData(serializePendingTools(handle.getPendingToolCalls()));
        entry.setRunId(resolveRunId(handle));
        return entry;
    }

    /**
     * 序列化暂停时请求（跨节点重建上下文用）
     * @param request
     * @return
     */
    private String serializeRequest(AgentRequest request) {
        try {
            return OBJECT_MAPPER.writeValueAsString(request);
        } catch (Exception e) {
            log.warn("确认登记请求序列化失败: sessionId={}", request.getSessionId(), e);
            return null;
        }
    }

    /**
     * 序列化待确认工具清单（toolUseId/toolName最小集，跨节点构建确认结果用）
     * @param pendingToolCalls
     * @return
     */
    private String serializePendingTools(List<com.yangqiongai.ai.agent.runtime.message.AgentToolUseBlock> pendingToolCalls) {
        try {
            List<Map<String, Object>> items = pendingToolCalls.stream()
                    .map(call -> Map.<String, Object>of(
                            "toolUseId", call.getToolUseId() == null ? "" : call.getToolUseId(),
                            "toolName", call.getToolName() == null ? "" : call.getToolName()))
                    .toList();
            return OBJECT_MAPPER.writeValueAsString(items);
        } catch (Exception e) {
            log.warn("确认登记待确认工具序列化失败", e);
            return null;
        }
    }

    /**
     * 从运行时上下文解析引擎运行ID
     * @param handle
     * @return
     */
    private String resolveRunId(ConfirmResumeHandle handle) {
        try {
            Object runId = handle.getRuntimeContext() != null
                    ? handle.getRuntimeContext().get(ATTR_RUN_ID) : null;
            return runId instanceof String s && !s.isBlank() ? s : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 构建确认登记请求标识
     * @param sessionId
     * @return
     */
    static String requestId(String sessionId) {
        return "confirm:" + sessionId;
    }
}
