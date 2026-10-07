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

import com.yangqiongai.agent.harness.core.tool.AgentTool;
import com.yangqiongai.agent.harness.core.tool.AgentToolkit;

import java.util.ArrayList;
import java.util.List;

/**
 * Agent工具箱适配器
 * @author yangqiong
 */
public class AgentToolkitAdapter implements AgentToolkit {

    /**
     * 框架工具箱委托
     */
    private final com.yangqiongai.ai.agent.runtime.tool.AgentToolkit delegate;

    public AgentToolkitAdapter(com.yangqiongai.ai.agent.runtime.tool.AgentToolkit delegate) {
        this.delegate = delegate;
    }

    /**
     * 获取工具列表
     * @return
     */
    @Override
    public List<AgentTool> getTools() {
        List<com.yangqiongai.ai.agent.runtime.tool.AgentTool> tools = delegate.getTools();
        if (tools == null || tools.isEmpty()) {
            return List.of();
        }
        List<AgentTool> out = new ArrayList<>(tools.size());
        for (com.yangqiongai.ai.agent.runtime.tool.AgentTool tool : tools) {
            if (tool != null) {
                out.add(new AgentToolAdapter(tool));
            }
        }
        return out;
    }

    /**
     * 工具箱是否为空
     * @return
     */
    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * 添加工具
     * @param tool
     */
    @Override
    public void addTool(AgentTool tool) {
        if (tool == null) {
            return;
        }
        if (tool instanceof AgentToolAdapter a) {
            delegate.addTool(a.getDelegate());
            return;
        }
        throw new UnsupportedOperationException("仅支持添加由AgentToolAdapter包装的框架工具");
    }

    /**
     * 获取被包装的框架工具箱
     * @return
     */
    com.yangqiongai.ai.agent.runtime.tool.AgentToolkit getDelegate() {
        return delegate;
    }
}
