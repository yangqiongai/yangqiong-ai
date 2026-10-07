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

/**
 * 工具分类
 * <p>
 * BUILTIN 内置工具自动装配不过滤；CUSTOM 扩展工具需 agentConfig.tools 显式挂载。
 * 新工具默认 CUSTOM，需显式声明 BUILTIN 才自动加载。
 * </p>
 * @author yangqiong
 */
public enum ToolCategory {

    /**
     * 内置工具（自动装配不过滤）
     */
    BUILTIN,

    /**
     * 扩展工具（需agentConfig.tools显式挂载）
     */
    CUSTOM;

    /**
     * 从字符串解析分类，空/非法值降级CUSTOM
     * @param value
     * @return
     */
    public static ToolCategory fromString(String value) {
        if (value == null || value.isBlank()) {
            return CUSTOM;
        }
        try {
            return ToolCategory.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return CUSTOM;
        }
    }
}
