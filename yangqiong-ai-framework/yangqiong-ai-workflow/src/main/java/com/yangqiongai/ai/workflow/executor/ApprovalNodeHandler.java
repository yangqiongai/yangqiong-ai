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

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.approval.ApprovalGate;
import com.yangqiongai.ai.approval.ApprovalRequest;
import com.yangqiongai.ai.workflow.model.NodeApprovalConfig;
import com.yangqiongai.ai.workflow.model.NodeType;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import com.yangqiongai.ai.workflow.spi.WorkflowApprovalInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 审批处理
 * @author yangqiong
 */
@Service
public class ApprovalNodeHandler {

    private static final Logger log = LoggerFactory.getLogger(ApprovalNodeHandler.class);

    private final ObjectProvider<ApprovalGate> approvalGateProvider;

    private final ObjectProvider<WorkflowApprovalInterceptor> approvalInterceptorProvider;

    public ApprovalNodeHandler(ObjectProvider<ApprovalGate> approvalGateProvider,
                               ObjectProvider<WorkflowApprovalInterceptor> approvalInterceptorProvider) {
        this.approvalGateProvider = approvalGateProvider;
        this.approvalInterceptorProvider = approvalInterceptorProvider;
    }

    /**
     * 节点前置审批检查，非APPROVAL节点且配置了approvalConfig时触发（异步模式）
     * @param context
     * @param state
     * @param node
     * @return 审批通过返回null，需暂停审批返回paused结果
     */
    public AgentResult checkNodeApproval(AgentContext context, WorkflowState state, WorkflowNode node) {
        if (node.getType() == NodeType.APPROVAL) {
            return null;
        }
        NodeApprovalConfig config = node.getApprovalConfig();
        if (config == null) {
            return null;
        }
        // 恢复场景：审批已通过则跳过
        if (Boolean.TRUE.equals(state.getVariable("approvalPassed:" + node.getId()))) {
            log.info("节点审批已通过（恢复场景），跳过: nodeId={}", node.getId());
            return null;
        }
        String requestId = createNodeApproval(context, state, node, config);
        if (requestId == null) {
            log.warn("ApprovalGate不可用，跳过节点审批: nodeId={}", node.getId());
            return null;
        }
        log.info("节点前置审批（异步）: nodeId={}, reason={}", node.getId(), config.getReason());
        state.setVariable("approvalRequestId:" + node.getId(), requestId);
        return AgentResult.paused(requestId, "节点前置审批已暂停，等待审批: " + node.getName());
    }

    /**
     * 执行APPROVAL节点（异步模式），发起审批请求并返回暂停结果
     * @param context
     * @param state
     * @param node
     * @return
     */
    public AgentResult executeApprovalNode(AgentContext context, WorkflowState state, WorkflowNode node) {
        NodeApprovalConfig config = node.getApprovalConfig();
        if (config == null) {
            log.warn("APPROVAL节点未配置approvalConfig，直接通过: nodeId={}", node.getId());
            return AgentResult.success("审批节点未配置，自动通过");
        }
        String requestId = createNodeApproval(context, state, node, config);
        if (requestId == null) {
            log.warn("ApprovalGate不可用，APPROVAL节点自动通过: nodeId={}", node.getId());
            return AgentResult.success("审批模块不可用，自动通过");
        }
        log.info("执行审批节点（异步）: nodeId={}, reason={}", node.getId(), config.getReason());
        state.setVariable("approvalRequestId:" + node.getId(), requestId);
        return AgentResult.paused(requestId, "审批节点已暂停，等待审批: " + node.getName());
    }

    /**
     * 异步创建节点审批请求，不阻塞线程，先经企业审批拦截加工企业元数据
     * @param context
     * @param state
     * @param node
     * @param config
     * @return 审批请求ID，ApprovalGate不可用或拦截取消时返回null
     */
    public String createNodeApproval(AgentContext context, WorkflowState state, WorkflowNode node,
                                     NodeApprovalConfig config) {
        ApprovalGate gate = approvalGateProvider.getIfAvailable();
        if (gate == null) {
            return null;
        }
        String sessionId = context.getRequest() != null ? context.getRequest().getSessionId() : null;
        String userId = context.getRequest() != null ? context.getRequest().getUserId() : null;
        ApprovalRequest request = ApprovalRequest.builder()
                .sessionId(sessionId != null ? sessionId : "workflow-" + UUID.randomUUID())
                .userId(userId != null ? userId : "system")
                .resourceType("WORKFLOW_NODE")
                .targetName(node.getName() != null ? node.getName() : node.getId())
                .reason(config.getReason() != null ? config.getReason() : "工作流节点审批")
                .options(config.getOptions())
                .inputFields(config.getInputFields())
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds() > 0 ? config.getTimeoutSeconds() : 300))
                .build();
        // 企业审批拦截：写入级次、委托、SLA等企业元数据，返回null时取消本次审批发起
        WorkflowApprovalInterceptor interceptor = approvalInterceptorProvider.getIfAvailable();
        if (interceptor != null) {
            try {
                request = interceptor.enrich(request, state, node, context);
            } catch (Exception e) {
                log.warn("企业审批拦截enrich异常，按社区逻辑继续: {}", e.getMessage());
            }
            if (request == null) {
                log.info("企业审批拦截取消本次审批发起: nodeId={}", node.getId());
                return null;
            }
        }
        return gate.createPendingRequest(request);
    }
}
