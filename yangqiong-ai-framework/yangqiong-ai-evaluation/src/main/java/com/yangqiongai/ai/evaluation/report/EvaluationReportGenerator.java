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
package com.yangqiongai.ai.evaluation.report;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测报告生成器
 * @author yangqiong
 */
@Component
public class EvaluationReportGenerator {

    /**
     * 根据结果列表生成报告
     * @param datasetId
     * @param datasetName
     * @param results
     * @param totalDurationMs
     * @return
     */
    public EvaluationReport generate(String datasetId, String datasetName,
                                     List<CaseResult> results, long totalDurationMs) {
        int totalCases = results.size();
        int passedCases = (int) results.stream().filter(CaseResult::isPassed).count();
        int failedCases = totalCases - passedCases;
        double averageScore = results.stream()
                .mapToDouble(CaseResult::getScore)
                .average()
                .orElse(0.0);
        boolean overallPassed = averageScore >= 0.6;

        return new EvaluationReport(
                datasetId,
                datasetName,
                totalCases,
                passedCases,
                failedCases,
                averageScore,
                overallPassed,
                results,
                LocalDateTime.now(),
                totalDurationMs
        );
    }
}
