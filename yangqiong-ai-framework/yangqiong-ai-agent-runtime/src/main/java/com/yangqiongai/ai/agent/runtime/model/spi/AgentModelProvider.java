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
package com.yangqiongai.ai.agent.runtime.model.spi;

import com.yangqiongai.ai.agent.runtime.model.AgentModel;

/**
 * 模型提供者SPI
 * @author yangqiong
 */
public interface AgentModelProvider {

    /**
     * 获取Provider标识
     * @return
     */
    String providerId();

    /**
     * 是否支持指定模型
     * @param provider
     * @param modelName
     * @return
     */
    boolean supports(String provider, String modelName);

    /**
     * 创建模型实例
     * @param provider
     * @param modelName
     * @param context
     * @return
     */
    AgentModel create(String provider, String modelName, AgentModelCreationContext context);
}
