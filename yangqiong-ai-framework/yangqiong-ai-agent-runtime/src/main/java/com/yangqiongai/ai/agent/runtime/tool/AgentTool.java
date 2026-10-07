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

import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Agent工具调用契约
 * @author yangqiong
 */
public interface AgentTool {

    /**
     * 获取工具名称
     * @return
     */
    String getName();

    /**
     * 获取工具描述
     * @return
     */
    String getDescription();

    /**
     * 获取工具参数定义
     * @return
     */
    Map<String, Object> getParameters();

    /**
     * 异步调用工具
     * @param param
     * @return
     */
    Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param);

    /**
     * 工具类别标识，用于权限策略判定
     * <p>
     * 默认 builtin，MCP 工具适配器覆写为 mcp。
     * </p>
     * @return
     */
    default String getToolCategory() {
        return "builtin";
    }

    /**
     * 获取工具规格定义，基于 name/description/parameters 构建
     * @return
     */
    default AgentToolSpec getSpec() {
        return AgentToolSpec.builder()
                .name(getName())
                .description(getDescription())
                .parameters(getParameters())
                .build();
    }
}
