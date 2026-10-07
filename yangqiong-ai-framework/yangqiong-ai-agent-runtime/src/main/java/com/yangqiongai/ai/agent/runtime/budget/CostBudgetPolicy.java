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
package com.yangqiongai.ai.agent.runtime.budget;

/**
 * 成本预算策略
 * <p>
 * 基于真实usage与模型定价累计的成本预算判定：告警阈值用于预警，硬限阈值用于硬控中止。
 * 未注册定价的模型按0计价。
 * </p>
 * @author yangqiong
 */
public interface CostBudgetPolicy {

    /**
     * 判定当前累计成本（美元）是否超过告警阈值
     * @param currentCostUsd 当前累计成本
     * @return true时触发告警（不中断）
     */
    boolean isWarnExceeded(double currentCostUsd);

    /**
     * 判定当前累计成本（美元）是否超过硬限阈值
     * @param currentCostUsd 当前累计成本
     * @return true时硬控中止执行
     */
    boolean isHardExceeded(double currentCostUsd);

    /**
     * 创建仅告警的成本预算策略
     * @param warnUsd 告警阈值（美元）
     * @return
     */
    static CostBudgetPolicy warnOnly(double warnUsd) {
        return new CostBudgetPolicy() {

            @Override
            public boolean isWarnExceeded(double currentCostUsd) {
                return currentCostUsd >= warnUsd;
            }

            @Override
            public boolean isHardExceeded(double currentCostUsd) {
                return false;
            }
        };
    }

    /**
     * 创建带硬限的成本预算策略
     * @param warnUsd 告警阈值（美元）
     * @param hardLimitUsd 硬限阈值（美元）
     * @return
     */
    static CostBudgetPolicy hardLimit(double warnUsd, double hardLimitUsd) {
        return new CostBudgetPolicy() {

            @Override
            public boolean isWarnExceeded(double currentCostUsd) {
                return currentCostUsd >= warnUsd;
            }

            @Override
            public boolean isHardExceeded(double currentCostUsd) {
                return currentCostUsd >= hardLimitUsd;
            }
        };
    }
}
