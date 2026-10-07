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
package com.yangqiongai.ai.agent.tool;

import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;

import java.util.ArrayList;
import java.util.List;

/**
 * 默认工具箱实现
 * @author yangqiong
 */
public class DefaultAgentToolkit implements AgentToolkit {

    /**
     * 工具列表
     */
    private final List<AgentTool> tools = new ArrayList<>();

    @Override
    public void addTool(AgentTool tool) {
        if (tool != null) {
            tools.add(tool);
        }
    }

    @Override
    public List<AgentTool> getTools() {
        return tools;
    }

    @Override
    public boolean isEmpty() {
        return tools.isEmpty();
    }
}
