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

import java.util.List;

/**
 * 外部工具收集者
 * <p>
 * 供平台侧扩展（连接器等场景）在Toolkit装配时并入外部产出的AgentTool，
 * 实现方由Spring容器注入，框架侧零业务依赖。
 * </p>
 * @author yangqiong
 */
public interface ToolExtraContributor {

    /**
     * 收集指定Agent可用的外部工具
     * @param agentCode Agent编码
     * @return AgentTool列表，可空
     */
    List<AgentTool> collectTools(String agentCode);
}
