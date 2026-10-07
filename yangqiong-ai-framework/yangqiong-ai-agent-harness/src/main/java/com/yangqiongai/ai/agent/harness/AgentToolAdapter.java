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

import com.yangqiongai.agent.harness.core.message.AgentToolResultBlock;
import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolCallParam;

import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Agent工具适配器
 * @author yangqiong
 */
public class AgentToolAdapter implements AgentTool {

    /**
     * 框架工具委托
     */
    private final com.yangqiongai.ai.agent.runtime.tool.AgentTool delegate;

    public AgentToolAdapter(com.yangqiongai.ai.agent.runtime.tool.AgentTool delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取工具名称
     * @return
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    /**
     * 获取工具描述
     * @return
     */
    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    /**
     * 获取工具参数定义
     * @return
     */
    @Override
    public Map<String, Object> getParameters() {
        return delegate.getParameters();
    }

    /**
     * 异步调用工具
     * @param param
     * @return
     */
    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam runtimeParam = SpiConverters.toRuntimeToolCallParam(param);
        return delegate.callAsync(runtimeParam).map(SpiConverters::toHarnessToolResult);
    }

    /**
     * 获取工具类别标识
     * @return
     */
    @Override
    public String getToolCategory() {
        return delegate.getToolCategory();
    }

    /**
     * 获取被包装的框架工具
     * @return
     */
    com.yangqiongai.ai.agent.runtime.tool.AgentTool getDelegate() {
        return delegate;
    }
}
