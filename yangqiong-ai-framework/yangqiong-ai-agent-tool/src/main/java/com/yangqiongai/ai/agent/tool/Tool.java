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
 * 项目工具提供者标记接口
 * <p>
 * 实现此接口的类会被自动注册到工具箱中，
 * 其中标注了{@link AgentTool}的方法将被识别为可调用工具。
 * </p>
 * <p>
 * 接口提供的 default 方法用于声明工具元数据，启动时会通过
 * {@code ToolConfigManager.syncToolsFromSpring()} 同步到 ai_tool_config 表。
 * 实现类可按需重写这些方法以提供更精确的元数据。
 * </p>
 * @author yangqiong
 */
public interface Tool {

    /**
     * 工具编码(默认取类 SimpleName)
     * @return
     */
    default String getToolCode() {
        return getClass().getSimpleName();
    }

    /**
     * 工具名称(默认取类 SimpleName，可被@AgentTool.name 覆盖)
     * @return
     */
    default String getToolName() {
        return getClass().getSimpleName();
    }

    /**
     * 工具类型(默认 TOOL，可被@AgentTool.type 覆盖)
     * @return
     */
    default String getToolType() {
        return "TOOL";
    }

    /**
     * 工具分类(默认CUSTOM，可被@AgentTool.category 覆盖)
     * @return
     */
    default ToolCategory getToolCategory() {
        return ToolCategory.CUSTOM;
    }

    /**
     * 排序号(默认 0，数值越小越靠前)
     * @return
     */
    default int getToolOrder() {
        return 0;
    }

    /**
     * 工具描述(默认空，可从类上首个@AgentTool 方法注解的 value 推断)
     * @return
     */
    default String getToolDesc() {
        return "";
    }
}
