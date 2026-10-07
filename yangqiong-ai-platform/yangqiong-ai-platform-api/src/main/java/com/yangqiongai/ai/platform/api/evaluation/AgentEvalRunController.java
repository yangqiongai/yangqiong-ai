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
package com.yangqiongai.ai.platform.api.evaluation;

import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 评测运行接口
 * @author yangqiong
 */
@Tag(name = "评测运行接口")
@RestController
@RequestMapping("/api/agent/evaluation/runs")
@ConditionalOnProperty(prefix = "ai.agent.evaluation", name = "enabled", havingValue = "true")
public class AgentEvalRunController {

    @Autowired
    private EvaluationRunService evaluationRunService;

    @Autowired
    private EvaluationReportService evaluationReportService;

    @Autowired
    private EvalRunRepository evalRunRepository;

    /**
     * 触发评测(异步执行)
     * @param request
     * @return
     */
    @Operation(summary = "触发评测", description = "创建RUNNING运行后异步执行，返回runId供轮询终态")
    @PostMapping
    public ApiResult<Map<String, Object>> trigger(@RequestBody RunTriggerRequest request) {
        if (request.getDatasetId() == null) {
            return ApiResult.fail("数据集ID不能为空");
        }
        try {
            EvalRunEntity run = evaluationRunService.triggerRun(
                    request.getDatasetId(), request.getAgentCode(), request.getThreshold());
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("runId", run.getId());
            result.put("status", run.getStatus());
            return ApiResult.ok(result);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 运行分页
     * @param agentCode Agent编码(可空)
     * @param datasetCode 数据集编码(可空)
     * @param status 状态(可空)
     * @param pageNum 页码(从1开始)
     * @param pageSize 每页条数
     * @return
     */
    @Operation(summary = "运行分页")
    @GetMapping
    public ApiResult<List<EvalRunEntity>> list(
            @Parameter(name = "agentCode", description = "Agent编码") @RequestParam(required = false) String agentCode,
            @Parameter(name = "datasetCode", description = "数据集编码") @RequestParam(required = false) String datasetCode,
            @Parameter(name = "status", description = "状态(RUNNING/PASSED/FAILED/ERROR/CANCELLED)") @RequestParam(required = false) String status,
            @Parameter(name = "pageNum", description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(1, pageSize), 100);
        int offset = (Math.max(1, pageNum) - 1) * size;
        List<EvalRunEntity> rows = evalRunRepository.findRuns(
                agentCode, datasetCode, status, null, null, offset, size);
        long total = evalRunRepository.countRuns(agentCode, datasetCode, status, null, null);
        return ApiResult.okPage(rows, total, pageNum, size);
    }

    /**
     * 运行详情(轮询终态入口)
     * @param id
     * @param includeReport 是否返回report_json完整快照
     * @return
     */
    @Operation(summary = "运行详情", description = "前端轮询直至终态(PASSED/FAILED/ERROR/CANCELLED)")
    @GetMapping("/{id}")
    public ApiResult<EvalRunEntity> detail(
            @Parameter(name = "id", description = "运行ID") @PathVariable Long id,
            @Parameter(name = "includeReport", description = "是否返回报告完整快照") @RequestParam(defaultValue = "false") boolean includeReport) {
        EvalRunEntity run = evalRunRepository.findRunById(id);
        if (run == null) {
            return ApiResult.fail(com.yangqiongai.ai.common.exception.AiErrorCode.NOT_FOUND.getCode(),
                    "评测运行不存在: " + id);
        }
        if (!includeReport) {
            run.setReportJson(null);
        }
        return ApiResult.ok(run);
    }

    /**
     * 运行用例明细
     * @param id
     * @param pageNum 页码(从1开始)
     * @param pageSize 每页条数
     * @return
     */
    @Operation(summary = "运行用例明细")
    @GetMapping("/{id}/cases")
    public ApiResult<List<EvalRunCaseEntity>> cases(
            @Parameter(name = "id", description = "运行ID") @PathVariable Long id,
            @Parameter(name = "pageNum", description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        EvalRunEntity run = evalRunRepository.findRunById(id);
        if (run == null) {
            return ApiResult.fail(com.yangqiongai.ai.common.exception.AiErrorCode.NOT_FOUND.getCode(),
                    "评测运行不存在: " + id);
        }
        List<EvalRunCaseEntity> all = evalRunRepository.findRunCases(id);
        int size = Math.min(Math.max(1, pageSize), 100);
        int from = Math.min(Math.max(0, (Math.max(1, pageNum) - 1) * size), all.size());
        int to = Math.min(from + size, all.size());
        return ApiResult.okPage(all.subList(from, to), all.size(), pageNum, size);
    }

    /**
     * 取消运行
     * @param id
     * @return
     */
    @Operation(summary = "取消运行", description = "仅RUNNING状态可取消，置CANCELLED并丢弃结果")
    @PostMapping("/{id}/cancel")
    public ApiResult<Void> cancel(@Parameter(name = "id", description = "运行ID") @PathVariable Long id) {
        try {
            evaluationRunService.cancelRun(id);
            return ApiResult.ok();
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }

    /**
     * 两次运行对比
     * @param leftId
     * @param rightId
     * @return
     */
    @Operation(summary = "运行对比", description = "整体指标diff+按case_no对齐的用例级矩阵")
    @GetMapping("/compare")
    public ApiResult<Map<String, Object>> compare(
            @Parameter(name = "leftId", description = "左侧运行ID") @RequestParam Long leftId,
            @Parameter(name = "rightId", description = "右侧运行ID") @RequestParam Long rightId) {
        try {
            return ApiResult.ok(evaluationReportService.compare(leftId, rightId));
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
    }
}
