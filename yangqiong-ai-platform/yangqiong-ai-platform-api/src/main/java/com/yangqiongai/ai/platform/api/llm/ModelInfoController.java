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
package com.yangqiongai.ai.platform.api.llm;

import com.yangqiongai.ai.data.llm.entity.ModelInfo;
import com.yangqiongai.ai.data.llm.ModelInfoManager;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.util.SecretCipherUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型信息管理
 * @author yangqiong
 */
@Tag(name = "模型信息管理接口")
@RestController
@RequestMapping("/api/model/info")
public class ModelInfoController {

    @Autowired
    private ModelInfoManager modelInfoManager;

    @Autowired
    private ModelConnectivityTester modelConnectivityTester;

    /**
     * 创建模型
     * @param modelInfo
     * @return
     */
    @Operation(summary = "创建模型")
    @PostMapping
    public ApiResult<ModelInfo> create(
            @Parameter(name = "modelInfo", description = "模型信息") @RequestBody ModelInfo modelInfo) {
        return ApiResult.ok(masked(modelInfoManager.create(modelInfo)));
    }

    /**
     * 查询所有模型
     * @return
     */
    @Operation(summary = "查询所有模型")
    @GetMapping
    public ApiResult<List<ModelInfo>> list() {
        return ApiResult.ok(modelInfoManager.findAll().stream()
                .map(this::masked)
                .collect(Collectors.toList()));
    }

    /**
     * 根据模型编码查询
     * @param modelCode
     * @return
     */
    @Operation(summary = "根据模型编码查询")
    @GetMapping("/{modelCode}")
    public ApiResult<ModelInfo> getByModelCode(
            @Parameter(name = "modelCode", description = "模型编码") @PathVariable String modelCode) {
        return modelInfoManager.findByModelCode(modelCode)
                .map(this::masked)
                .map(ApiResult::ok)
                .orElse(ApiResult.fail(AiErrorCode.MODEL_NOT_FOUND.getCode(), "模型不存在: " + modelCode));
    }

    /**
     * 更新模型
     * @param modelInfo
     */
    @Operation(summary = "更新模型")
    @PutMapping
    public ApiResult<Void> update(
            @Parameter(name = "modelInfo", description = "模型信息") @RequestBody ModelInfo modelInfo) {
        // 掩码或空密钥表示未修改，保留库中原密钥
        if (SecretCipherUtil.isMasked(modelInfo.getApiKey()) || !StringUtils.hasText(modelInfo.getApiKey())) {
            modelInfo.setApiKey(null);
        }
        modelInfoManager.update(modelInfo);
        return ApiResult.ok();
    }

    /**
     * 测试模型连通性（支持未保存的表单配置）
     * @param modelInfo
     * @return
     */
    @Operation(summary = "测试模型连通性")
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test(
            @Parameter(name = "modelInfo", description = "模型信息") @RequestBody ModelInfo modelInfo) {
        // 掩码或空密钥回退库中真实值后测试，避免把掩码当真实密钥请求
        if (SecretCipherUtil.isMasked(modelInfo.getApiKey()) || !StringUtils.hasText(modelInfo.getApiKey())) {
            modelInfoManager.findByModelCode(modelInfo.getModelCode())
                    .ifPresent(saved -> modelInfo.setApiKey(saved.getApiKey()));
        }
        return ApiResult.ok(modelConnectivityTester.test(modelInfo));
    }

    /**
     * 删除模型
     * @param modelCode
     */
    @Operation(summary = "删除模型")
    @DeleteMapping("/{modelCode}")
    public ApiResult<Void> delete(
            @Parameter(name = "modelCode", description = "模型编码") @PathVariable String modelCode) {
        modelInfoManager.delete(modelCode);
        return ApiResult.ok();
    }

    /**
     * 构建脱敏副本，不回传明文密钥且不改动库中对象
     * @param modelInfo
     * @return
     */
    private ModelInfo masked(ModelInfo modelInfo) {
        if (modelInfo == null) {
            return null;
        }
        ModelInfo view = new ModelInfo();
        view.setId(modelInfo.getId());
        view.setModelCode(modelInfo.getModelCode());
        view.setModelName(modelInfo.getModelName());
        view.setProvider(modelInfo.getProvider());
        view.setModelType(modelInfo.getModelType());
        view.setApiEndpoint(modelInfo.getApiEndpoint());
        view.setApiKey(SecretCipherUtil.mask(modelInfo.getApiKey()));
        view.setModelConfig(modelInfo.getModelConfig());
        view.setIsDefault(modelInfo.getIsDefault());
        view.setModelStatus(modelInfo.getModelStatus());
        view.setRemark(modelInfo.getRemark());
        view.setSupportReasoning(modelInfo.getSupportReasoning());
        view.setSupportImage(modelInfo.getSupportImage());
        view.setScopeId(modelInfo.getScopeId());
        return view;
    }
}
