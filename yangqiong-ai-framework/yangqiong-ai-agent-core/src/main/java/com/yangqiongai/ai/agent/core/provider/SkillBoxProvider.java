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

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolkit;

/**
 * 技能箱提供者接口
 * <p>
 * 定义在ai-agent-core中，由ai-agent-skill模块实现。
 * 通过Spring自动注入，实现模块间的解耦。
 * 不引用ai-agent-skill的具体类型，避免循环依赖。
 * </p>
 * @author yangqiong
 */
public interface SkillBoxProvider {

    /**
     * 按请求装配技能箱
     * @param request Agent请求
     * @return 框架层技能箱，无技能时返回null
     */
    AgentSkillBox resolveSkillBox(AgentRequest request);

    /**
     * 按请求装配技能绑定的工具箱
     * <p>
     * 技能可以绑定工具（boundTools），这些工具需要在Agent运行时可用。
     * 默认返回null（无绑定工具），由ai-agent-skill模块的实现类覆盖。
     * </p>
     * @param request Agent请求
     * @return 框架层工具箱，无绑定工具时返回null
     */
    default AgentToolkit resolveBoundToolkit(AgentRequest request) {
        return null;
    }

    /**
     * 是否启用技能箱装配
     * @return
     */
    default boolean isEnabled() {
        return true;
    }
}
