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
package com.yangqiongai.ai.llm;

import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 大语言模型选择
 * @author yangqiong
 */
@Service
public class DefaultLlmModelService implements LlmModelService {

    @Autowired
    private ModelInfoRepository modelInfoRepository;

    /**
     * 默认模型标识
     */
    @Value("${ai.model.default:deepseek}")
    private String defaultModel;

    /**
     * 解析当前请求应使用的模型标识
     * @param agentCode
     * @param userId
     * @return
     */
    @Override
    public String resolveModel(String agentCode, String userId) {
        String model = resolveTaskModel(agentCode, userId);
        if (model != null) {
            return model;
        }
        if (defaultModel != null && !defaultModel.isEmpty()) {
            return defaultModel;
        }
        return null;
    }

    /**
     * 获取可用模型列表
     * @return
     */
    @Override
    public List<String> listAvailableModels() {
        List<ModelInfo> models = modelInfoRepository.findAll();
        return models.stream()
                .filter(m -> m.getModelStatus() != null && m.getModelStatus() == 1)
                .map(ModelInfo::getModelCode)
                .collect(Collectors.toList());
    }

    /**
     * 根据模型编码查询模型信息
     * @param modelCode
     * @return
     */
    @Override
    public Optional<ModelInfo> findByModelCode(String modelCode) {
        if (modelCode == null || modelCode.isEmpty()) {
            return Optional.empty();
        }
        return modelInfoRepository.findByModelCode(modelCode);
    }

    /**
     * 按任务解析模型标识
     * @param agentCode
     * @param userId
     * @return
     */
    private String resolveTaskModel(String agentCode, String userId) {
        List<ModelInfo> models = modelInfoRepository.findAll();
        // 按模型类型匹配任务编码
        for (ModelInfo model : models) {
            if (agentCode != null && agentCode.equals(model.getModelType())
                    && model.getModelStatus() != null && model.getModelStatus() == 1) {
                return model.getModelCode();
            }
        }
        // 未匹配任务时查找默认模型
        for (ModelInfo model : models) {
            if (model.getIsDefault() != null && model.getIsDefault() == 1
                    && model.getModelStatus() != null && model.getModelStatus() == 1) {
                return model.getModelCode();
            }
        }
        return null;
    }
}
