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
package com.yangqiongai.ai.memory.vector;

import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.storage.qdrant.QdrantVectorStorage;
import io.qdrant.client.grpc.Points.ScoredPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("MemoryVectorStore 单元测试")
class MemoryVectorStoreTest {

    private static final String COLLECTION = "ai_memory_vectors";

    private QdrantVectorStorage qdrant;

    private MemoryVectorStore store;

    /**
     * 模拟Qdrant行为：集合未创建时search抛出NOT_FOUND，与真实服务一致
     */
    @BeforeEach
    void setUp() {
        qdrant = mock(QdrantVectorStorage.class);
        LanguageModelFactory modelFactory = mock(LanguageModelFactory.class);
        EmbeddingClient embeddingClient = mock(EmbeddingClient.class);
        when(modelFactory.getTextEmbeddingClient(anyString())).thenReturn(embeddingClient);
        when(embeddingClient.embed(anyString())).thenReturn(new float[4]);

        AtomicBoolean collectionCreated = new AtomicBoolean(false);
        when(qdrant.collectionExists(anyString())).thenAnswer(inv -> collectionCreated.get());
        doAnswer(inv -> {
            collectionCreated.set(true);
            return null;
        }).when(qdrant).createCollection(anyString(), anyInt(), anyString());
        when(qdrant.search(anyString(), ArgumentMatchers.any(float[].class), anyInt(), any()))
                .thenAnswer(inv -> {
                    if (!collectionCreated.get()) {
                        throw new RuntimeException("Not found: Collection doesn't exist!");
                    }
                    return new ArrayList<ScoredPoint>();
                });

        store = new MemoryVectorStore();
        injectField("qdrantVectorService", qdrant);
        injectField("languageModelFactory", modelFactory);
        injectField("collectionName", COLLECTION);
        injectField("embeddingModelCode", "bge-base-zh-djl");
    }

    /**
     * 集合不存在时查询应先建集合再检索，不报Collection not found
     */
    @Test
    void searchCreatesMissingCollectionBeforeQuery() {
        List<MemoryVectorStore.ScoredMemoryId> result =
                store.searchScored("今天天气如何", "user1", "FACT", 5);

        assertThat(result).isEmpty();
        verify(qdrant).createCollection(eq(COLLECTION), eq(4), eq("Cosine"));
        verify(qdrant).search(eq(COLLECTION), any(float[].class), eq(5), any());
    }

    /**
     * 集合已存在时查询不应重复创建集合
     */
    @Test
    void searchSkipsCreationWhenCollectionExists() {
        when(qdrant.collectionExists(COLLECTION)).thenReturn(true);
        when(qdrant.search(anyString(), ArgumentMatchers.any(float[].class), anyInt(), any()))
                .thenReturn(List.of(ScoredPoint.newBuilder()
                        .setId(io.qdrant.client.grpc.Common.PointId.newBuilder().setNum(7L).build())
                        .setScore(0.9f)
                        .build()));

        List<MemoryVectorStore.ScoredMemoryId> result =
                store.searchScored("今天天气如何", "user1", "FACT", 5);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMemoryId()).isEqualTo(7L);
        verify(qdrant, never()).createCollection(anyString(), anyInt(), anyString());
    }

    /**
     * 集合初始化后第二次查询不再触发exists判断与建集合
     */
    @Test
    void secondSearchReusesInitializedCollection() {
        store.searchScored("第一问", "user1", "FACT", 5);
        store.searchScored("第二问", "user1", "FACT", 5);

        verify(qdrant, times(1)).collectionExists(COLLECTION);
        verify(qdrant, times(1)).createCollection(eq(COLLECTION), eq(4), eq("Cosine"));
        verify(qdrant, times(2)).search(eq(COLLECTION), any(float[].class), eq(5), any());
    }

    /**
     * 写入路径在集合不存在时先建集合再写入
     */
    @Test
    void embedAndIndexCreatesMissingCollectionBeforeUpsert() {
        boolean result = store.embedAndIndex(100L, "用户喜欢咖啡", "user1", "PREFERENCE");

        assertThat(result).isTrue();
        verify(qdrant).createCollection(eq(COLLECTION), eq(4), eq("Cosine"));
        verify(qdrant).upsertPoints(eq(COLLECTION), any());
    }

    /**
     * 空查询文本直接返回空列表，不触碰向量库
     */
    @Test
    void searchWithBlankQueryReturnsEmptyWithoutTouchingQdrant() {
        assertThat(store.searchScored("", "user1", "FACT", 5)).isEmpty();
        assertThat(store.searchScored("   ", "user1", "FACT", 5)).isEmpty();
        assertThat(store.searchScored(null, "user1", "FACT", 5)).isEmpty();
        assertThat(store.searchScored("问题", "", "FACT", 5)).isEmpty();

        verify(qdrant, never()).search(anyString(), ArgumentMatchers.any(float[].class),
                anyInt(), any());
        verify(qdrant, never()).createCollection(anyString(), anyInt(), anyString());
    }

    /**
     * 空内容或空ID写入直接返回false，不触碰向量库
     */
    @Test
    void embedAndIndexWithInvalidInputReturnsFalse() {
        assertThat(store.embedAndIndex(null, "内容", "user1", "FACT")).isFalse();
        assertThat(store.embedAndIndex(1L, "", "user1", "FACT")).isFalse();
        assertThat(store.embedAndIndex(1L, "   ", "user1", "FACT")).isFalse();

        verify(qdrant, never()).upsertPoints(anyString(), any());
    }

    /**
     * 反射注入私有字段
     * @param fieldName
     * @param value
     */
    private void injectField(String fieldName, Object value) {
        try {
            Field field = MemoryVectorStore.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(store, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}
