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

import java.util.List;
import java.util.Optional;

import com.yangqiongai.ai.llm.model.ModelInfo;

/**
 * 大语言模型选择
 * @author yangqiong
 */
public interface LlmModelService {

    /**
     * 解析当前请求应使用的模型标识
     * @param agentCode
     * @param userId
     * @return
     */
    String resolveModel(String agentCode, String userId);

    /**
     * 获取可用模型列表
     * @return
     */
    List<String> listAvailableModels();

    /**
     * 根据模型编码查询模型信息
     * @param modelCode
     * @return
     */
    Optional<ModelInfo> findByModelCode(String modelCode);
}
