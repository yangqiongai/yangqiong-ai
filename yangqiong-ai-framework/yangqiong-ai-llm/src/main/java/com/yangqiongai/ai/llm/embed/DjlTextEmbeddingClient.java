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

import ai.djl.Model;
import ai.djl.inference.Predictor;
import ai.djl.repository.zoo.Criteria;
import ai.djl.repository.zoo.ZooModel;
import ai.djl.huggingface.translator.TextEmbeddingTranslatorFactory;
import ai.djl.translate.TranslateException;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * DJL本地文本嵌入
 * @author yangqiong
 */
public class DjlTextEmbeddingClient implements EmbeddingClient, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DjlTextEmbeddingClient.class);

    private final DjlEmbeddingConfig config;

    private final int dimensions;

    private final ZooModel<String, float[]> model;

    private final Predictor<String, float[]> predictor;

    private final Object lock = new Object();

    /**
     * 构造DJL本地嵌入客户端并加载模型
     * @param config
     * @param modelCode
     */
    public DjlTextEmbeddingClient(DjlEmbeddingConfig config, String modelCode) {
        this.config = config;
        this.dimensions = config.getDimensions();
        try {
            Criteria.Builder<String, float[]> builder = Criteria.builder()
                    .setTypes(String.class, float[].class)
                    .optEngine(config.getEngine())
                    .optTranslatorFactory(new TextEmbeddingTranslatorFactory());

            String source = config.resolveModelSource();
            if (source.startsWith("djl://") || source.startsWith("http")) {
                builder.optModelUrls(source);
            } else {
                builder.optModelPath(Paths.get(source));
            }

            Criteria<String, float[]> criteria = builder.build();
            this.model = criteria.loadModel();
            this.predictor = model.newPredictor();
            log.info("DJL嵌入模型加载成功, modelCode={}, source={}, dimensions={}",
                    modelCode, source, dimensions);
        } catch (Exception e) {
            log.error("DJL嵌入模型加载失败, modelCode={}, source={}", modelCode,
                    config.resolveModelSource(), e);
            throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR,
                    "DJL嵌入模型加载失败: " + e.getMessage(), e);
        }
    }

    /**
     * 单条文本嵌入
     * @param text
     * @return
     */
    @Override
    public float[] embed(String text) {
        if (text == null || text.isEmpty()) {
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED, "嵌入文本不能为空");
        }
        synchronized (lock) {
            try {
                return predictor.predict(text);
            } catch (Exception e) {
                throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                        "DJL嵌入推理失败: " + e.getMessage(), e);
            }
        }
    }

    /**
     * 批量文本嵌入(串行推理,text2sql场景量小无需真批量)
     * @param texts
     * @return
     */
    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        List<float[]> results = null;
        try {
            results = predictor.batchPredict(texts);
        } catch (TranslateException e) {
            throw new AiException(AiErrorCode.RAG_EMBEDDING_FAILED,
                    "DJL嵌入推理失败: " + e.getMessage(), e);
        }
        return results;
    }

    /**
     * 获取向量维度
     * @return
     */
    @Override
    public int getDimensions() {
        return dimensions;
    }

    /**
     * 释放DJL native资源
     */
    @Override
    public void close() {
        try {
            if (predictor != null) {
                predictor.close();
            }
        } catch (Exception e) {
            log.warn("关闭DJL Predictor失败", e);
        }
        try {
            if (model != null) {
                model.close();
            }
        } catch (Exception e) {
            log.warn("关闭DJL Model失败", e);
        }
    }
}
