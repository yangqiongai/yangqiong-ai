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

import org.springframework.stereotype.Component;

/**
 * 精确匹配策略
 * @author yangqiong
 */
@Component
public class ExactMatchStrategy implements ScoringStrategy {

    /**
     * 获取策略名称
     * @return
     */
    @Override
    public String getStrategyName() {
        return "exact_match";
    }

    /**
     * 评分
     * @param actualOutput
     * @param expectedOutput
     * @return
     */
    @Override
    public double score(String actualOutput, String expectedOutput) {
        if (actualOutput == null || expectedOutput == null) {
            return 0.0;
        }
        return actualOutput.trim().equals(expectedOutput.trim()) ? 1.0 : 0.0;
    }
}
