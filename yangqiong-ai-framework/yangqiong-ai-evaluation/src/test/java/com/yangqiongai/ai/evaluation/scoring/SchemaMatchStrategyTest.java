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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Schema结构合规评分策略测试
 * @author yangqiong
 */
@DisplayName("SchemaMatchStrategy 单元测试")
class SchemaMatchStrategyTest {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "title": {"type": "string"},
                "priority": {"type": "integer"}
              },
              "required": ["title"]
            }
            """;

    private final SchemaMatchStrategy strategy = new SchemaMatchStrategy();

    @Test
    @DisplayName("策略名称为schema_match")
    void strategyName() {
        assertThat(strategy.getStrategyName()).isEqualTo("schema_match");
    }

    @Test
    @DisplayName("合规输出得满分")
    void validOutputScoresFull() {
        double score = strategy.score("{\"title\": \"任务\", \"priority\": 3}", SCHEMA);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("缺少必填字段得零分")
    void missingRequiredScoresZero() {
        double score = strategy.score("{\"priority\": 3}", SCHEMA);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("类型错误得零分")
    void typeMismatchScoresZero() {
        double score = strategy.score("{\"title\": 123}", SCHEMA);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("额外字段默认允许")
    void additionalFieldsPass() {
        double score = strategy.score("{\"title\": \"任务\", \"extra\": true}", SCHEMA);
        assertThat(score).isEqualTo(1.0);
    }

    @Test
    @DisplayName("非JSON输出得零分")
    void nonJsonOutputScoresZero() {
        double score = strategy.score("普通文本回答", SCHEMA);
        assertThat(score).isEqualTo(0.0);
    }

    @Test
    @DisplayName("markdown包裹的JSON输出参与校验")
    void fencedJsonOutputValidated() {
        double pass = strategy.score("```json\n{\"title\": \"任务\"}\n```", SCHEMA);
        double fail = strategy.score("```json\n{\"priority\": 1}\n```", SCHEMA);
        assertThat(pass).isEqualTo(1.0);
        assertThat(fail).isEqualTo(0.0);
    }

    @Test
    @DisplayName("expected为空容错计零分不抛异常")
    void blankExpectedTolerated() {
        assertThat(strategy.score("{\"title\": \"任务\"}", null)).isEqualTo(0.0);
        assertThat(strategy.score("{\"title\": \"任务\"}", "")).isEqualTo(0.0);
        assertThat(strategy.score("{\"title\": \"任务\"}", "   ")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("expected非Schema容错计零分不抛异常")
    void invalidSchemaTolerated() {
        assertThatCode(() -> strategy.score("{\"title\": \"任务\"}", "not a schema")).doesNotThrowAnyException();
        assertThat(strategy.score("{\"title\": \"任务\"}", "not a schema")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("actual为空容错计零分")
    void blankActualTolerated() {
        assertThat(strategy.score(null, SCHEMA)).isEqualTo(0.0);
        assertThat(strategy.score("", SCHEMA)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("边界类型-null与空对象输出")
    void boundaryOutputTypes() {
        assertThat(strategy.score("null", SCHEMA)).isEqualTo(0.0);
        assertThat(strategy.score("{}", SCHEMA)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("布尔与数组类型Schema边界")
    void booleanAndArraySchema() {
        assertThat(strategy.score("true", "{\"type\": \"boolean\"}")).isEqualTo(1.0);
        assertThat(strategy.score("1", "{\"type\": \"boolean\"}")).isEqualTo(0.0);
        assertThat(strategy.score("[1,2,3]", "{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}")).isEqualTo(1.0);
        assertThat(strategy.score("[1,\"a\"]", "{\"type\": \"array\", \"items\": {\"type\": \"integer\"}}")).isEqualTo(0.0);
    }
}
