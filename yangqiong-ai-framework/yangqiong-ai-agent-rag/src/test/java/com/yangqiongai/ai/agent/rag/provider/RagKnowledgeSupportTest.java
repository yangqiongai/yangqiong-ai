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
package com.yangqiongai.ai.agent.rag.provider;

import com.yangqiongai.ai.agent.core.provider.KnowledgeRetrievalResult;
import com.yangqiongai.ai.agent.core.provider.RerankOptions;
import com.yangqiongai.ai.agent.rag.resolver.KnowledgeResolver;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RagKnowledgeSupport单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RagKnowledgeSupport 单元测试")
class RagKnowledgeSupportTest {

    @Mock
    private ObjectProvider<KnowledgeResolver> resolverProvider;

    private RagKnowledgeSupport support;

    @BeforeEach
    void setUp() {
        support = new RagKnowledgeSupport(resolverProvider);
    }

    private RetrievalEvidence evidence(String kbId, String docName) {
        RetrievalEvidence item = new RetrievalEvidence(
                "shell是命令行解释器", "doc-1", docName, "slice-1", 0.72, null);
        item.setKbId(kbId);
        return item;
    }

    @Test
    @DisplayName("retrieveWithEvidences - 返回上下文文本与结构化证据")
    void retrieveWithEvidences_returnsContextAndEvidences() {
        KnowledgeResolver resolver = mock(KnowledgeResolver.class);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolve(any(), anyList(), anyInt()))
                .thenReturn(List.of(evidence("kb-1", "TLCL-25.12A.pdf")));

        KnowledgeRetrievalResult result = support.retrieveWithEvidences("什么是shell", List.of("kb-1"), 5);

        assertThat(result.context()).startsWith("<knowledge>");
        assertThat(result.context()).endsWith("</knowledge>");
        assertThat(result.context()).contains("<chunk source=\"TLCL-25.12A.pdf\">\nshell是命令行解释器\n</chunk>");
        assertThat(result.evidences()).hasSize(1);
        Map<String, Object> map = result.evidences().get(0);
        assertThat(map.get("kbId")).isEqualTo("kb-1");
        assertThat(map.get("kbName")).isNull();
        assertThat(map.get("sourceDocId")).isEqualTo("doc-1");
        assertThat(map.get("sourceDocName")).isEqualTo("TLCL-25.12A.pdf");
        assertThat(map.get("sliceId")).isEqualTo("slice-1");
        assertThat(map.get("score")).isEqualTo(0.72);
    }

    @Test
    @DisplayName("retrieveWithEvidences - 多库检索失败时跳过失败库")
    void retrieveWithEvidences_skipsFailedKb() {
        KnowledgeResolver resolver = mock(KnowledgeResolver.class);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolve(any(), anyList(), anyInt()))
                .thenThrow(new RuntimeException("检索超时"))
                .thenReturn(List.of(evidence("kb-2", "卷3.pdf")));

        KnowledgeRetrievalResult result = support.retrieveWithEvidences("查询", List.of("kb-1", "kb-2"), 5);

        assertThat(result.evidences()).hasSize(1);
        assertThat(result.evidences().get(0).get("kbId")).isEqualTo("kb-2");
    }

    @Test
    @DisplayName("retrieveWithEvidences - 空参数或检索器缺失返回空结果")
    void retrieveWithEvidences_emptyInputs() {
        assertThat(support.retrieveWithEvidences(null, List.of("kb-1"), 5).isEmpty()).isTrue();
        assertThat(support.retrieveWithEvidences("  ", List.of("kb-1"), 5).isEmpty()).isTrue();
        assertThat(support.retrieveWithEvidences("查询", null, 5).isEmpty()).isTrue();
        assertThat(support.retrieveWithEvidences("查询", List.of("  "), 5).isEmpty()).isTrue();
        assertThat(support.retrieveWithEvidences("查询", List.of("kb-1"), 5).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("retrieveWithEvidences - 检索结果为空时返回空结果")
    void retrieveWithEvidences_emptyResults() {
        KnowledgeResolver resolver = mock(KnowledgeResolver.class);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolve(any(), anyList(), anyInt())).thenReturn(List.of());

        KnowledgeRetrievalResult result = support.retrieveWithEvidences("查询", List.of("kb-1"), 5);

        assertThat(result.isEmpty()).isTrue();
        assertThat(result.evidences()).isEmpty();
    }

    @Test
    @DisplayName("retrieveWithEvidences - 重排序选项透传给解析器")
    void retrieveWithEvidences_passesRerankOptionsToResolver() {
        KnowledgeResolver resolver = mock(KnowledgeResolver.class);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolve(any(), anyList(), anyInt(), any()))
                .thenReturn(List.of(evidence("kb-1", "TLCL-25.12A.pdf")));
        RerankOptions options = RerankOptions.of(Boolean.FALSE, "deepseek");

        KnowledgeRetrievalResult result = support.retrieveWithEvidences("什么是shell", List.of("kb-1"), 5, options);

        assertThat(result.isEmpty()).isFalse();
        verify(resolver).resolve("什么是shell", List.of("kb-1"), 5, options);
    }

    @Test
    @DisplayName("retrieveWithEvidences - 重排序选项为null时走无覆盖路径")
    void retrieveWithEvidences_nullRerankOptionsUsesLegacyPath() {
        KnowledgeResolver resolver = mock(KnowledgeResolver.class);
        when(resolverProvider.getIfAvailable()).thenReturn(resolver);
        when(resolver.resolve(any(), anyList(), anyInt()))
                .thenReturn(List.of(evidence("kb-1", "TLCL-25.12A.pdf")));

        KnowledgeRetrievalResult result = support.retrieveWithEvidences("什么是shell", List.of("kb-1"), 5, null);

        assertThat(result.isEmpty()).isFalse();
        verify(resolver).resolve("什么是shell", List.of("kb-1"), 5);
    }
}
