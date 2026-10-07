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
package com.yangqiongai.ai.platform.api.governance.dashboard;

import java.math.BigDecimal;
import java.util.List;

/**
 * 趋势地平线数据（近7天运行量与成本）
 * @author yangqiong
 */
public class GovernanceTrendSeries {

    /**
     * 近7天逐日运行量(下标0=6天前，下标6=今天)
     */
    private List<Long> runs7d;

    /**
     * 近7天逐日成本USD(下标同上)
     */
    private List<BigDecimal> cost7d;

    public List<Long> getRuns7d() {
        return runs7d;
    }

    public void setRuns7d(List<Long> runs7d) {
        this.runs7d = runs7d;
    }

    public List<BigDecimal> getCost7d() {
        return cost7d;
    }

    public void setCost7d(List<BigDecimal> cost7d) {
        this.cost7d = cost7d;
    }
}
