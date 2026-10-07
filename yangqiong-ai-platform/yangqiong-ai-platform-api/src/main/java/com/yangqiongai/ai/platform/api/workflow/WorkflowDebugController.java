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
package com.yangqiongai.ai.platform.api.workflow;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.workflow.WorkflowEngine;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteRequest;
import com.yangqiongai.ai.workflow.api.dto.WorkflowExecuteResult;
import com.yangqiongai.ai.workflow.model.WorkflowDefinition;
import com.yangqiongai.ai.platform.bss.security.annotation.IgnoreSecurityCheckEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 工作流单节点调试接口
 * @author yangqiong
 */
@Tag(name = "工作流单节点调试接口")
@RestController
@RequestMapping("/api/workflow/debug")
@IgnoreSecurityCheckEntity
public class WorkflowDebugController {

    private static final String CAPABILITY_WORKFLOW = "WORKFLOW";

    @Autowired
    private WorkflowEngine workflowEngine;

    @Autowired
    private WorkflowControllerSupport support;

    @Autowired
    private FeatureGuard featureGuard;

    /**
     * 工作流节点调试请求
     */
    @Data
    public static class WorkflowNodeDebugRequest {

        /**
         * 工作流定义（即时调试，不落定义表）
         */
        private WorkflowDefinition definition;

        /**
         * 调试目标节点ID
         */
        private String nodeId;

        /**
         * Mock变量
         */
        private Map<String, Object> mockVariables;

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 会话ID
         */
        private String sessionId;
    }

    /**
     * 单节点调试执行（不产生实例、不落历史）
     * @param request
     * @return
     */
    @Operation(summary = "单节点调试执行")
    @PostMapping("/node")
    public ApiResult<WorkflowExecuteResult> debugNode(@RequestBody WorkflowNodeDebugRequest request) {
        featureGuard.checkFeature(CAPABILITY_WORKFLOW);
        if (request.getDefinition() == null) {
            return ApiResult.fail("必须提供工作流定义definition");
        }
        if (request.getNodeId() == null || request.getNodeId().isBlank()) {
            return ApiResult.fail("必须提供调试节点ID nodeId");
        }
        AgentContext context = support.createAgentContext(request.getDefinition().getName(), toExecuteRequest(request));
        return ApiResult.ok(workflowEngine.debugNode(request.getDefinition(), request.getNodeId(),
                request.getMockVariables(), context));
    }

    /**
     * 调试请求转通用执行请求（复用上下文构建）
     * @param request
     * @return
     */
    private WorkflowExecuteRequest toExecuteRequest(WorkflowNodeDebugRequest request) {
        WorkflowExecuteRequest executeRequest = new WorkflowExecuteRequest();
        executeRequest.setUserId(request.getUserId());
        executeRequest.setSessionId(request.getSessionId());
        return executeRequest;
    }
}
