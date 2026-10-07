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
import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.index.VersionPayloadBuilder;
import com.yangqiongai.ai.storage.qdrant.QdrantVectorStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * DefaultVectorEmbedder 查询向量化测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultVectorEmbedder 查询向量化测试")
class DefaultVectorEmbedderEmbedQueryTest {

    @Mock
    private QdrantVectorStorage qdrantVectorService;

    @Mock
    private VersionPayloadBuilder versionPayloadBuilder;

    @Mock
    private LanguageModelFactory languageModelFactory;

    @Mock
    private EmbeddingClient embeddingClient;

    private DefaultVectorEmbedder service;

    @BeforeEach
    void setUp() {
        service = new DefaultVectorEmbedder();
        ReflectionTestUtils.setField(service, "qdrantVectorService", qdrantVectorService);
        ReflectionTestUtils.setField(service, "versionPayloadBuilder", versionPayloadBuilder);
        ReflectionTestUtils.setField(service, "languageModelFactory", languageModelFactory);
        ReflectionTestUtils.setField(service, "ragProperties", new RagProperties());
        ReflectionTestUtils.setField(service, "embeddingModelCode", "test-model");
        ReflectionTestUtils.setField(service, "ragMetrics", null);
        ReflectionTestUtils.setField(service, "failedEmbedQueue", new ArrayBlockingQueue<>(100));
    }

    @Test
    @DisplayName("空文本抛出异常")
    void embedQuery_throws_whenTextBlank() {
        assertThatThrownBy(() -> service.embedQuery("   "))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("查询文本不能为空");
    }

    @Test
    @DisplayName("未配置嵌入模型编码时抛出异常, 不再回退默认维度")
    void embedQuery_throws_whenModelCodeMissing() {
        ReflectionTestUtils.setField(service, "embeddingModelCode", "");

        assertThatThrownBy(() -> service.embedQuery("测试查询"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("未配置嵌入模型编码");
    }

    @Test
    @DisplayName("模型维度缺失时抛出异常, 不再回退默认维度")
    void embedQuery_throws_whenDimensionsMissing() {
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(0);

        assertThatThrownBy(() -> service.embedQuery("测试查询"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("未配置有效维度");
    }

    @Test
    @DisplayName("获取嵌入客户端异常时抛出异常, 不再回退默认维度")
    void embedQuery_throws_whenClientBuildFails() {
        when(languageModelFactory.getTextEmbeddingClient(anyString()))
                .thenThrow(new RuntimeException("模型不存在"));

        assertThatThrownBy(() -> service.embedQuery("测试查询"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("获取嵌入模型维度失败");
    }

    @Test
    @DisplayName("正常返回模型维度对应的向量")
    void embedQuery_returnsVector_whenNormal() {
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(768);
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(new float[768]));

        float[] result = service.embedQuery("测试查询");

        assertThat(result).hasSize(768);
    }

    @Test
    @DisplayName("相同查询命中缓存, 不重复调用嵌入模型")
    void embedQuery_usesCache_forSameQuery() {
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(768);
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(new float[768]));

        service.embedQuery("测试查询");
        service.embedQuery("测试查询");
        service.embedQuery(" 测试查询 ");

        verify(embeddingClient, times(1)).embedBatch(any());
    }

    @Test
    @DisplayName("缓存返回向量副本, 修改返回值不影响缓存")
    void embedQuery_returnsCopy_toProtectCache() {
        when(languageModelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.getDimensions()).thenReturn(768);
        when(embeddingClient.embedBatch(any())).thenReturn(List.of(new float[768]));

        float[] first = service.embedQuery("测试查询");
        first[0] = 999.0f;
        float[] second = service.embedQuery("测试查询");

        assertThat(second[0]).isZero();
    }
}
