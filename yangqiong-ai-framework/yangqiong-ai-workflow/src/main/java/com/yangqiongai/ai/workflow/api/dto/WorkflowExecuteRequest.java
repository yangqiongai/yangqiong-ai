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

import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * 工作流执行请求（合并定义和执行参数）
 * @author yangqiong
 */
@Data
public class WorkflowExecuteRequest {

    /**
     * 工作流定义（传入完整定义时使用，与definitionName二选一）
     */
    private WorkflowDefinition definition;

    /**
     * 工作流定义名称（按名称执行时使用，与definition二选一）
     */
    private String definitionName;

    /**
     * 指定版本号
     */
    private Integer version;

    /**
     * 用户ID
     */
    @Size(max = 64, message = "userId长度不能超过64")
    private String userId;

    /**
     * 会话ID
     */
    @Size(max = 128, message = "sessionId长度不能超过128")
    private String sessionId;

    /**
     * 用户输入
     */
    @Size(max = 10000, message = "input长度不能超过10000")
    private String input;

    /**
     * 额外参数
     */
    private Map<String, Object> params;

    /**
     * 初始变量（注入到工作流状态中，供条件表达式等节点使用）
     */
    private Map<String, Object> initialVariables;

    /**
     * 暂停原因（工作流暂停时记录，用户输入）
     */
    @Size(max = 500, message = "pauseReason长度不能超过500")
    private String pauseReason;
}
