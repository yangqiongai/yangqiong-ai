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
package com.yangqiongai.ai.rag.grader;

import com.yangqiongai.ai.llm.factory.LanguageModelFactory;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LlmContextGrader 单元测试
 * @author yangqiong
 */
@DisplayName("LlmContextGrader 单元测试")
class LlmContextGraderTest {

    private LlmContextGrader grader;

    private String stubResponse;

    private RuntimeException stubException;

    @BeforeEach
    void setUp() {
        grader = new LlmContextGrader();
        stubResponse = null;
        stubException = null;
        ReflectionTestUtils.setField(grader, "languageModelFactory", createStubFactory());
        ReflectionTestUtils.setField(grader, "modelCode", "default");
        ReflectionTestUtils.setField(grader, "maxEvidences", 10);
    }

    /**
     * 创建LanguageModelFactory子类桩,避免Mockito加载agentscope依赖
     */
    private LanguageModelFactory createStubFactory() {
        return new LanguageModelFactory(null) {
            @Override
            public String generateText(String modelCode, String systemPrompt, String userPrompt) {
                if (stubException != null) {
                    throw stubException;
                }
                return stubResponse;
            }
        };
    }

    private List<RetrievalEvidence> buildEvidences(int count) {
        List<RetrievalEvidence> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            RetrievalEvidence evidence = new RetrievalEvidence();
            evidence.setContent("证据内容" + i);
            evidence.setSourceDocId("doc-" + i);
            evidence.setSliceId("slice-" + i);
            evidence.setScore(0.8 - i * 0.05);
            list.add(evidence);
        }
        return list;
    }

    @Test
    @DisplayName("LLM返回sufficient=true时正确解析")
    void grade_returnsSufficient_whenLLMSaysSufficient() {
        stubResponse = "{\"sufficient\":true,\"confidence\":0.9,\"reason\":\"证据充分\"}";

        ContextGradeResult result = grader.grade("查询", buildEvidences(3));

        assertThat(result.isSufficient()).isTrue();
        // sufficient=true时confidence硬编码为1.0
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getReason()).isEqualTo("证据充分");
    }

    @Test
    @DisplayName("LLM返回sufficient=false时正确解析missing维度")
    void grade_returnsInsufficient_whenLLMSaysInsufficient() {
        stubResponse = "{\"sufficient\":false,\"confidence\":0.3,\"missing\":[\"时效性\",\"具体数据\"],\"reason\":\"缺少最新数据\"}";

        ContextGradeResult result = grader.grade("查询", buildEvidences(3));

        assertThat(result.isSufficient()).isFalse();
        assertThat(result.getConfidence()).isEqualTo(0.3);
        assertThat(result.getMissingDimensions()).containsExactly("时效性", "具体数据");
        assertThat(result.getReason()).isEqualTo("缺少最新数据");
    }

    @Test
    @DisplayName("LanguageModelFactory未注入时降级返回sufficient=true")
    void grade_degradesToSufficient_whenLLMFactoryNull() {
        ReflectionTestUtils.setField(grader, "languageModelFactory", null);

        ContextGradeResult result = grader.grade("查询", buildEvidences(3));

        assertThat(result.isSufficient()).isTrue();
    }

    @Test
    @DisplayName("LLM抛异常时降级返回sufficient=true")
    void grade_degradesToSufficient_whenLLMThrows() {
        stubException = new RuntimeException("LLM调用失败");

        ContextGradeResult result = grader.grade("查询", buildEvidences(3));

        assertThat(result.isSufficient()).isTrue();
    }

    @Test
    @DisplayName("LLM响应非JSON时降级返回sufficient=true")
    void grade_degradesToSufficient_whenResponseNonJSON() {
        stubResponse = "这不是JSON格式";

        ContextGradeResult result = grader.grade("查询", buildEvidences(3));

        assertThat(result.isSufficient()).isTrue();
    }

    @Test
    @DisplayName("evidences为空时返回insufficient(完整性)")
    void grade_returnsInsufficient_whenEvidencesEmpty() {
        ContextGradeResult result = grader.grade("查询", new ArrayList<>());

        assertThat(result.isSufficient()).isFalse();
        assertThat(result.getMissingDimensions()).contains("完整性");
    }

    @Test
    @DisplayName("evidences超过maxEvidences时截断")
    void grade_truncatesEvidences_whenExceedsMax() {
        ReflectionTestUtils.setField(grader, "maxEvidences", 3);
        stubResponse = "{\"sufficient\":true,\"confidence\":1.0,\"reason\":\"OK\"}";

        ContextGradeResult result = grader.grade("查询", buildEvidences(10));

        assertThat(result.isSufficient()).isTrue();
    }

    @Test
    @DisplayName("响应带markdown代码块标记时正确解析")
    void grade_handlesMarkdownCodeBlock() {
        stubResponse = "```json\n{\"sufficient\":true,\"confidence\":0.95,\"reason\":\"markdown测试\"}\n```";

        ContextGradeResult result = grader.grade("查询", buildEvidences(3));

        assertThat(result.isSufficient()).isTrue();
        // sufficient=true时confidence硬编码为1.0
        assertThat(result.getConfidence()).isEqualTo(1.0);
        assertThat(result.getReason()).isEqualTo("markdown测试");
    }
}
