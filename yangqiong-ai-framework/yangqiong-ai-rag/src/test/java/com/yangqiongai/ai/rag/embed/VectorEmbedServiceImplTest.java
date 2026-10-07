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
package com.yangqiongai.ai.rag.embed;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultCollectionNameResolver;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.index.VersionPayloadBuilder;
import com.yangqiongai.ai.storage.qdrant.QdrantVectorStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DefaultVectorEmbedder 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class DefaultVectorEmbedderTest {

    @Mock
    private QdrantVectorStorage qdrantVectorService;

    @Mock
    private VersionPayloadBuilder versionPayloadBuilder;

    @Mock
    private LanguageModelFactory languageModelFactory;

    @InjectMocks
    private DefaultVectorEmbedder vectorEmbedder;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(vectorEmbedder, "embeddingModelCode", "test-model");
        ReflectionTestUtils.setField(vectorEmbedder, "ragProperties", new RagProperties());
        CollectionNameResolver defaultResolver = new DefaultCollectionNameResolver();
        ReflectionTestUtils.setField(vectorEmbedder, "collectionNameResolver", defaultResolver);
    }

    @Test
    @DisplayName("embedQuery - 空文本抛出AiException")
    void embedQuery_returnsZeroVector_whenEmptyText() {
        assertThatThrownBy(() -> vectorEmbedder.embedQuery(""))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("查询文本不能为空");
    }

    @Test
    @DisplayName("embedQuery - null文本抛出AiException")
    void embedQuery_returnsZeroVector_whenNullText() {
        assertThatThrownBy(() -> vectorEmbedder.embedQuery(null))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("查询文本不能为空");
    }

    @Test
    @DisplayName("embedQuery - 纯空白文本抛出AiException")
    void embedQuery_returnsZeroVector_whenBlankText() {
        assertThatThrownBy(() -> vectorEmbedder.embedQuery("   "))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("查询文本不能为空");
    }

    @Test
    @DisplayName("validateEmbedding - 维度不匹配返回false")
    void validateEmbedding_returnsFalse_whenWrongSize() {
        float[] vector = new float[512];
        vector[0] = 1.0f;

        assertThat(vectorEmbedder.validateEmbedding(vector, 1024)).isFalse();
    }

    @Test
    @DisplayName("validateEmbedding - 全零向量返回false")
    void validateEmbedding_returnsFalse_whenAllZeros() {
        float[] vector = new float[1024];

        assertThat(vectorEmbedder.validateEmbedding(vector, 1024)).isFalse();
    }

    @Test
    @DisplayName("validateEmbedding - 包含NaN返回false")
    void validateEmbedding_returnsFalse_whenNaN() {
        float[] vector = new float[1024];
        vector[0] = 1.0f;
        vector[1] = Float.NaN;

        assertThat(vectorEmbedder.validateEmbedding(vector, 1024)).isFalse();
    }

    @Test
    @DisplayName("validateEmbedding - 包含Infinity返回false")
    void validateEmbedding_returnsFalse_whenInfinity() {
        float[] vector = new float[1024];
        vector[0] = 1.0f;
        vector[1] = Float.POSITIVE_INFINITY;

        assertThat(vectorEmbedder.validateEmbedding(vector, 1024)).isFalse();
    }

    @Test
    @DisplayName("validateEmbedding - 有效向量返回true")
    void validateEmbedding_returnsTrue_whenValid() {
        float[] vector = new float[1024];
        vector[0] = 0.5f;
        vector[1] = -0.3f;

        assertThat(vectorEmbedder.validateEmbedding(vector, 1024)).isTrue();
    }

    @Test
    @DisplayName("validateEmbedding - null向量返回false")
    void validateEmbedding_returnsFalse_whenNull() {
        assertThat(vectorEmbedder.validateEmbedding(null, 1024)).isFalse();
    }

    @Test
    @DisplayName("validateEmbedding - 空数组返回false")
    void validateEmbedding_returnsFalse_whenEmpty() {
        assertThat(vectorEmbedder.validateEmbedding(new float[0], 1024)).isFalse();
    }

    @Test
    @DisplayName("validateEmbedding - 无参版本使用默认1024维度")
    void validateEmbedding_defaultSize_uses1024() {
        float[] validVector = new float[1024];
        validVector[0] = 1.0f;

        assertThat(vectorEmbedder.validateEmbedding(validVector)).isTrue();

        float[] wrongSizeVector = new float[512];
        wrongSizeVector[0] = 1.0f;

        assertThat(vectorEmbedder.validateEmbedding(wrongSizeVector)).isFalse();
    }
}
