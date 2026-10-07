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
package com.yangqiongai.ai.agent.runtime;

import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;

/**
 * Agent运行时构建器（基础）
 * <p>
 * 所有运行时实现必须支持的核心配置项。
 * 高级配置见 {@link AdvancedAgentRuntimeBuilder}，
 * 引擎增强特性见 {@link HarnessAgentRuntimeBuilder}。
 * </p>
 * @author yangqiong
 */
public interface AgentRuntimeBuilder {

    /**
     * 设置Agent名称
     * @param name
     * @return
     */
    AgentRuntimeBuilder name(String name);

    /**
     * 设置模型
     * @param model
     * @return
     */
    AgentRuntimeBuilder model(AgentModel model);

    /**
     * 设置回退模型，主模型调用失败时自动切换
     * @param model
     * @return
     */
    default AgentRuntimeBuilder fallbackModel(AgentModel model) {
        return this;
    }

    /**
     * 设置系统提示词
     * @param systemPrompt
     * @return
     */
    AgentRuntimeBuilder systemPrompt(String systemPrompt);

    /**
     * 设置最大迭代次数
     * @param maxIters
     * @return
     */
    AgentRuntimeBuilder maxIters(int maxIters);

    /**
     * 设置工具箱
     * @param toolkit
     * @return
     */
    AgentRuntimeBuilder toolkit(AgentToolkit toolkit);

    /**
     * 注册单个工具，多次调用累积
     * @param tool
     * @return
     */
    default AgentRuntimeBuilder tool(AgentTool tool) {
        return this;
    }

    /**
     * 设置中间件
     * @param middleware
     * @return
     */
    AgentRuntimeBuilder middleware(AgentMiddleware middleware);

    /**
     * 设置模型生成选项
     * @param options
     * @return
     */
    AgentRuntimeBuilder generateOptions(AgentGenerateOptions options);

    /**
     * 构建Agent运行时
     * @return
     */
    AgentRuntime build();
}
