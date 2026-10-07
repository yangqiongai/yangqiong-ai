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
package com.yangqiongai.ai.evaluation.engine;

import com.yangqiongai.ai.evaluation.report.CaseResult;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import com.yangqiongai.ai.evaluation.report.EvaluationReportGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("EvaluationReportGenerator 单元测试")
class EvaluationReportGeneratorTest {

    private EvaluationReportGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new EvaluationReportGenerator();
    }

    @Test
    @DisplayName("generate: 所有用例通过 → 整体通过")
    void generate_allPass_overallPass() {
        List<CaseResult> results = Arrays.asList(
                createCaseResult(1.0, true),
                createCaseResult(0.8, true),
                createCaseResult(0.9, true)
        );

        EvaluationReport report = generator.generate("ds-1", "test-dataset", results, 1000L);

        assertThat(report.isOverallPassed()).isTrue();
    }

    @Test
    @DisplayName("generate: 低于阈值 → 整体不通过")
    void generate_belowThreshold_overallFail() {
        List<CaseResult> results = Arrays.asList(
                createCaseResult(0.2, false),
                createCaseResult(0.3, false),
                createCaseResult(0.1, false)
        );

        EvaluationReport report = generator.generate("ds-1", "test-dataset", results, 1000L);

        assertThat(report.isOverallPassed()).isFalse();
    }

    @Test
    @DisplayName("generate: 正确计算平均分数")
    void generate_calculatesAverageScore() {
        List<CaseResult> results = Arrays.asList(
                createCaseResult(0.8, true),
                createCaseResult(0.6, true),
                createCaseResult(1.0, true)
        );

        EvaluationReport report = generator.generate("ds-1", "test-dataset", results, 1000L);

        assertThat(report.getAverageScore()).isCloseTo(0.8, within(0.001));
    }

    @Test
    @DisplayName("generate: 正确统计通过/失败数量")
    void generate_countsPassFailCorrectly() {
        List<CaseResult> results = Arrays.asList(
                createCaseResult(0.8, true),
                createCaseResult(0.3, false),
                createCaseResult(1.0, true),
                createCaseResult(0.1, false)
        );

        EvaluationReport report = generator.generate("ds-1", "test-dataset", results, 500L);

        assertThat(report.getTotalCases()).isEqualTo(4);
        assertThat(report.getPassedCases()).isEqualTo(2);
        assertThat(report.getFailedCases()).isEqualTo(2);
    }

    @Test
    @DisplayName("generate: 空结果列表 → 平均0.0，整体不通过")
    void generate_emptyResults_returnsZeroScore() {
        EvaluationReport report = generator.generate("ds-1", "test-dataset", Collections.emptyList(), 0L);

        assertThat(report.getTotalCases()).isEqualTo(0);
        assertThat(report.getAverageScore()).isEqualTo(0.0);
        assertThat(report.isOverallPassed()).isFalse();
    }

    private CaseResult createCaseResult(double score, boolean passed) {
        return new CaseResult("case-1", "query", "actual", "expected", score, passed, "exact_match", null, 100L);
    }
}
