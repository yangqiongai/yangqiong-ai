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
package com.yangqiongai.ai.workflow.spi;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.approval.ApprovalRequest;
import com.yangqiongai.ai.approval.ApprovalResolvedEvent;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.workflow.model.WorkflowNode;
import com.yangqiongai.ai.workflow.model.WorkflowState;

/**
 * 工作流审批拦截
 * <p>
 * 供企业版挂载多级审批、审批委托与SLA管控等扩展能力。
 * 未注入实现时社区引擎按单级审批原逻辑执行，默认降级不报错。
 * </p>
 * @author yangqiong
 */
public interface WorkflowApprovalInterceptor {

    /**
     * 创建审批请求前回调，写入级次、委托、SLA等企业元数据
     * @param request 审批请求（不可变，可通过getParams写入元数据或重建返回）
     * @param state 工作流状态
     * @param node 审批节点
     * @param context 执行上下文
     * @return 实际提交的审批请求，返回null时取消本次审批发起
     */
    default ApprovalRequest enrich(ApprovalRequest request, WorkflowState state, WorkflowNode node,
                                   AgentContext context) {
        return request;
    }

    /**
     * 审批完成后恢复执行前回调
     * @param definition 工作流定义
     * @param context 执行上下文
     * @param state 工作流状态
     * @param node 暂停的审批节点
     * @param event 审批完成事件
     * @return true表示企业侧已接管本次恢复（如仍有下一级审批并已重新发起），社区引擎直接返回暂停结果
     */
    default boolean beforeResume(WorkflowDefinition definition, AgentContext context, WorkflowState state,
                                 WorkflowNode node, ApprovalResolvedEvent event) {
        return false;
    }
}
