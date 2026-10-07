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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.core.model.AgentGenerateOptions;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.AgentModelFactory;

/**
 * Agent模型工厂适配器
 * @author yangqiong
 */
public class AgentModelFactoryAdapter implements AgentModelFactory {

    /**
     * 框架模型工厂委托
     */
    private final com.yangqiongai.ai.agent.runtime.model.AgentModelFactory delegate;

    public AgentModelFactoryAdapter(com.yangqiongai.ai.agent.runtime.model.AgentModelFactory delegate) {
        this.delegate = delegate;
    }

    /**
     * 按模型编码获取模型
     * @param modelCode
     * @param options
     * @return
     */
    @Override
    public AgentModel getModel(String modelCode, AgentGenerateOptions options) {
        com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions runtimeOptions = SpiConverters.toRuntimeOptions(options);
        com.yangqiongai.ai.agent.runtime.model.AgentModel runtimeModel = delegate.getModel(modelCode, runtimeOptions);
        return runtimeModel == null ? null : new AgentModelAdapter(runtimeModel);
    }

    /**
     * 按完整配置获取模型
     * @param provider
     * @param apiKey
     * @param modelCode
     * @param options
     * @return
     */
    @Override
    public AgentModel getModelByConfig(String provider, String apiKey, String modelCode, AgentGenerateOptions options) {
        com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions runtimeOptions = SpiConverters.toRuntimeOptions(options);
        com.yangqiongai.ai.agent.runtime.model.AgentModel runtimeModel = delegate.getModelByConfig(provider, apiKey, modelCode, runtimeOptions);
        return runtimeModel == null ? null : new AgentModelAdapter(runtimeModel);
    }
}
