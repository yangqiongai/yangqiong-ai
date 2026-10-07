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
package com.yangqiongai.ai.evaluation.regression;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 评测回归退化事件
 * <p>
 * 回归评测环比下降超阈值时发布，社区版日志、企业版由告警通道消费。
 * </p>
 * @author yangqiong
 */
public class EvalRegressionDegradedEvent {

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 回归日期
     */
    private final LocalDate runDate;

    /**
     * 关联的评测运行ID
     */
    private final Long evalRunId;

    /**
     * 当日通过率
     */
    private final BigDecimal passRate;

    /**
     * 当日平均分
     */
    private final BigDecimal avgScore;

    /**
     * 前一日通过率
     */
    private final BigDecimal prevPassRate;

    /**
     * 前一日平均分
     */
    private final BigDecimal prevAvgScore;

    public EvalRegressionDegradedEvent(String scopeId, String agentCode, LocalDate runDate, Long evalRunId,
                                       BigDecimal passRate, BigDecimal avgScore,
                                       BigDecimal prevPassRate, BigDecimal prevAvgScore) {
        this.scopeId = scopeId;
        this.agentCode = agentCode;
        this.runDate = runDate;
        this.evalRunId = evalRunId;
        this.passRate = passRate;
        this.avgScore = avgScore;
        this.prevPassRate = prevPassRate;
        this.prevAvgScore = prevAvgScore;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public LocalDate getRunDate() {
        return runDate;
    }

    public Long getEvalRunId() {
        return evalRunId;
    }

    public BigDecimal getPassRate() {
        return passRate;
    }

    public BigDecimal getAvgScore() {
        return avgScore;
    }

    public BigDecimal getPrevPassRate() {
        return prevPassRate;
    }

    public BigDecimal getPrevAvgScore() {
        return prevAvgScore;
    }
}
