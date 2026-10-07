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
package com.yangqiongai.ai.rag.agent;

import com.yangqiongai.ai.rag.config.RagProperties;
import com.yangqiongai.ai.rag.grader.ContextGradeResult;
import com.yangqiongai.ai.rag.grader.ContextGraderService;
import com.yangqiongai.ai.rag.model.RetrievalEvidence;
import com.yangqiongai.ai.rag.RagRetrieveService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RetrievalAgent 单元测试
 * @author yangqiong
 */
@DisplayName("RetrievalAgent 单元测试")
class RetrievalAgentTest {

    private RetrievalAgent agent;

    private RagProperties ragProperties;

    private List<RetrievalEvidence> delegateEvidences;

    private ContextGradeResult gradeResult;

    private AtomicReference<Integer> capturedTopK;

    @BeforeEach
    void setUp() {
        agent = new RetrievalAgent();
        ragProperties = new RagProperties();
        ReflectionTestUtils.setField(agent, "ragProperties", ragProperties);
        delegateEvidences = buildEvidences(3);
        capturedTopK = new AtomicReference<>();
        ReflectionTestUtils.setField(agent, "delegate", createDefaultDelegate());
        gradeResult = ContextGradeResult.sufficient("默认充分");
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) -> gradeResult);
    }

    /**
     * 默认委托实现：返回固定证据,记录topK
     */
    private RagRetrieveService createDefaultDelegate() {
        return new RagRetrieveService() {
            @Override
            public List<RetrievalEvidence> retrieve(String query, List<String> kbIds,
                                                     List<String> docIds, int topK) {
                capturedTopK.set(topK);
                return new ArrayList<>(delegateEvidences);
            }

            @Override
            public List<RetrievalEvidence> retrieveHybrid(String query, List<String> kbIds,
                                                            List<String> docIds, int topK) {
                capturedTopK.set(topK);
                return new ArrayList<>(delegateEvidences);
            }

            @Override
            public String expandContext(String docId, String query, int maxChars) {
                return "";
            }

            @Override
            public List<com.yangqiongai.ai.rag.model.RecallResult> verifyRecall(com.yangqiongai.ai.rag.model.RecallRequest request) {
                return List.of();
            }
        };
    }

    private List<RetrievalEvidence> buildEvidences(int count) {
        List<RetrievalEvidence> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            list.add(buildEvidence("slice-" + i, "doc-" + i, 0.8 - i * 0.05));
        }
        return list;
    }

    private RetrievalEvidence buildEvidence(String sliceId, String docId, double score) {
        RetrievalEvidence evidence = new RetrievalEvidence();
        evidence.setContent("内容-" + sliceId);
        evidence.setSourceDocId(docId);
        evidence.setSliceId(sliceId);
        evidence.setScore(score);
        return evidence;
    }

    @Test
    @DisplayName("ContextGraderService未注入时直接返回原始evidences")
    void retrieve_returnsEvidences_whenContextGraderNull() {
        ReflectionTestUtils.setField(agent, "contextGraderService", null);

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("contextGrade.enabled=false时直接返回原始evidences")
    void retrieve_returnsEvidences_whenContextGradeDisabled() {
        ragProperties.getContextGrade().setEnabled(false);

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("grader返回sufficient=true时不触发重检索")
    void retrieve_returnsEvidences_whenGradeSufficient() {
        ragProperties.getContextGrade().setEnabled(true);
        gradeResult = ContextGradeResult.sufficient("充分");

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("grader返回sufficient=false时触发重检索")
    void retrieve_triggersLoop_whenGradeInsufficient() {
        ragProperties.getContextGrade().setEnabled(true);
        ragProperties.getContextGrade().setMaxRetries(2);
        AtomicInteger gradeCallCount = new AtomicInteger(0);
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) -> {
                    if (gradeCallCount.incrementAndGet() == 1) {
                        return ContextGradeResult.insufficient(List.of("完整性"), 0.3, "不充分");
                    }
                    return ContextGradeResult.sufficient("重检索后充分");
                });

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).isNotNull();
        assertThat(gradeCallCount.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("阶段1扩大topK（×3倍）")
    void retrieve_expandsTopKOnRetry1() {
        ragProperties.getContextGrade().setEnabled(true);
        ragProperties.getContextGrade().setMaxRetries(2);
        AtomicInteger gradeCallCount = new AtomicInteger(0);
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) -> {
                    if (gradeCallCount.incrementAndGet() == 1) {
                        return ContextGradeResult.insufficient(List.of("完整性"), 0.3, "不充分");
                    }
                    return ContextGradeResult.sufficient("扩大topK后充分");
                });

        agent.retrieve("查询", List.of("kb-1"), null, 5);

        // 阶段1使用retrieveHybrid,topK扩大到5*3=15
        assertThat(capturedTopK.get()).isEqualTo(15);
    }

    @Test
    @DisplayName("阶段2路由升级（VECTOR_ONLY→HYBRID）")
    void retrieve_upgradesRouteOnRetry2() {
        ragProperties.getContextGrade().setEnabled(true);
        ragProperties.getContextGrade().setMaxRetries(2);
        AtomicInteger gradeCallCount = new AtomicInteger(0);
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) -> {
                    int call = gradeCallCount.incrementAndGet();
                    if (call <= 2) {
                        return ContextGradeResult.insufficient(List.of("完整性"), 0.3, "仍不充分");
                    }
                    return ContextGradeResult.sufficient("路由升级后充分");
                });

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).isNotNull();
        assertThat(gradeCallCount.get()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("达到maxRetries后返回最佳证据")
    void retrieve_returnsBestAfterMaxRetries() {
        ragProperties.getContextGrade().setEnabled(true);
        ragProperties.getContextGrade().setMaxRetries(2);
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) ->
                        ContextGradeResult.insufficient(List.of("完整性"), 0.1, "始终不充分"));

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("grader异常时降级返回原始evidences")
    void retrieve_degradesWhenGraderThrows() {
        ragProperties.getContextGrade().setEnabled(true);
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) -> {
                    throw new RuntimeException("grader调用失败");
                });

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("合并去重按sliceId去重")
    void retrieve_mergesAndDedupesBySliceId() {
        ragProperties.getContextGrade().setEnabled(true);
        ragProperties.getContextGrade().setMaxRetries(2);
        // 委托返回带重复sliceId-1的证据
        ReflectionTestUtils.setField(agent, "delegate", new RagRetrieveService() {
            @Override
            public List<RetrievalEvidence> retrieve(String query, List<String> kbIds,
                                                     List<String> docIds, int topK) {
                List<RetrievalEvidence> list = new ArrayList<>();
                list.add(buildEvidence("slice-1", "doc-1", 0.85));
                list.add(buildEvidence("slice-2", "doc-2", 0.7));
                return list;
            }

            @Override
            public List<RetrievalEvidence> retrieveHybrid(String query, List<String> kbIds,
                                                            List<String> docIds, int topK) {
                List<RetrievalEvidence> list = new ArrayList<>();
                // slice-1重复,应被去重
                list.add(buildEvidence("slice-1", "doc-1", 0.9));
                list.add(buildEvidence("slice-3", "doc-3", 0.6));
                return list;
            }

            @Override
            public String expandContext(String docId, String query, int maxChars) {
                return "";
            }

            @Override
            public List<com.yangqiongai.ai.rag.model.RecallResult> verifyRecall(com.yangqiongai.ai.rag.model.RecallRequest request) {
                return List.of();
            }
        });
        AtomicInteger gradeCallCount = new AtomicInteger(0);
        ReflectionTestUtils.setField(agent, "contextGraderService",
                (ContextGraderService) (query, evidences) -> {
                    if (gradeCallCount.incrementAndGet() == 1) {
                        return ContextGradeResult.insufficient(List.of("完整性"), 0.3, "不充分");
                    }
                    return ContextGradeResult.sufficient("合并后充分");
                });

        List<RetrievalEvidence> result = agent.retrieve("查询", List.of("kb-1"), null, 5);

        // slice-1应只出现一次
        long slice1Count = result.stream().filter(e -> "slice-1".equals(e.getSliceId())).count();
        assertThat(slice1Count).isEqualTo(1);
    }
}
