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
package com.yangqiongai.ai.rag.retriever;

import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import com.yangqiongai.ai.rag.model.ChunkCandidate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.retry.policy.NeverRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * DefaultFullTextSearcher 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class DefaultFullTextSearcherTest {

    @Mock
    private SliceRecordRepository sliceRecordRepository;

    @Mock
    private RagProperties ragProperties;

    private RetryTemplate retryTemplate;

    private DefaultFullTextSearcher fullTextSearcher;

    @BeforeEach
    void setUp() {
        fullTextSearcher = new DefaultFullTextSearcher();
        retryTemplate = new RetryTemplate();
        retryTemplate.setRetryPolicy(new NeverRetryPolicy());

        RagProperties.Fulltext fulltextProps = new RagProperties.Fulltext();
        fulltextProps.setMinOverfetch(10);
        fulltextProps.setOverfetchFactor(3);
        fulltextProps.setFallbackResultSize(20);
        fulltextProps.setLikeFallbackLimit(50);
        fulltextProps.setBaseRelevance(0.3);
        fulltextProps.setExactMatchBonus(0.4);
        fulltextProps.setTermHitBonus(0.1);

        org.springframework.test.util.ReflectionTestUtils.setField(fullTextSearcher, "sliceRecordRepository", sliceRecordRepository);
        org.springframework.test.util.ReflectionTestUtils.setField(fullTextSearcher, "ragProperties", ragProperties);
        org.springframework.test.util.ReflectionTestUtils.setField(fullTextSearcher, "retryTemplate", retryTemplate);
        // 参数校验类用例不会触达fulltext配置，使用lenient避免UnnecessaryStubbingException
        lenient().when(ragProperties.getFulltext()).thenReturn(fulltextProps);
    }

    @Test
    @DisplayName("searchFullText - 空query返回空列表")
    void searchFullText_returnsEmpty_whenQueryEmpty() {
        List<ChunkCandidate> result = fullTextSearcher.searchFullText("", "kb-1", 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchFullText - null query返回空列表")
    void searchFullText_returnsEmpty_whenQueryNull() {
        List<ChunkCandidate> result = fullTextSearcher.searchFullText(null, "kb-1", 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchFullText - 纯空白query返回空列表")
    void searchFullText_returnsEmpty_whenQueryBlank() {
        List<ChunkCandidate> result = fullTextSearcher.searchFullText("   ", "kb-1", 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchFullText - 空kbId返回空列表")
    void searchFullText_returnsEmpty_whenKbIdEmpty() {
        List<ChunkCandidate> result = fullTextSearcher.searchFullText("查询", "", 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchFullText - null kbId返回空列表")
    void searchFullText_returnsEmpty_whenKbIdNull() {
        List<ChunkCandidate> result = fullTextSearcher.searchFullText("查询", null, 5, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("searchFullText - 切片匹配时返回结果")
    void searchFullText_returnsResults_whenSliceMatches() {
        SliceRecord record = new SliceRecord();
        record.setId(1L);
        record.setSliceId("slice-1");
        record.setDocId("doc-1");
        record.setContent("这是一段包含查询关键词的内容");
        record.setSliceType("parent");
        record.setParentId(null);

        when(sliceRecordRepository.searchFullTextSlices(eq("kb-1"), isNull(), anyBoolean(), anyInt(), eq("查询"), isNull()))
                .thenReturn(List.of(record));

        List<ChunkCandidate> result = fullTextSearcher.searchFullText("查询", "kb-1", 5, null);

        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getText()).isEqualTo("这是一段包含查询关键词的内容");
        assertThat(result.get(0).getDocId()).isEqualTo("doc-1");
    }

    @Test
    @DisplayName("searchFullText - 切片无匹配时降级到父块查询")
    void searchFullText_fallsBackToParent_whenNoSliceMatch() {
        ArgumentCaptor<String> keywordCaptor = ArgumentCaptor.forClass(String.class);
        when(sliceRecordRepository.searchFullTextSlices(eq("kb-1"), isNull(), anyBoolean(), anyInt(), keywordCaptor.capture(), isNull()))
                .thenReturn(Collections.emptyList());
        when(sliceRecordRepository.searchFullTextSlices(eq("kb-1"), isNull(), anyBoolean(), anyInt(), anyString(), eq("parent")))
                .thenReturn(List.of(createParentSliceRecord()));

        List<ChunkCandidate> result = fullTextSearcher.searchFullText("查询关键词", "kb-1", 5, null);

        assertThat(result).isNotEmpty();
        // 布尔模式查询关键词按分词结果空格拼接（OR匹配），不再是原始整串
        assertThat(keywordCaptor.getValue()).contains(" ");
    }

    @Test
    @DisplayName("searchFullText - 无任何匹配时返回空列表")
    void searchFullText_returnsEmpty_whenNoMatch() {
        when(sliceRecordRepository.searchFullTextSlices(anyString(), isNull(), anyBoolean(), anyInt(), anyString(), isNull()))
                .thenReturn(Collections.emptyList());
        when(sliceRecordRepository.searchFullTextSlices(anyString(), isNull(), anyBoolean(), anyInt(), anyString(), eq("parent")))
                .thenReturn(Collections.emptyList());

        List<ChunkCandidate> result = fullTextSearcher.searchFullText("不存在的关键词", "kb-1", 5, null);

        assertThat(result).isEmpty();
    }

    private SliceRecord createParentSliceRecord() {
        SliceRecord parentSlice = new SliceRecord();
        parentSlice.setId(1L);
        parentSlice.setSliceId("parent-1");
        parentSlice.setDocId("doc-1");
        parentSlice.setContent("父块包含查询关键词");
        parentSlice.setSliceType("parent");
        parentSlice.setParentId(null);
        return parentSlice;
    }
}
