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
 * 模糊匹配策略
 * @author yangqiong
 */
@Component
public class FuzzyMatchStrategy implements ScoringStrategy {

    /**
     * 获取策略名称
     * @return
     */
    @Override
    public String getStrategyName() {
        return "fuzzy_match";
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
        int lcsLength = longestCommonSubsequence(actualOutput, expectedOutput);
        int maxLen = Math.max(actualOutput.length(), expectedOutput.length());
        if (maxLen == 0) {
            return 1.0;
        }
        return (double) lcsLength / maxLen;
    }

    private int longestCommonSubsequence(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        int[][] dp = new int[m + 1][n + 1];
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        return dp[m][n];
    }
}
