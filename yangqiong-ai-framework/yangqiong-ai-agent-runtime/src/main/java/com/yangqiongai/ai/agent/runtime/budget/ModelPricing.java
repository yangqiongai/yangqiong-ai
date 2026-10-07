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
 * 模型定价
 * <p>
 * 描述单个模型每千Token的输入/输出单价（美元），供成本预算累计使用。
 * </p>
 * @author yangqiong
 * @param modelCode 模型编码
 * @param promptUsdPer1k 输入单价（美元/千Token）
 * @param completionUsdPer1k 输出单价（美元/千Token）
 */
public record ModelPricing(String modelCode, double promptUsdPer1k, double completionUsdPer1k) {
}
