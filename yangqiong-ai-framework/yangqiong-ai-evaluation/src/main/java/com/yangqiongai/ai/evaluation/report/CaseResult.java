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

/**
 * 单用例评测结果
 * @author yangqiong
 */
public class CaseResult {

    /**
     * 用例ID
     */
    private String caseId;

    /**
     * 查询输入
     */
    private String query;

    /**
     * 实际输出
     */
    private String actualOutput;

    /**
     * 期望输出
     */
    private String expectedOutput;

    /**
     * 评分
     */
    private double score;

    /**
     * 是否通过
     */
    private boolean passed;

    /**
     * 评分策略名称
     */
    private String strategyName;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 执行耗时(毫秒)
     */
    private long durationMs;

    public CaseResult() {
    }

    public CaseResult(String caseId, String query, String actualOutput, String expectedOutput,
                      double score, boolean passed, String strategyName, String errorMessage, long durationMs) {
        this.caseId = caseId;
        this.query = query;
        this.actualOutput = actualOutput;
        this.expectedOutput = expectedOutput;
        this.score = score;
        this.passed = passed;
        this.strategyName = strategyName;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
    }

    public String getCaseId() {
        return caseId;
    }

    public void setCaseId(String caseId) {
        this.caseId = caseId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getActualOutput() {
        return actualOutput;
    }

    public void setActualOutput(String actualOutput) {
        this.actualOutput = actualOutput;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public boolean isPassed() {
        return passed;
    }

    public void setPassed(boolean passed) {
        this.passed = passed;
    }

    public String getStrategyName() {
        return strategyName;
    }

    public void setStrategyName(String strategyName) {
        this.strategyName = strategyName;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(long durationMs) {
        this.durationMs = durationMs;
    }
}
