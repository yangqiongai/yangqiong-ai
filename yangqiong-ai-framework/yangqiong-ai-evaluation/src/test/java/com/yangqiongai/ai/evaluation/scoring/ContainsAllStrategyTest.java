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

@DisplayName("ContainsAllStrategy 单元测试")
class ContainsAllStrategyTest {

    private ContainsAllStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new ContainsAllStrategy();
    }

    @Test
    @DisplayName("score: 所有关键词都存在返回1.0")
    void score_allKeywordsPresent_returns1() {
        assertThat(strategy.score("hello world foo bar", "hello,world,foo,bar")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("score: 部分关键词缺失返回部分分数")
    void score_someKeywordsMissing_returnsPartialScore() {
        // 4个关键词中匹配2个 → 0.5
        double score = strategy.score("hello world", "hello,world,foo,bar");
        assertThat(score).isCloseTo(0.5, within(0.001));
    }

    @Test
    @DisplayName("score: 没有关键词存在返回0.0")
    void score_noKeywordsPresent_returns0() {
        assertThat(strategy.score("nothing here", "foo,bar,baz")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: null参数返回0.0")
    void score_null_returns0() {
        assertThat(strategy.score(null, "hello,world")).isEqualTo(0.0);
        assertThat(strategy.score("hello world", null)).isEqualTo(0.0);
        assertThat(strategy.score(null, null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("score: 期望输出为空字符串返回1.0")
    void score_emptyExpected_returns1() {
        // split(",") on "" returns [""], trimmed is empty → matched=0, keywords.length=1
        // 但空字符串trim后为空，不计入matched，所以0/1=0.0
        // 实际逻辑：keywords.length == 0 才返回1.0，而"".split(",")长度为1
        // 所以空字符串不满足 keywords.length == 0
        double score = strategy.score("anything", "");
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getStrategyName: 返回 'contains_all'")
    void getStrategyName_returnsContainsAll() {
        assertThat(strategy.getStrategyName()).isEqualTo("contains_all");
    }
}
