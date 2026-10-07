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
package com.yangqiongai.ai.llm.embed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DJL嵌入配置
 * @author yangqiong
 */
public class DjlEmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(DjlEmbeddingConfig.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * 默认HuggingFace模型URL
     */
    private static final String DEFAULT_MODEL_URL = "djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5";

    /**
     * 向量维度
     */
    private final int dimensions;

    /**
     * 本地模型路径(优先于modelUrl)
     */
    private final String modelPath;

    /**
     * 模型下载URL
     */
    private final String modelUrl;

    /**
     * tokenizer最大序列长度
     */
    private final int maxSeqLength;

    /**
     * 是否L2归一化输出向量
     */
    private final boolean normalize;

    /**
     * 推理引擎
     */
    private final String engine;

    private DjlEmbeddingConfig(int dimensions, String modelPath, String modelUrl,
                               int maxSeqLength, boolean normalize, String engine) {
        this.dimensions = dimensions;
        this.modelPath = modelPath;
        this.modelUrl = modelUrl;
        this.maxSeqLength = maxSeqLength;
        this.normalize = normalize;
        this.engine = engine;
    }

    /**
     * 从modelConfig JSON解析DJL配置
     * @param jsonConfig
     * @return
     */
    public static DjlEmbeddingConfig parse(String jsonConfig) {
        if (jsonConfig == null || jsonConfig.trim().isEmpty()) {
            throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR, "DJL嵌入配置为空");
        }
        try {
            JsonNode root = MAPPER.readTree(jsonConfig);
            int dimensions = root.path("dimensions").asInt(0);
            if (dimensions <= 0) {
                throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR,
                        "DJL嵌入配置缺少dimensions或dimensions<=0");
            }
            String modelPath = root.path("modelPath").asText(null);
            String modelUrl = root.path("modelUrl").asText(null);
            int maxSeqLength = root.path("maxSeqLength").asInt(512);
            boolean normalize = root.path("normalize").asBoolean(true);
            String engine = root.path("engine").asText("PyTorch");
            return new DjlEmbeddingConfig(dimensions, modelPath, modelUrl, maxSeqLength, normalize, engine);
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR, "解析DJL嵌入配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取模型加载来源(本地路径优先,其次modelUrl,最后默认HF地址)
     * @return
     */
    public String resolveModelSource() {
        if (modelPath != null && !modelPath.trim().isEmpty()) {
            return modelPath;
        }
        if (modelUrl != null && !modelUrl.trim().isEmpty()) {
            return modelUrl;
        }
        return DEFAULT_MODEL_URL;
    }

    public int getDimensions() {
        return dimensions;
    }

    public String getModelPath() {
        return modelPath;
    }

    public String getModelUrl() {
        return modelUrl;
    }

    public int getMaxSeqLength() {
        return maxSeqLength;
    }

    public boolean isNormalize() {
        return normalize;
    }

    public String getEngine() {
        return engine;
    }
}
