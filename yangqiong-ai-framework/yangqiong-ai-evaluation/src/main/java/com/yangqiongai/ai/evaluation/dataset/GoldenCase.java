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
package com.yangqiongai.ai.evaluation.dataset;

import java.util.Map;

/**
 * 评测用例
 * @author yangqiong
 */
public class GoldenCase {

    /**
     * 用例ID
     */
    private String caseId;

    /**
     * 查询输入
     */
    private String query;

    /**
     * 期望输出
     */
    private String expectedOutput;

    /**
     * 评分策略名称
     */
    private String scoringCriteria;

    /**
     * 请求数据体
     */
    private Map<String, Object> body;

    public GoldenCase() {
    }

    public GoldenCase(String caseId, String query, String expectedOutput, String scoringCriteria, Map<String, Object> body) {
        this.caseId = caseId;
        this.query = query;
        this.expectedOutput = expectedOutput;
        this.scoringCriteria = scoringCriteria;
        this.body = body;
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

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public void setExpectedOutput(String expectedOutput) {
        this.expectedOutput = expectedOutput;
    }

    public String getScoringCriteria() {
        return scoringCriteria;
    }

    public void setScoringCriteria(String scoringCriteria) {
        this.scoringCriteria = scoringCriteria;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setMetadata(Map<String, Object> body) {
        this.body = body;
    }
}
