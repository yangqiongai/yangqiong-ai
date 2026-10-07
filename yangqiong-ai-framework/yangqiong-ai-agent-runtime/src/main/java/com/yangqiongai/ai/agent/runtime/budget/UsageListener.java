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
 * 用量监听SPI
 * <p>
 * 运行时在用量汇总点发布Token用量，具体落库/计费/配额由平台侧实现并注入。
 * 未注入时静默降级不计量。
 * </p>
 * @author yangqiong
 */
public interface UsageListener {

    /**
     * 上报一次运行的Token用量与耗时
     * @param usage 用量数据
     */
    void onUsage(UsageRecord usage);

    /**
     * 上报一次模型调用的用量明细（默认空实现，兼容既有实现类）
     * @param usage 调用级用量数据
     */
    default void onModelCall(ModelCallUsage usage) {
    }
}
