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
package com.yangqiongai.ai.workflow.model;

import lombok.Data;

import java.util.List;

/**
 * 节点审批配置
 * <p>
 * 可配置在任意节点上，节点执行前触发审批；也可作为APPROVAL节点的主配置。
 * </p>
 *
 * @author yangqiong
 */
@Data
public class NodeApprovalConfig {

    /**
     * 审批原因说明，展示给审批人
     */
    private String reason;

    /**
     * 供审批人选择的选项列表，非空时前端渲染为选择对话框
     */
    private List<String> options;

    /**
     * 供审批人填写的字段名列表，非空时前端渲染为表单
     */
    private List<String> inputFields;

    /**
     * 等待审批超时时间（秒），默认5分钟
     */
    private int timeoutSeconds = 300;

    /**
     * 审批拒绝后的行为：FAIL（默认，节点失败）、SKIP（跳过节点）、RETRY（重试节点）
     */
    private RejectBehavior rejectBehavior = RejectBehavior.FAIL;

    /**
     * 审批拒绝行为
     */
    public enum RejectBehavior {
        /**
         * 节点标记为FAILED，工作流停止
         */
        FAIL,

        /**
         * 跳过节点，继续执行后续节点
         */
        SKIP,

        /**
         * 重试节点（配合retryCount使用）
         */
        RETRY
    }
}
