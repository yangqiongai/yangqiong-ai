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
package com.yangqiongai.ai.workflow.api.dto;

import com.yangqiongai.ai.agent.core.model.content.OutputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextOutputBlock;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 工作流执行结果（结构化返回，替代裸AgentResult）
 * @author yangqiong
 */
@Data
public class WorkflowExecuteResult {

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 工作流实例ID
     */
    private String instanceId;

    /**
     * 工作流定义名称
     */
    private String definitionName;

    /**
     * 执行状态
     */
    private ExecutionStatus status;

    /**
     * 最终输出（多模态内容块列表，END前驱节点的输出汇总）
     */
    private List<OutputBlock> output;

    /**
     * 错误信息（失败时有值）
     */
    private String errorMessage;

    /**
     * 工作流变量（过滤掉内部变量后的业务变量）
     */
    private Map<String, Object> variables;

    /**
     * 节点执行摘要列表
     */
    private List<NodeSummary> nodeSummaries;

    /**
     * 总执行耗时（毫秒）
     */
    private Long totalDurationMs;

    /**
     * 是否暂停（等待审批）
     */
    private boolean paused;

    /**
     * 暂停时关联的审批请求ID
     */
    private String pendingRequestId;

    /**
     * 从多模态输出中抽取纯文本（拼接所有 TextBlock，以 \n 连接）
     * @return
     */
    public String getOutputAsText() {
        if (output == null || output.isEmpty()) {
            return "";
        }
        return output.stream()
                .filter(TextOutputBlock.class::isInstance)
                .map(TextOutputBlock.class::cast)
                .map(TextOutputBlock::getText)
                .filter(t -> t != null && !t.isBlank())
                .collect(Collectors.joining("\n"));
    }

    /**
     * 节点执行摘要
     */
    @Data
    public static class NodeSummary {

        /**
         * 节点ID
         */
        private String nodeId;

        /**
         * 节点名称
         */
        private String nodeName;

        /**
         * 节点类型
         */
        private String nodeType;

        /**
         * 执行状态
         */
        private ExecutionStatus status;

        /**
         * 节点输入（结构化）
         */
        private Map<String, Object> input;

        /**
         * 节点输出（结构化）
         */
        private Map<String, Object> output;

        /**
         * 执行耗时（毫秒）
         */
        private Long durationMs;

        /**
         * 错误信息
         */
        private String errorMessage;

        /**
         * 重试次数
         */
        private Integer retryCount;

        /**
         * 循环迭代次数
         */
        private Integer iterationCount;
    }
}
