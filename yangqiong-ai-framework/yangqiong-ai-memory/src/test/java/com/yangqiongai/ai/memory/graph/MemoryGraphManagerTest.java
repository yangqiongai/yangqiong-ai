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
package com.yangqiongai.ai.memory.graph;

import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * 知识图谱服务单元测试
 * @author yangqiong
 */
@DisplayName("知识图谱服务单元测试")
class MemoryGraphManagerTest {

    @Mock
    private LanguageModelFactory languageModelFactory;

    private MemoryGraphManager service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new MemoryGraphManager();
        injectField(service, "languageModelFactory", languageModelFactory);
        injectField(service, "extractionModelCode", "default");
    }

    @Test
    @DisplayName("提取三元组：正确解析LLM返回的JSON数组")
    void extractTriples_validJson_returnsTriples() {
        String llmResponse = "[{\"subject\":\"用户\",\"predicate\":\"喜欢\",\"object\":\"咖啡\","
                + "\"isCausal\":false,\"confidence\":0.9},"
                + "{\"subject\":\"项目延期\",\"predicate\":\"导致\",\"object\":\"加班\","
                + "\"isCausal\":true,\"confidence\":0.85}]";
        when(languageModelFactory.generateText(anyString(), anyString())).thenReturn(llmResponse);

        List<KnowledgeTriple> triples = service.extractTriples("用户喜欢咖啡，项目延期导致加班", "user1");

        assertThat(triples).hasSize(2);
        assertThat(triples.get(0).getSubject()).isEqualTo("用户");
        assertThat(triples.get(0).getPredicate()).isEqualTo("喜欢");
        assertThat(triples.get(0).getObject()).isEqualTo("咖啡");
        assertThat(triples.get(0).isCausal()).isFalse();
        assertThat(triples.get(0).getConfidence()).isEqualTo(0.9);
        assertThat(triples.get(1).getSubject()).isEqualTo("项目延期");
        assertThat(triples.get(1).isCausal()).isTrue();
        assertThat(triples.get(1).getUserId()).isEqualTo("user1");
        assertThat(triples.get(1).getTripleId()).isNotBlank();
    }

    @Test
    @DisplayName("提取三元组：带时间有效性字段解析")
    void extractTriples_withValidity_returnsParsedTime() {
        String llmResponse = "[{\"subject\":\"用户\",\"predicate\":\"任职于\",\"object\":\"A公司\","
                + "\"isCausal\":false,\"validFrom\":\"2026-01-01T00:00:00\","
                + "\"validUntil\":\"2026-12-31T00:00:00\",\"confidence\":0.8}]";
        when(languageModelFactory.generateText(anyString(), anyString())).thenReturn(llmResponse);

        List<KnowledgeTriple> triples = service.extractTriples("用户在A公司任职", "user1");

        assertThat(triples).hasSize(1);
        KnowledgeTriple triple = triples.get(0);
        assertThat(triple.getValidFrom()).isNotNull();
        assertThat(triple.getValidUntil()).isNotNull();
        assertThat(triple.getValidFrom().getYear()).isEqualTo(2026);
        assertThat(triple.getValidUntil().getMonthValue()).isEqualTo(12);
    }

    @Test
    @DisplayName("提取三元组：空文本返回空列表")
    void extractTriples_blankText_returnsEmpty() {
        List<KnowledgeTriple> triples = service.extractTriples("", "user1");
        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("提取三元组：null文本返回空列表")
    void extractTriples_nullText_returnsEmpty() {
        List<KnowledgeTriple> triples = service.extractTriples(null, "user1");
        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("提取三元组：LLM工厂未注入时降级返回空列表")
    void extractTriples_noLLM_returnsEmpty() throws Exception {
        injectField(service, "languageModelFactory", null);

        List<KnowledgeTriple> triples = service.extractTriples("用户喜欢咖啡", "user1");

        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("提取三元组：LLM返回非JSON格式降级返回空列表")
    void extractTriples_invalidJson_returnsEmpty() {
        when(languageModelFactory.generateText(anyString(), anyString())).thenReturn("这不是JSON");

        List<KnowledgeTriple> triples = service.extractTriples("测试文本", "user1");

        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("提取三元组：LLM返回空字符串降级返回空列表")
    void extractTriples_emptyResponse_returnsEmpty() {
        when(languageModelFactory.generateText(anyString(), anyString())).thenReturn("");

        List<KnowledgeTriple> triples = service.extractTriples("测试文本", "user1");

        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("提取三元组：LLM抛异常时降级返回空列表")
    void extractTriples_llmThrowsException_returnsEmpty() {
        when(languageModelFactory.generateText(anyString(), anyString()))
                .thenThrow(new RuntimeException("LLM调用失败"));

        List<KnowledgeTriple> triples = service.extractTriples("测试文本", "user1");

        assertThat(triples).isEmpty();
    }

    @Test
    @DisplayName("提取三元组：过滤字段不完整的条目")
    void extractTriples_partialFields_filtersInvalid() {
        String llmResponse = "[{\"subject\":\"用户\",\"predicate\":\"喜欢\",\"object\":\"咖啡\","
                + "\"isCausal\":false,\"confidence\":0.9},"
                + "{\"subject\":\"\",\"predicate\":\"喜欢\",\"object\":\"茶\"},"
                + "{\"subject\":\"项目\",\"predicate\":\"\",\"object\":\"延期\"}]";
        when(languageModelFactory.generateText(anyString(), anyString())).thenReturn(llmResponse);

        List<KnowledgeTriple> triples = service.extractTriples("测试", "user1");

        assertThat(triples).hasSize(1);
        assertThat(triples.get(0).getSubject()).isEqualTo("用户");
    }

    @Test
    @DisplayName("提取三元组：Markdown代码块包裹的JSON能正确解析")
    void extractTriples_markdownWrappedJson_returnsTriples() {
        String llmResponse = "```json\n[{\"subject\":\"用户\",\"predicate\":\"喜欢\","
                + "\"object\":\"咖啡\",\"isCausal\":false,\"confidence\":0.9}]\n```";
        when(languageModelFactory.generateText(anyString(), anyString())).thenReturn(llmResponse);

        List<KnowledgeTriple> triples = service.extractTriples("用户喜欢咖啡", "user1");

        assertThat(triples).hasSize(1);
        assertThat(triples.get(0).getObject()).isEqualTo("咖啡");
    }

    @Test
    @DisplayName("按实体查询：空参数返回空列表")
    void searchByEntity_blankParams_returnsEmpty() {
        assertThat(service.searchByEntity("", "user1", 2)).isEmpty();
        assertThat(service.searchByEntity(null, "user1", 2)).isEmpty();
        assertThat(service.searchByEntity("用户", "", 2)).isEmpty();
        assertThat(service.searchByEntity("用户", "user1", 0)).isEmpty();
    }

    @Test
    @DisplayName("因果链查询：空参数返回空列表")
    void searchByCausalChain_blankParams_returnsEmpty() {
        assertThat(service.searchByCausalChain("", "user1", 2)).isEmpty();
        assertThat(service.searchByCausalChain(null, "user1", 2)).isEmpty();
        assertThat(service.searchByCausalChain("项目延期", "", 2)).isEmpty();
        assertThat(service.searchByCausalChain("项目延期", "user1", 0)).isEmpty();
    }

    @Test
    @DisplayName("保存三元组：空列表返回0")
    void saveTriples_emptyList_returnsZero() {
        assertThat(service.saveTriples(null, "user1")).isZero();
        assertThat(service.saveTriples(java.util.Collections.emptyList(), "user1")).isZero();
    }

    @Test
    @DisplayName("保存三元组：LLM工厂未注入时返回0")
    void saveTriples_noLLM_returnsZero() throws Exception {
        injectField(service, "languageModelFactory", null);
        KnowledgeTriple triple = new KnowledgeTriple("用户", "喜欢", "咖啡", false);

        int saved = service.saveTriples(List.of(triple), "user1");

        assertThat(saved).isZero();
    }

    @Test
    @DisplayName("三元组文本拼接：subject + predicate + object")
    void tripleToText_correctConcatenation() {
        KnowledgeTriple triple = new KnowledgeTriple("用户", "喜欢", "咖啡", false);
        assertThat(triple.toText()).isEqualTo("用户 喜欢 咖啡");
    }

    @Test
    @DisplayName("三元组字符串表示：包含因果标注")
    void tripleToString_causalAnnotated() {
        KnowledgeTriple causal = new KnowledgeTriple("项目延期", "导致", "加班", true);
        KnowledgeTriple normal = new KnowledgeTriple("用户", "喜欢", "咖啡", false);
        assertThat(causal.toString()).contains("因果");
        assertThat(normal.toString()).doesNotContain("因果");
    }

    private static void injectField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
