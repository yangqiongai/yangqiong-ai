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

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测报告
 * @author yangqiong
 */
public class EvaluationReport {

    /**
     * 数据集ID
     */
    private String datasetId;

    /**
     * 数据集名称
     */
    private String datasetName;

    /**
     * 总用例数
     */
    private int totalCases;

    /**
     * 通过用例数
     */
    private int passedCases;

    /**
     * 失败用例数
     */
    private int failedCases;

    /**
     * 平均分数
     */
    private double averageScore;

    /**
     * 整体是否通过
     */
    private boolean overallPassed;

    /**
     * 用例结果列表
     */
    private List<CaseResult> results;

    /**
     * 评测时间
     */
    private LocalDateTime evaluatedAt;

    /**
     * 总耗时(毫秒)
     */
    private long durationMs;

    public EvaluationReport() {
    }

    public EvaluationReport(String datasetId, String datasetName, int totalCases, int passedCases,
                            int failedCases, double averageScore, boolean overallPassed,
                            List<CaseResult> results, LocalDateTime evaluatedAt, long durationMs) {
        this.datasetId = datasetId;
        this.datasetName = datasetName;
        this.totalCases = totalCases;
        this.passedCases = passedCases;
        this.failedCases = failedCases;
        this.averageScore = averageScore;
        this.overallPassed = overallPassed;
        this.results = results;
        this.evaluatedAt = evaluatedAt;
        this.durationMs = durationMs;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getDatasetName() {
        return datasetName;
    }

    public void setDatasetName(String datasetName) {
        this.datasetName = datasetName;
    }

    public int getTotalCases() {
        return totalCases;
    }

    public void setTotalCases(int totalCases) {
        this.totalCases = totalCases;
    }

    public int getPassedCases() {
        return passedCases;
    }

    public void setPassedCases(int passedCases) {
        this.passedCases = passedCases;
    }

    public int getFailedCases() {
        return failedCases;
    }

    public void setFailedCases(int failedCases) {
        this.failedCases = failedCases;
    }

    public double getAverageScore() {
        return averageScore;
    }

    public void setAverageScore(double averageScore) {
        this.averageScore = averageScore;
    }

    public boolean isOverallPassed() {
        return overallPassed;
    }

    public void setOverallPassed(boolean overallPassed) {
        this.overallPassed = overallPassed;
    }

    public List<CaseResult> getResults() {
        return results;
    }

    public void setResults(List<CaseResult> results) {
        this.results = results;
    }

    public LocalDateTime getEvaluatedAt() {
        return evaluatedAt;
    }

    public void setEvaluatedAt(LocalDateTime evaluatedAt) {
        this.evaluatedAt = evaluatedAt;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
