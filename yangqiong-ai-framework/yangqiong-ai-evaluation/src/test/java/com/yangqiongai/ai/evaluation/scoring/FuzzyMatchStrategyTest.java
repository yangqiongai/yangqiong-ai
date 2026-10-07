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
package com.yangqiongai.ai.evaluation.scoring;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("FuzzyMatchStrategy 单元测试")
class FuzzyMatchStrategyTest {

    private FuzzyMatchStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new FuzzyMatchStrategy();
    }

    @Test
    @DisplayName("score: 完全相同返回1.0")
    void score_identical_returns1() {
        assertThat(strategy.score("hello world", "hello world")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("score: 部分匹配返回正确的LCS比率")
    void score_partialMatch_returnsCorrectLcsRatio() {
        // "abc" vs "adc" → LCS = "ac" (长度2), maxLen = 3 → 2/3
        double score = strategy.score("abc", "adc");
        assertThat(score).isCloseTo(2.0 / 3.0, within(0.001));

        // "kitten" vs "sitting" → LCS = "ittn" (长度4), maxLen = 7 → 4/7
        double score2 = strategy.score("kitten", "sitting");
        assertThat(score2).isCloseTo(4.0 / 7.0, within(0.001));
    }

    @Test
    @DisplayName("score: 完全不同返回0.0")
    void score_completelyDifferent_returns0() {
        assertThat(strategy.score("abc", "xyz")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: null参数返回0.0")
    void score_null_returns0() {
        assertThat(strategy.score(null, "hello")).isEqualTo(0.0);
        assertThat(strategy.score("hello", null)).isEqualTo(0.0);
        assertThat(strategy.score(null, null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: 两个空字符串返回1.0")
    void score_emptyStrings_returns1() {
        assertThat(strategy.score("", "")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("getStrategyName: 返回 'fuzzy_match'")
    void getStrategyName_returnsFuzzyMatch() {
        assertThat(strategy.getStrategyName()).isEqualTo("fuzzy_match");
    }
}
