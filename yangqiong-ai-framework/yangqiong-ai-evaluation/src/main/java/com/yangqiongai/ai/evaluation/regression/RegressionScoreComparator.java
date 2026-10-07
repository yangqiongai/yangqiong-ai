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

/**
 * 回归环比下降判定
 * <p>
 * 比较昨日与今日评测指标（通过率/平均分），下降幅度严格大于阈值才判定退化，
 * 恰好等于阈值不触发；任一侧无前值不判定（首日无环比）。
 * </p>
 * @author yangqiong
 */
public final class RegressionScoreComparator {

    private RegressionScoreComparator() {
    }

    /**
     * 判定是否环比下降超阈值
     * @param prev 前一次指标(0-1)
     * @param curr 当前指标(0-1)
     * @param threshold 下降阈值(0-1)
     * @return true=退化
     */
    public static boolean isDegraded(BigDecimal prev, BigDecimal curr, double threshold) {
        if (prev == null || curr == null) {
            return false;
        }
        double drop = prev.doubleValue() - curr.doubleValue();
        return drop > threshold;
    }
}
