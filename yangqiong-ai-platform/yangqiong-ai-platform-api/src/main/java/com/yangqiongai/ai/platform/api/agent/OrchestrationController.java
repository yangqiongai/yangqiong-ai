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

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.orchestration.OrchestrationDecisionService;
import com.yangqiongai.ai.agent.core.orchestration.SubagentDeclaration;
import com.yangqiongai.ai.agent.core.orchestration.SubagentSpecGeneratorService;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.platform.bss.security.annotation.IgnoreSecurityCheckEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 多代理编排方案
 * @author yangqiong
 */
@Tag(name = "多代理编排接口")
@RestController
@RequestMapping("/api/agent/orchestration")
@IgnoreSecurityCheckEntity
public class OrchestrationController {

    @Autowired
    private OrchestrationDecisionService orchestrationDecisionService;

    @Autowired
    private SubagentSpecGeneratorService subagentSpecGeneratorService;

    /**
     * 预览编排方案
     * @param request
     * @return
     */
    @Operation(summary = "预览编排方案", description = "LLM 分析任务并生成编排模式与子代理声明")
    @PostMapping("/plan")
    public ApiResult<Map<String, Object>> plan(
            @Parameter(name = "request", description = "预览请求，body中可指定maxSubagents和generationModelCode")
            @RequestBody AgentRequest request) {
        String task = request.getInputAsText();
        if (task == null || task.isBlank()) {
            return ApiResult.fail(AiErrorCode.PARAM_ERROR.getCode(), "任务描述不能为空");
        }
        Map<String, Object> body = request.getBody() != null ? request.getBody() : new HashMap<>();
        int maxSubagents = body.get(AgentRequest.BodyKeys.MAX_SUBAGENTS) instanceof Number n
                ? n.intValue() : 5;
        String genModelCode = body.get(AgentRequest.BodyKeys.GENERATION_MODEL_CODE) instanceof String s
                ? s : null;

        // 决策编排模式
        String mode = orchestrationDecisionService
                .decideMode(task, genModelCode)
                .block(Duration.ofSeconds(60));
        if (mode == null || mode.isBlank()) {
            mode = "delegate";
        }

        // 生成子代理声明
        List<SubagentDeclaration> subagents = subagentSpecGeneratorService
                .generateDeclarations(task, maxSubagents, Collections.emptyList(), genModelCode)
                .block(Duration.ofSeconds(120));
        if (subagents == null) {
            subagents = Collections.emptyList();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", mode);
        result.put("subagents", subagents);
        return ApiResult.ok(result);
    }
}
