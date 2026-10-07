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
package com.yangqiongai.ai.agent.core.circuit;

import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Fallback模型解析器
 * <p>当主模型失败时，自动查找备用模型</p>
 * <p>解析策略：同类型可用模型 → 默认模型 → 无可用</p>
 */
@Slf4j
@Service
public class FallbackModelResolver {

    @Autowired
    private ModelInfoRepository modelInfoRepository;

    /**
     * 解析Fallback模型编码
     *
     * @param currentModelCode 当前失败的模型编码
     * @return Fallback模型编码，无可用时返回null
     */
    public String resolveFallbackModelCode(String currentModelCode) {
        if (currentModelCode == null || currentModelCode.isEmpty()) {
            return null;
        }

        // 查找当前模型信息
        ModelInfo currentModel = modelInfoRepository.findByModelCode(currentModelCode).orElse(null);
        String modelType = currentModel != null ? currentModel.getModelType() : null;

        // 策略1：同类型可用模型（排除当前模型）
        if (modelType != null) {
            List<ModelInfo> candidates = modelInfoRepository.findAll();
            for (ModelInfo candidate : candidates) {
                if (modelType.equals(candidate.getModelType())
                        && !currentModelCode.equals(candidate.getModelCode())
                        && candidate.getModelStatus() != null
                        && candidate.getModelStatus() == 1) {
                    log.info("Fallback模型解析: 同类型备用, currentModel={}, fallbackModel={}, type={}",
                            currentModelCode, candidate.getModelCode(), modelType);
                    return candidate.getModelCode();
                }
            }
        }

        // 策略2：默认模型
        List<ModelInfo> allModels = modelInfoRepository.findAll();
        for (ModelInfo candidate : allModels) {
            if (!currentModelCode.equals(candidate.getModelCode())
                    && candidate.getIsDefault() != null
                    && candidate.getIsDefault() == 1
                    && candidate.getModelStatus() != null
                    && candidate.getModelStatus() == 1) {
                log.info("Fallback模型解析: 默认模型, currentModel={}, fallbackModel={}",
                        currentModelCode, candidate.getModelCode());
                return candidate.getModelCode();
            }
        }

        log.warn("Fallback模型解析: 无可用备用模型, currentModelCode={}", currentModelCode);
        return null;
    }
}
