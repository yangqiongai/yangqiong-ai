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
package com.yangqiongai.ai.platform.api.evaluation;

import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 评测报告对比测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class EvaluationReportServiceTest {

    @Mock
    private EvalRunRepository evalRunRepository;

    private EvaluationReportService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationReportService();
        try {
            java.lang.reflect.Field field = EvaluationReportService.class.getDeclaredField("evalRunRepository");
            field.setAccessible(true);
            field.set(service, evalRunRepository);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private EvalRunEntity run(Long id, double avgScore, int total, int passed, int failed) {
        EvalRunEntity entity = new EvalRunEntity();
        entity.setId(id);
        entity.setDatasetCode("recruit");
        entity.setStatus(id.equals(1L) ? EvaluationRunService.STATUS_PASSED : EvaluationRunService.STATUS_FAILED);
        entity.setAvgScore(BigDecimal.valueOf(avgScore));
        entity.setTotalCases(total);
        entity.setPassedCases(passed);
        entity.setFailedCases(failed);
        return entity;
    }

    private EvalRunCaseEntity runCase(String caseNo, String score, Integer passed, String expected) {
        EvalRunCaseEntity entity = new EvalRunCaseEntity();
        entity.setCaseNo(caseNo);
        entity.setScore(new BigDecimal(score));
        entity.setPassed(passed);
        entity.setExpectedOutput(expected);
        return entity;
    }

    @Test
    void 对比运行不存在时拒绝() {
        when(evalRunRepository.findRunById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.compare(1L, 2L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("评测运行不存在");
    }

    @Test
    void 整体指标diff按右减左计算() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, 82.5, 10, 8, 2));
        when(evalRunRepository.findRunById(2L)).thenReturn(run(2L, 88.0, 12, 11, 1));
        when(evalRunRepository.findRunCases(1L)).thenReturn(List.of());
        when(evalRunRepository.findRunCases(2L)).thenReturn(List.of());

        Map<String, Object> result = service.compare(1L, 2L);
        @SuppressWarnings("unchecked")
        Map<String, Object> diff = (Map<String, Object>) result.get("summaryDiff");

        assertThat(diff.get("avgScoreDelta")).isEqualTo(new BigDecimal("5.5000"));
        assertThat(diff.get("passedCasesDelta")).isEqualTo(3);
        assertThat(diff.get("failedCasesDelta")).isEqualTo(-1);
        assertThat(diff.get("statusLeft")).isEqualTo(EvaluationRunService.STATUS_PASSED);
        assertThat(diff.get("statusRight")).isEqualTo(EvaluationRunService.STATUS_FAILED);
    }

    @Test
    void 双侧共有用例计算分数与通过变化() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, 80.0, 2, 1, 1));
        when(evalRunRepository.findRunById(2L)).thenReturn(run(2L, 90.0, 2, 2, 0));
        when(evalRunRepository.findRunCases(1L)).thenReturn(List.of(
                runCase("c1", "70.0", 0, "期望A"),
                runCase("c2", "90.0", 1, "期望B")));
        when(evalRunRepository.findRunCases(2L)).thenReturn(List.of(
                runCase("c1", "100.0", 1, "期望A"),
                runCase("c2", "80.0", 1, "期望B")));

        Map<String, Object> result = service.compare(1L, 2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) result.get("caseMatrix");

        assertThat(matrix).hasSize(2);
        Map<String, Object> c1 = matrix.get(0);
        assertThat(c1.get("caseNo")).isEqualTo("c1");
        assertThat(c1.get("presentLeft")).isEqualTo(true);
        assertThat(c1.get("presentRight")).isEqualTo(true);
        assertThat(c1.get("scoreDelta")).isEqualTo(new BigDecimal("30.0000"));
        assertThat(c1.get("passChanged")).isEqualTo(true);
        assertThat(c1.get("expectedOutput")).isEqualTo("期望A");
    }

    @Test
    void 仅左侧存在的用例标注缺失() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, 80.0, 1, 1, 0));
        when(evalRunRepository.findRunById(2L)).thenReturn(run(2L, 90.0, 1, 1, 0));
        when(evalRunRepository.findRunCases(1L)).thenReturn(List.of(runCase("c9", "60.0", 0, "仅左")));
        when(evalRunRepository.findRunCases(2L)).thenReturn(List.of(runCase("c1", "90.0", 1, "共有")));

        Map<String, Object> result = service.compare(1L, 2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) result.get("caseMatrix");

        assertThat(matrix).hasSize(2);
        Map<String, Object> c9 = matrix.stream()
                .filter(row -> "c9".equals(row.get("caseNo"))).findFirst().orElseThrow();
        assertThat(c9.get("presentLeft")).isEqualTo(true);
        assertThat(c9.get("presentRight")).isEqualTo(false);
        assertThat(c9.get("scoreDelta")).isNull();
        assertThat(c9.get("passChanged")).isNull();
    }

    @Test
    void 仅右侧存在的用例追加到矩阵尾部() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, 80.0, 1, 1, 0));
        when(evalRunRepository.findRunById(2L)).thenReturn(run(2L, 90.0, 1, 1, 0));
        when(evalRunRepository.findRunCases(1L)).thenReturn(List.of());
        when(evalRunRepository.findRunCases(2L)).thenReturn(List.of(runCase("c8", "95.0", 1, "仅右")));

        Map<String, Object> result = service.compare(1L, 2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) result.get("caseMatrix");

        assertThat(matrix).hasSize(1);
        assertThat(matrix.get(0).get("caseNo")).isEqualTo("c8");
        assertThat(matrix.get(0).get("presentLeft")).isEqualTo(false);
        assertThat(matrix.get(0).get("presentRight")).isEqualTo(true);
        assertThat(matrix.get(0).get("expectedOutput")).isEqualTo("仅右");
    }

    @Test
    void 用例矩阵按case_no排序对齐() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, 80.0, 2, 2, 0));
        when(evalRunRepository.findRunById(2L)).thenReturn(run(2L, 90.0, 2, 2, 0));
        when(evalRunRepository.findRunCases(1L)).thenReturn(List.of(
                runCase("c2", "80.0", 1, null), runCase("c1", "80.0", 1, null)));
        when(evalRunRepository.findRunCases(2L)).thenReturn(List.of(
                runCase("c1", "90.0", 1, null), runCase("c2", "90.0", 1, null)));

        Map<String, Object> result = service.compare(1L, 2L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> matrix = (List<Map<String, Object>>) result.get("caseMatrix");

        assertThat(matrix).extracting(row -> row.get("caseNo")).containsExactly("c1", "c2");
    }

    @Test
    void 空明细对比返回空矩阵() {
        when(evalRunRepository.findRunById(1L)).thenReturn(run(1L, 80.0, 0, 0, 0));
        when(evalRunRepository.findRunById(2L)).thenReturn(run(2L, 90.0, 0, 0, 0));
        when(evalRunRepository.findRunCases(1L)).thenReturn(List.of());
        when(evalRunRepository.findRunCases(2L)).thenReturn(List.of());

        Map<String, Object> result = service.compare(1L, 2L);

        assertThat((List<?>) result.get("caseMatrix")).isEmpty();
    }
}
