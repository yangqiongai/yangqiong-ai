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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 智能体工具注解
 * <p>
 * 标注在方法上，声明该方法为AgentScope智能体可调用的工具。
 * 替代LangChain4j的@Tool注解，统一使用AgentScope工具体系。
 * 注解属性会同步到 ai_tool_config 表对应字段，用于工具元数据管理。
 * </p>
 * @author yangqiong
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AgentTool {

    /**
     * 工具功能描述
     */
    String value() default "";

    /**
     * 工具名称
     */
    String name() default "";

    /**
     * 工具类型
     */
    String type() default "TOOL";

    /**
     * 工具分类
     */
    ToolCategory category() default ToolCategory.CUSTOM;
}
