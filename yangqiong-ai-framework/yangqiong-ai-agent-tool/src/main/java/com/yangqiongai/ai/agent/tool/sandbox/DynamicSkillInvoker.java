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
package com.yangqiongai.ai.agent.tool.sandbox;

import com.yangqiongai.ai.agent.tool.AgentTool;
import com.yangqiongai.ai.agent.tool.AgentToolParam;
import com.yangqiongai.ai.agent.tool.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 动态技能调用器
 * @author yangqiong
 */
@Component
@ConditionalOnProperty(name = "ai.dynamic-skill.enabled", havingValue = "true", matchIfMissing = false)
public class DynamicSkillInvoker implements Tool {

    private static final Logger log = LoggerFactory.getLogger(DynamicSkillInvoker.class);

    /**
     * 动态执行技能脚本
     * @param skillName
     * @param argumentsJson
     * @return
     */
    @AgentTool("动态执行技能脚本，根据技能名称和参数执行对应的技能")
    public String invokeSkill(@AgentToolParam("技能名称") String skillName, @AgentToolParam("技能参数，JSON格式字符串") String argumentsJson) {
        log.debug("动态调用技能: skillName={}", skillName);
        return "{\"status\":\"not_implemented\",\"message\":\"动态技能调用尚未实现\"}";
    }
}
