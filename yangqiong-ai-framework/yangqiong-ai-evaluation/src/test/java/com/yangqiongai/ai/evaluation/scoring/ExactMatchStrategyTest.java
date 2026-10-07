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

@DisplayName("ExactMatchStrategy 单元测试")
class ExactMatchStrategyTest {

    private ExactMatchStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ExactMatchStrategy();
    }

    @Test
    @DisplayName("score: 精确匹配返回1.0")
    void score_exactMatch_returns1() {
        assertThat(strategy.score("hello", "hello")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("score: 不同内容返回0.0")
    void score_different_returns0() {
        assertThat(strategy.score("hello", "world")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: 大小写不同返回0.0")
    void score_caseDifference_returns0() {
        assertThat(strategy.score("Hello", "hello")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: 空白差异（trim后匹配）返回0.0")
    void score_whitespaceDifference_returns0() {
        assertThat(strategy.score("hello ", "hello")).isEqualTo(1.0);
        assertThat(strategy.score(" hello", "hello")).isEqualTo(1.0);
        assertThat(strategy.score("hello world", "hello world ")).isEqualTo(1.0);
        assertThat(strategy.score("hello world", "helloworld")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: null参数返回0.0")
    void score_null_returns0() {
        assertThat(strategy.score(null, "hello")).isEqualTo(0.0);
        assertThat(strategy.score("hello", null)).isEqualTo(0.0);
        assertThat(strategy.score(null, null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getStrategyName: 返回 'exact_match'")
    void getStrategyName_returnsExactMatch() {
        assertThat(strategy.getStrategyName()).isEqualTo("exact_match");
    }
}
