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
 * Agent技能仓库元信息
 * @author yangqiong
 */
public final class AgentSkillRepositoryInfo {

    /**
     * 仓库名称
     */
    private final String name;

    /**
     * 仓库类型
     */
    private final String type;

    /**
     * 是否启用
     */
    private final boolean enabled;

    public AgentSkillRepositoryInfo(String name, String type, boolean enabled) {
        this.name = name;
        this.type = type;
        this.enabled = enabled;
    }

    /**
     * 获取仓库名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取仓库类型
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * 是否启用
     * @return
     */
    public boolean isEnabled() {
        return enabled;
    }
}
