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
package com.yangqiongai.ai.agent.core.orchestration;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Set;

/**
 * 子代理声明
 * @author yangqiong
 */
@Data
@Builder
public class SubagentDeclaration {

    /**
     * 子代理名称
     */
    private String name;

    /**
     * 子代理描述
     */
    private String description;

    /**
     * Agent编码，默认使用default处理器
     */
    @Builder.Default
    private String agentCode = "default";

    /**
     * 模型编码，可选，为空时走默认模型
     */
    private String modelCode;

    /**
     * 系统提示词，定义子代理的专业能力和行为规范
     */
    private String systemPrompt;

    /**
     * 工具列表（工具名称），仅作生成提示参考，不参与运行时筛选
     */
    private List<String> tools;

    /**
     * 温度参数
     */
    private Double temperature;

    /**
     * 最大迭代次数
     */
    private Integer maxIterations;

    /**
     * 是否继承主代理工具箱，默认true
     */
    @Builder.Default
    private Boolean inheritTools = Boolean.TRUE;

    /**
     * 工具白名单，非空时子代理仅可用这些工具
     */
    private Set<String> allowedTools;

    /**
     * 工具黑名单，这些工具对子代理不可用
     */
    private Set<String> deniedTools;

    /**
     * 是否继承主代理技能箱，默认true
     */
    @Builder.Default
    private Boolean inheritSkills = Boolean.TRUE;

    /**
     * 是否继承主代理MCP工具，默认true
     */
    @Builder.Default
    private Boolean inheritMcp = Boolean.TRUE;

    /**
     * 是否继承主代理中间件链，默认true
     */
    @Builder.Default
    private Boolean inheritMiddlewares = Boolean.TRUE;
}
