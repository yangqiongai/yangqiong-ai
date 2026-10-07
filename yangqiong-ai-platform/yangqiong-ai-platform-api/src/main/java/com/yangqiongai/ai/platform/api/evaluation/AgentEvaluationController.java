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

import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import com.yangqiongai.ai.evaluation.EvaluationRunner;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * Agent评测管理
 * @author yangqiong
 */
@Tag(name = "Agent评测管理接口")
@RestController
@RequestMapping("/api/agent/evaluation")
public class AgentEvaluationController {

    @Autowired
    private EvaluationRunner evaluationRunner;

    /**
     * 从文件加载评测数据集并执行评测
     * @param filePath
     * @return
     */
    @Operation(summary = "从文件加载评测数据集并执行评测")
    @PostMapping("/evaluate/file")
    public ApiResult<EvaluationReport> evaluateFromFile(
            @Parameter(name = "filePath", description = "评测数据集文件路径，支持classpath:前缀") @RequestParam String filePath) {
        try {
            validateFilePath(filePath);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
        return ApiResult.ok(evaluationRunner.evaluateFromFile(filePath));
    }

    /**
     * 校验文件路径，防止路径遍历攻击
     * @param filePath
     */
    private void validateFilePath(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("文件路径不能为空");
        }
        // 禁止路径遍历
        if (filePath.contains("..")) {
            throw new IllegalArgumentException("文件路径包含非法字符: ..");
        }
        // 禁止绝对路径（Linux以/开头，Windows以盘符开头如C:）
        if (filePath.startsWith("/") || filePath.matches("^[a-zA-Z]:.*")) {
            throw new IllegalArgumentException("不允许使用绝对路径");
        }
    }

    /**
     * 从JSON字符串加载评测数据集并执行评测
     * @param json
     * @return
     */
    @Operation(summary = "从JSON字符串加载评测数据集并执行评测")
    @PostMapping("/evaluate/json")
    public ApiResult<EvaluationReport> evaluateFromJson(
            @Parameter(name = "json", description = "评测数据集JSON字符串") @RequestBody String json) {
        return ApiResult.ok(evaluationRunner.evaluateFromJson(json));
    }

    /**
     * 直接传入评测数据集执行评测
     * @param dataset
     * @return
     */
    @Operation(summary = "直接传入评测数据集执行评测")
    @PostMapping("/evaluate")
    public ApiResult<EvaluationReport> evaluate(
            @Parameter(name = "dataset", description = "评测数据集对象") @RequestBody GoldenDataset dataset) {
        return ApiResult.ok(evaluationRunner.evaluate(dataset));
    }
}
