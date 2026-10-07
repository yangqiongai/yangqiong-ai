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
package com.yangqiongai.ai.agent.core.provider;

import java.util.Set;

/**
 * 工具名约定提供者
 * <p>
 * 若运行环境未注入实现，{@link #isEnabled()} 返回 false，调用方应降级处理（禁用相关追踪逻辑）。
 * </p>
 * @author yangqiong
 */
public interface ToolConventions {

    /**
     * 委托模式子代理工具名前缀（SDK SubAgentTool 生成的工具名格式为 call_{agentName}）
     * @return
     */
    String subagentToolPrefix();

    /**
     * 技能查看工具名集合（如 load_skill_through_path / read_skill）
     * @return
     */
    Set<String> skillViewTools();

    /**
     * 技能使用工具名（如 use_skill）
     * @return
     */
    String skillUseTool();

    /**
     * 技能加载工具名（加载即使用，需同时记录view和use）
     * @return
     */
    String skillLoadTool();

    /**
     * 是否启用SDK约定
     * <p>
     * 未注入实现或实现方主动禁用时返回false，调用方应降级处理。
     * </p>
     * @return
     */
    default boolean isEnabled() {
        return true;
    }
}
