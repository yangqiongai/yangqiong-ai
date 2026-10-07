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
 * 模型定价SPI
 * <p>
 * 运行时经此接口查询模型单价（美元/千Token），平台侧可对接定价表实现。
 * 查不到定价时返回null，调用方按无定价处理（只记Token不计成本，不阻塞）。
 * </p>
 * @author yangqiong
 */
public interface PricingProvider {

    /**
     * 查询模型定价
     * @param modelCode 模型编码
     * @return 定价，未配置返回null
     */
    ModelPricing getPricing(String modelCode);
}
