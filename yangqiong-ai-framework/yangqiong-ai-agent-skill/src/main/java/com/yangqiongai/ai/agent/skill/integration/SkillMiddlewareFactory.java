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
package com.yangqiongai.ai.agent.skill.integration;

import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.middleware.AgentMiddleware;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.prompt.SystemPromptSections;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkill;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillBox;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillFilter;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillFilterMode;
import com.yangqiongai.ai.agent.runtime.skill.AgentSkillRepository;
import com.yangqiongai.ai.agent.skill.DefaultAgentSkillBox;

import java.util.List;
import java.util.Map;

/**
 * 技能中间件工厂，构建技能中间件实例
 * @author yangqiong
 */
public class SkillMiddlewareFactory {

    /**
     * 创建技能中间件实例
     * @param repositories 技能仓库列表
     * @param skillFilter 技能过滤规则
     * @return
     */
    public static AgentMiddleware create(
            List<AgentSkillRepository> repositories,
            AgentSkillFilter skillFilter) {
        return new SkillMiddleware(repositories, skillFilter);
    }

    /**
     * 创建技能中间件实例（无SkillFilter）
     * @param repositories
     * @return
     */
    public static AgentMiddleware create(
            List<AgentSkillRepository> repositories) {
        return create(repositories, null);
    }

    /**
     * 技能中间件实现，将仓库中的技能注入系统提示词
     * @author yangqiong
     */
    private static class SkillMiddleware implements AgentMiddleware {

        private final List<AgentSkillRepository> repositories;

        private final AgentSkillFilter skillFilter;

        SkillMiddleware(List<AgentSkillRepository> repositories, AgentSkillFilter skillFilter) {
            this.repositories = repositories != null ? repositories : List.of();
            this.skillFilter = skillFilter;
        }

        /**
         * 系统提示词处理，追加可用技能分块（技能摘要+使用指引）
         * @param systemPrompt
         * @param context
         * @return
         */
        @Override
        public String onSystemPrompt(String systemPrompt, AgentRuntimeContext context) {
            AgentSkillBox skillBox = loadSkills();
            if (skillBox.isEmpty()) {
                return systemPrompt;
            }
            String skillPrompt = skillBox.buildSystemPrompt();
            if (skillPrompt == null || skillPrompt.isBlank()) {
                return systemPrompt;
            }
            String base = systemPrompt != null ? systemPrompt : "";
            String separator = base.isEmpty() || base.endsWith("\n\n") ? "" : (base.endsWith("\n") ? "\n" : "\n\n");
            return base + separator + skillPrompt + SystemPromptSections.SKILL_USAGE_GUIDE;
        }

        /**
         * 工具调用前拦截
         * @param toolName
         * @param input
         * @param context
         * @return
         */
        @Override
        public Map<String, Object> onToolCall(String toolName, Map<String, Object> input, AgentRuntimeContext context) {
            return input;
        }

        /**
         * 工具调用后拦截
         * @param toolName
         * @param result
         * @param context
         * @return
         */
        @Override
        public AgentToolResultBlock onToolResult(String toolName, AgentToolResultBlock result, AgentRuntimeContext context) {
            return result;
        }

        /**
         * 消息处理拦截
         * @param message
         * @param context
         * @return
         */
        @Override
        public AgentMessage onMessage(AgentMessage message, AgentRuntimeContext context) {
            return message;
        }

        /**
         * 从所有仓库加载技能并按过滤规则筛选
         * @return
         */
        private AgentSkillBox loadSkills() {
            AgentSkillBox skillBox = new DefaultAgentSkillBox();
            for (AgentSkillRepository repository : repositories) {
                List<AgentSkill> skills = repository.findAll();
                if (skills == null) {
                    continue;
                }
                for (AgentSkill skill : skills) {
                    if (matchesFilter(skill)) {
                        skillBox.addSkill(skill);
                    }
                }
            }
            return skillBox;
        }

        /**
         * 判断技能是否匹配过滤规则
         * @param skill
         * @return
         */
        private boolean matchesFilter(AgentSkill skill) {
            if (skillFilter == null) {
                return true;
            }
            AgentSkillFilterMode mode = skillFilter.getMode();
            if (mode == null || mode == AgentSkillFilterMode.ALL) {
                return true;
            }
            if (mode == AgentSkillFilterMode.NONE) {
                return false;
            }
            List<String> names = skillFilter.getSkills();
            boolean contains = names != null && names.contains(skill.getName());
            if (mode == AgentSkillFilterMode.ONLY || mode == AgentSkillFilterMode.ENABLE) {
                return contains;
            }
            if (mode == AgentSkillFilterMode.EXCEPT || mode == AgentSkillFilterMode.DISABLE) {
                return !contains;
            }
            return true;
        }
    }
}
