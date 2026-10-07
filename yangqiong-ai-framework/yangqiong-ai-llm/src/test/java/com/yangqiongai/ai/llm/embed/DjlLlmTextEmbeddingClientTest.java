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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DJL嵌入配置与客户端单元测试
 * @author yangqiong
 */
@DisplayName("DjlEmbeddingConfig 单元测试")
class DjlLlmTextEmbeddingClientTest {

    private static final String FULL_JSON =
            "{\"dimensions\":512,\"modelPath\":\"D:/models/bge\","
                    + "\"modelUrl\":\"djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5\","
                    + "\"maxSeqLength\":256,\"normalize\":false,\"engine\":\"OnnxRuntime\"}";

    @Nested
    @DisplayName("parse 配置解析")
    class ParseTest {

        @Test
        @DisplayName("正常JSON：全字段解析正确")
        void shouldParseFullConfig() {
            DjlEmbeddingConfig config = DjlEmbeddingConfig.parse(FULL_JSON);

            assertThat(config.getDimensions()).isEqualTo(512);
            assertThat(config.getModelPath()).isEqualTo("D:/models/bge");
            assertThat(config.getModelUrl()).isEqualTo("djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5");
            assertThat(config.getMaxSeqLength()).isEqualTo(256);
            assertThat(config.isNormalize()).isFalse();
            assertThat(config.getEngine()).isEqualTo("OnnxRuntime");
        }

        @Test
        @DisplayName("缺省字段：maxSeqLength=512、normalize=true、engine=PyTorch")
        void shouldApplyDefaults() {
            DjlEmbeddingConfig config = DjlEmbeddingConfig.parse("{\"dimensions\":512}");

            assertThat(config.getMaxSeqLength()).isEqualTo(512);
            assertThat(config.isNormalize()).isTrue();
            assertThat(config.getEngine()).isEqualTo("PyTorch");
            assertThat(config.getModelPath()).isNull();
            assertThat(config.getModelUrl()).isNull();
        }

        @Test
        @DisplayName("null配置：抛AiException(MODEL_CONFIG_ERROR)")
        void shouldThrowWhenConfigNull() {
            assertThatThrownBy(() -> DjlEmbeddingConfig.parse(null))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode())
                            .isEqualTo(AiErrorCode.MODEL_CONFIG_ERROR.getCode()));
        }

        @Test
        @DisplayName("空串配置：抛AiException(MODEL_CONFIG_ERROR)")
        void shouldThrowWhenConfigBlank() {
            assertThatThrownBy(() -> DjlEmbeddingConfig.parse("   "))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode())
                            .isEqualTo(AiErrorCode.MODEL_CONFIG_ERROR.getCode()));
        }

        @Test
        @DisplayName("dimensions缺失：抛AiException(MODEL_CONFIG_ERROR)")
        void shouldThrowWhenDimensionsMissing() {
            assertThatThrownBy(() -> DjlEmbeddingConfig.parse("{\"modelPath\":\"D:/models/bge\"}"))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode())
                            .isEqualTo(AiErrorCode.MODEL_CONFIG_ERROR.getCode()));
        }

        @Test
        @DisplayName("dimensions<=0：抛AiException(MODEL_CONFIG_ERROR)")
        void shouldThrowWhenDimensionsNotPositive() {
            assertThatThrownBy(() -> DjlEmbeddingConfig.parse("{\"dimensions\":0}"))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode())
                            .isEqualTo(AiErrorCode.MODEL_CONFIG_ERROR.getCode()));
        }

        @Test
        @DisplayName("非法JSON：抛AiException(MODEL_CONFIG_ERROR)")
        void shouldThrowWhenJsonMalformed() {
            assertThatThrownBy(() -> DjlEmbeddingConfig.parse("{not a json"))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode())
                            .isEqualTo(AiErrorCode.MODEL_CONFIG_ERROR.getCode()));
        }
    }

    @Nested
    @DisplayName("resolveModelSource 模型来源优先级")
    class ResolveModelSourceTest {

        @Test
        @DisplayName("modelPath非空：优先返回modelPath")
        void shouldPreferModelPath() {
            DjlEmbeddingConfig config = DjlEmbeddingConfig.parse(
                    "{\"dimensions\":512,\"modelPath\":\"D:/models/bge\","
                            + "\"modelUrl\":\"djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5\"}");

            assertThat(config.resolveModelSource()).isEqualTo("D:/models/bge");
        }

        @Test
        @DisplayName("modelPath空、modelUrl非空：返回modelUrl")
        void shouldUseModelUrlWhenPathAbsent() {
            DjlEmbeddingConfig config = DjlEmbeddingConfig.parse(
                    "{\"dimensions\":512,\"modelUrl\":\"djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5\"}");

            assertThat(config.resolveModelSource())
                    .isEqualTo("djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5");
        }

        @Test
        @DisplayName("modelPath与modelUrl均空：返回内置默认HF地址")
        void shouldUseDefaultWhenBothAbsent() {
            DjlEmbeddingConfig config = DjlEmbeddingConfig.parse("{\"dimensions\":512}");

            assertThat(config.resolveModelSource())
                    .isEqualTo("djl://ai.djl.huggingface.pytorch/BAAI/bge-base-zh-v1.5");
        }
    }

    @Nested
    @DisplayName("DjlTextEmbeddingClient 构造")
    class ConstructTest {

        @Test
        @DisplayName("modelPath指向不存在路径：构造抛AiException")
        void shouldThrowWhenModelPathNotExist() {
            DjlEmbeddingConfig config = DjlEmbeddingConfig.parse(
                    "{\"dimensions\":512,\"modelPath\":\"Z:/nonexistent_djl_test_path/bge-base-zh-v1.5\"}");

            assertThatThrownBy(() -> new DjlTextEmbeddingClient(config, "test-model"))
                    .isInstanceOf(AiException.class);
        }
    }
}
