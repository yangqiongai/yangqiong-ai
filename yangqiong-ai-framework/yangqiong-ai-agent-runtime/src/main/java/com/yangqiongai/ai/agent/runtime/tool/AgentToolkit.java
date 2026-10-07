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
package com.yangqiongai.ai.agent.runtime.tool;

import java.util.List;

/**
 * Agent工具箱
 * @author yangqiong
 */
public interface AgentToolkit {

    /**
     * 获取工具列表
     * @return
     */
    List<AgentTool> getTools();

    /**
     * 工具箱是否为空
     * @return
     */
    boolean isEmpty();

    /**
     * 添加工具
     * @param tool
     */
    void addTool(AgentTool tool);
}
