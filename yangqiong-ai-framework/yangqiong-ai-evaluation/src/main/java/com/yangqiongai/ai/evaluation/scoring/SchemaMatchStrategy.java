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

import com.yangqiongai.ai.common.util.AgentOutputSchemaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Schema结构合规评分策略
 * <p>
 * expectedOutput存放JSON Schema字符串，actualOutput为待评测的模型输出。
 * 硬校验结构合规，合规得1.0分，不合规得0.0分。
 * 用于发布门禁场景，展示Schema合规项。
 * </p>
 * @author yangqiong
 */
@Component
public class SchemaMatchStrategy implements ScoringStrategy {

    private static final Logger log = LoggerFactory.getLogger(SchemaMatchStrategy.class);

    /**
     * 获取策略名称
     * @return
     */
    @Override
    public String getStrategyName() {
        return "schema_match";
    }

    /**
     * 评分
     * @param actualOutput 模型输出
     * @param expectedOutput JSON Schema字符串
     * @return
     */
    @Override
    public double score(String actualOutput, String expectedOutput) {
        if (expectedOutput == null || expectedOutput.isBlank()) {
            log.warn("SchemaMatchStrategy缺少expected Schema，计0分");
            return 0.0;
        }
        try {
            List<String> errors = AgentOutputSchemaValidator.validateOutput(expectedOutput, actualOutput);
            return errors.isEmpty() ? 1.0 : 0.0;
        } catch (Exception e) {
            log.warn("SchemaMatchStrategy校验异常，计0分: {}", e.getMessage());
            return 0.0;
        }
    }
}
