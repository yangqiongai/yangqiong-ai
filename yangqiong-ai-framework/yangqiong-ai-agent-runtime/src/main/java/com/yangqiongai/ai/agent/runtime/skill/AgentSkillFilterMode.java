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
package com.yangqiongai.ai.agent.runtime.skill;

/**
 * Agent技能过滤模式
 * @author yangqiong
 */
public enum AgentSkillFilterMode {

    /**
     * 全部技能可用
     */
    ALL,

    /**
     * 全部技能禁用
     */
    NONE,

    /**
     * 仅列表中的技能可用
     */
    ONLY,

    /**
     * 除列表中的技能外都可用
     */
    EXCEPT,

    /**
     * 启用列表中的技能
     */
    ENABLE,

    /**
     * 禁用列表中的技能
     */
    DISABLE
}
