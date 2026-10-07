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

/**
 * 工作流执行监听
 * <p>
 * 供企业版挂载执行审计等扩展能力，社区引擎在执行关键节点回调。
 * 未注入实现时社区引擎不回调事件，默认降级不报错。
 * </p>
 * @author yangqiong
 */
public interface WorkflowExecutionListener {

    /**
     * 工作流开始执行
     * @param event
     */
    default void onWorkflowStart(WorkflowExecutionEvent event) {
    }

    /**
     * 工作流执行完成（含取消终态）
     * @param event
     */
    default void onWorkflowComplete(WorkflowExecutionEvent event) {
    }

    /**
     * 工作流执行失败
     * @param event
     */
    default void onWorkflowFailed(WorkflowExecutionEvent event) {
    }

    /**
     * 工作流暂停等待审批
     * @param event
     */
    default void onWorkflowPaused(WorkflowExecutionEvent event) {
    }

    /**
     * 节点开始执行
     * @param event
     */
    default void onNodeStart(WorkflowExecutionEvent event) {
    }

    /**
     * 节点执行完成
     * @param event
     */
    default void onNodeComplete(WorkflowExecutionEvent event) {
    }

    /**
     * 节点执行失败
     * @param event
     */
    default void onNodeFailed(WorkflowExecutionEvent event) {
    }
}
