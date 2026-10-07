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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归环比下降判定测试
 * @author yangqiong
 */
@DisplayName("RegressionScoreComparator 单元测试")
class RegressionScoreComparatorTest {

    @Test
    @DisplayName("下降超阈值判定退化")
    void dropBeyondThreshold() {
        assertThat(RegressionScoreComparator.isDegraded(
                new BigDecimal("0.9000"), new BigDecimal("0.7500"), 0.1)).isTrue();
    }

    @Test
    @DisplayName("下降恰好等于阈值不触发")
    void dropExactlyAtThreshold() {
        assertThat(RegressionScoreComparator.isDegraded(
                new BigDecimal("0.9000"), new BigDecimal("0.8000"), 0.1)).isFalse();
    }

    @Test
    @DisplayName("下降小于阈值不触发")
    void dropBelowThreshold() {
        assertThat(RegressionScoreComparator.isDegraded(
                new BigDecimal("0.8500"), new BigDecimal("0.8000"), 0.1)).isFalse();
    }

    @Test
    @DisplayName("指标上升不触发")
    void improvedNotDegraded() {
        assertThat(RegressionScoreComparator.isDegraded(
                new BigDecimal("0.7000"), new BigDecimal("0.9500"), 0.1)).isFalse();
    }

    @Test
    @DisplayName("前值为空(首日)不判定")
    void prevNullNotDegraded() {
        assertThat(RegressionScoreComparator.isDegraded(null, new BigDecimal("0.1000"), 0.1)).isFalse();
    }

    @Test
    @DisplayName("当前值为空不判定")
    void currNullNotDegraded() {
        assertThat(RegressionScoreComparator.isDegraded(new BigDecimal("0.9000"), null, 0.1)).isFalse();
    }

    @Test
    @DisplayName("双侧均为空不判定")
    void bothNullNotDegraded() {
        assertThat(RegressionScoreComparator.isDegraded(null, null, 0.1)).isFalse();
    }

    @Test
    @DisplayName("零阈值时任何下降都触发")
    void zeroThresholdAnyDropTriggers() {
        assertThat(RegressionScoreComparator.isDegraded(
                new BigDecimal("0.8000"), new BigDecimal("0.7999"), 0.0)).isTrue();
        assertThat(RegressionScoreComparator.isDegraded(
                new BigDecimal("0.8000"), new BigDecimal("0.8000"), 0.0)).isFalse();
    }
}
