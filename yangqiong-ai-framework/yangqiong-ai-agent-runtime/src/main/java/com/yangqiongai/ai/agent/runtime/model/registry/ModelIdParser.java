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
package com.yangqiongai.ai.agent.runtime.model.registry;

import java.util.Set;

/**
 * 模型ID解析器
 * <p>
 * 解析"provider:modelName"格式的模型ID，兼容无前缀格式（使用默认provider）。
 * </p>
 * @author yangqiong
 */
public final class ModelIdParser {

    /**
     * 已知provider集合
     */
    private static final Set<String> KNOWN_PROVIDERS = Set.of("openai", "dashscope", "ollama", "anthropic");

    /**
     * 默认provider
     */
    private static final String DEFAULT_PROVIDER = "openai";

    private ModelIdParser() {
    }

    /**
     * 解析模型ID
     * @param modelId
     * @param defaultProvider
     * @return
     */
    public static ParsedModelId parse(String modelId, String defaultProvider) {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("模型 ID 不能为空");
        }
        String effectiveDefault = defaultProvider != null && !defaultProvider.isBlank()
                ? defaultProvider : DEFAULT_PROVIDER;
        int colonIndex = modelId.indexOf(':');
        if (colonIndex <= 0) {
            // 无冒号或前缀为空：使用默认provider
            String modelName = colonIndex == 0 ? modelId.substring(1) : modelId;
            if (modelName.isBlank()) {
                throw new IllegalArgumentException("模型名称不能为空");
            }
            return new ParsedModelId(effectiveDefault, modelName);
        }
        String providerPrefix = modelId.substring(0, colonIndex);
        if (!KNOWN_PROVIDERS.contains(providerPrefix)) {
            // 前缀非已知provider：整个modelId作为modelName，使用默认provider
            return new ParsedModelId(effectiveDefault, modelId);
        }
        String modelName = modelId.substring(colonIndex + 1);
        if (modelName.isBlank()) {
            throw new IllegalArgumentException("模型名称不能为空");
        }
        return new ParsedModelId(providerPrefix, modelName);
    }

    /**
     * 解析结果
     */
    public record ParsedModelId(String provider, String modelName) {
    }
}
