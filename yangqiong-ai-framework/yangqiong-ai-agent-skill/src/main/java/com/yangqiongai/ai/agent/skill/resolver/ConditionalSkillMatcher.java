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
package com.yangqiongai.ai.agent.skill.resolver;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;

/**
 * 条件技能匹配器
 * @author yangqiong
 */
public interface ConditionalSkillMatcher {

    /**
     * 判断是否匹配
     * @param request
     * @return
     */
    boolean matches(AgentRequest request);

    /**
     * 判断技能是否适用于当前请求
     * @param skill
     * @param request
     * @return
     */
    boolean isSkillApplicable(SkillDefinition skill, AgentRequest request);
}
