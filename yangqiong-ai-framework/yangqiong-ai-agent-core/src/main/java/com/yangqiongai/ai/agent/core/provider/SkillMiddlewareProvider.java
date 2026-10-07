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
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;

/**
 * 技能中间件提供者接口
 * <p>
 * 定义在ai-agent-core中，由ai-agent-skill模块实现。
 * 用于动态重建模式，提供HarnessSkillMiddleware实例。
 * </p>
 * @author yangqiong
 */
public interface SkillMiddlewareProvider {

    /**
     * 按请求创建技能中间件
     * @param request Agent请求
     * @return HarnessSkillMiddleware实例，无法创建时返回null
     */
    AgentMiddleware resolveSkillMiddleware(AgentRequest request);
}
