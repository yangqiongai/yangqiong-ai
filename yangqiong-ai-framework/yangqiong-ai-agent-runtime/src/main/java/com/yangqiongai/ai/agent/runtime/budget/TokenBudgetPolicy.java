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
 * Token预算策略
 * <p>
 * 基于模型返回的真实usage累计判定预算：告警阈值用于预警，硬限阈值用于硬控中止。
 * 装饰顺序约定：重试 → 语义缓存 → 限流 → 自定义层，内层为真实模型。
 * </p>
 * @author yangqiong
 */
public interface TokenBudgetPolicy {

    /**
     * 判定已用Token是否超过告警阈值
     * @param usedTokens 已用Token数
     * @return true时触发告警（不中断）
     */
    boolean isWarnExceeded(long usedTokens);

    /**
     * 判定已用Token是否超过硬限阈值
     * @param usedTokens 已用Token数
     * @return true时硬控中止执行
     */
    boolean isHardExceeded(long usedTokens);

    /**
     * 创建仅告警的预算策略
     * @param warnThresholdTokens 告警阈值
     * @return
     */
    static TokenBudgetPolicy warnOnly(long warnThresholdTokens) {
        return new TokenBudgetPolicy() {

            @Override
            public boolean isWarnExceeded(long usedTokens) {
                return usedTokens >= warnThresholdTokens;
            }

            @Override
            public boolean isHardExceeded(long usedTokens) {
                return false;
            }
        };
    }

    /**
     * 创建带硬限的预算策略
     * @param warnThresholdTokens 告警阈值
     * @param hardLimitTokens 硬限阈值
     * @return
     */
    static TokenBudgetPolicy hardLimit(long warnThresholdTokens, long hardLimitTokens) {
        return new TokenBudgetPolicy() {

            @Override
            public boolean isWarnExceeded(long usedTokens) {
                return usedTokens >= warnThresholdTokens;
            }

            @Override
            public boolean isHardExceeded(long usedTokens) {
                return usedTokens >= hardLimitTokens;
            }
        };
    }
}
