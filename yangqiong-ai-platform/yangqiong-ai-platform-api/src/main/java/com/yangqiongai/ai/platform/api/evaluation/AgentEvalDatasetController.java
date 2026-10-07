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

import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 评测数据集管理接口
 * @author yangqiong
 */
@Tag(name = "评测数据集管理接口")
@RestController
@RequestMapping("/api/agent/evaluation/datasets")
@ConditionalOnProperty(prefix = "ai.agent.evaluation", name = "enabled", havingValue = "true")
public class AgentEvalDatasetController {

    @Autowired
    private EvalDatasetRepository evalDatasetRepository;

    @Autowired
    private EvalRunRepository evalRunRepository;

    /**
     * 数据集分页
     * @param keyword 名称/编码模糊搜索(可空)
     * @param status 状态(可空)
     * @param pageNum 页码(从1开始)
     * @param pageSize 每页条数
     * @return
     */
    @Operation(summary = "数据集分页")
    @GetMapping
    public ApiResult<List<EvalDatasetEntity>> list(
            @Parameter(name = "keyword", description = "名称/编码模糊搜索") @RequestParam(required = false) String keyword,
            @Parameter(name = "status", description = "状态(DRAFT/ENABLED/DISABLED)") @RequestParam(required = false) String status,
            @Parameter(name = "pageNum", description = "页码") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(name = "pageSize", description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        int size = Math.min(Math.max(1, pageSize), 100);
        int offset = (Math.max(1, pageNum) - 1) * size;
        List<EvalDatasetEntity> rows = evalDatasetRepository.findDatasets(keyword, status, offset, size);
        long total = evalDatasetRepository.countDatasets(keyword, status);
        return ApiResult.okPage(rows, total, pageNum, size);
    }

    /**
     * 创建数据集(可带用例批量导入)
     * @param request
     * @return
     */
    @Operation(summary = "创建数据集", description = "编码按scope唯一；可携带cases批量导入")
    @PostMapping
    public ApiResult<EvalDatasetEntity> create(@RequestBody DatasetSaveRequest request) {
        String validationError = validate(request);
        if (validationError != null) {
            return ApiResult.fail(validationError);
        }
        if (evalDatasetRepository.findDatasetByCode(request.getDatasetCode()) != null) {
            return ApiResult.fail("数据集编码已存在: " + request.getDatasetCode());
        }
        EvalDatasetEntity dataset = new EvalDatasetEntity();
        dataset.setDatasetCode(request.getDatasetCode());
        dataset.setName(request.getName());
        dataset.setDescription(request.getDescription());
        dataset.setStatus(defaultStatus(request.getStatus()));
        evalDatasetRepository.saveDataset(dataset);
        if (request.getCases() != null && !request.getCases().isEmpty()) {
            evalDatasetRepository.replaceCases(dataset.getId(), request.getCases());
        }
        return ApiResult.ok(evalDatasetRepository.findDatasetById(dataset.getId()));
    }

    /**
     * 更新数据集(cases非空时全量替换用例)
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "更新数据集", description = "编码不可变；cases非空时全量替换用例并刷新冗余数")
    @PutMapping("/{id}")
    public ApiResult<EvalDatasetEntity> update(
            @Parameter(name = "id", description = "数据集ID") @PathVariable Long id,
            @RequestBody DatasetSaveRequest request) {
        EvalDatasetEntity dataset = evalDatasetRepository.findDatasetById(id);
        if (dataset == null) {
            return ApiResult.fail(com.yangqiongai.ai.common.exception.AiErrorCode.NOT_FOUND.getCode(),
                    "数据集不存在: " + id);
        }
        if (request.getName() != null) {
            dataset.setName(request.getName());
        }
        if (request.getDescription() != null) {
            dataset.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            dataset.setStatus(request.getStatus());
        }
        evalDatasetRepository.updateDataset(dataset);
        if (request.getCases() != null) {
            evalDatasetRepository.replaceCases(id, request.getCases());
        }
        return ApiResult.ok(evalDatasetRepository.findDatasetById(id));
    }

    /**
     * 删除数据集(有关联运行时拒绝)
     * @param id
     * @return
     */
    @Operation(summary = "删除数据集", description = "级联删除用例；存在关联评测运行时拒绝删除")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@Parameter(name = "id", description = "数据集ID") @PathVariable Long id) {
        if (evalDatasetRepository.findDatasetById(id) == null) {
            return ApiResult.fail(com.yangqiongai.ai.common.exception.AiErrorCode.NOT_FOUND.getCode(),
                    "数据集不存在: " + id);
        }
        if (evalRunRepository.countRunsByDatasetId(id) > 0) {
            return ApiResult.fail("数据集存在关联评测运行，禁止删除");
        }
        evalDatasetRepository.deleteDataset(id);
        return ApiResult.ok();
    }

    /**
     * 数据集用例列表
     * @param id
     * @return
     */
    @Operation(summary = "数据集用例列表")
    @GetMapping("/{id}/cases")
    public ApiResult<List<EvalDatasetCaseEntity>> cases(
            @Parameter(name = "id", description = "数据集ID") @PathVariable Long id) {
        if (evalDatasetRepository.findDatasetById(id) == null) {
            return ApiResult.fail(com.yangqiongai.ai.common.exception.AiErrorCode.NOT_FOUND.getCode(),
                    "数据集不存在: " + id);
        }
        return ApiResult.ok(evalDatasetRepository.findCases(id));
    }

    /**
     * 保存请求校验
     * @param request
     * @return
     */
    private String validate(DatasetSaveRequest request) {
        if (request.getDatasetCode() == null || request.getDatasetCode().isBlank()) {
            return "数据集编码不能为空";
        }
        if (request.getDatasetCode().length() > 64) {
            return "数据集编码长度不能超过64";
        }
        if (request.getName() == null || request.getName().isBlank()) {
            return "数据集名称不能为空";
        }
        if (request.getCases() != null) {
            for (EvalDatasetCaseEntity caseEntity : request.getCases()) {
                if (caseEntity.getCaseNo() == null || caseEntity.getCaseNo().isBlank()) {
                    return "用例编号不能为空";
                }
                if (caseEntity.getQueryText() == null || caseEntity.getQueryText().isBlank()) {
                    return "用例[" + caseEntity.getCaseNo() + "]用户输入不能为空";
                }
            }
        }
        return null;
    }

    /**
     * 状态默认值
     * @param status
     * @return
     */
    private String defaultStatus(String status) {
        return status == null || status.isBlank() ? "ENABLED" : status;
    }
}
