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

/**
 * 技能使用量追踪接口
 * <p>
 * 定义在ai-agent-core中，由ai-agent-skill模块实现。
 * AgentMiddlewareAdapter在onActing中检测技能相关工具调用，
 * 通过此接口记录查看/使用事件。
 * </p>
 * @author yangqiong
 */
public interface SkillUsageTracker {

    /**
     * 技能被查看（load_skill_through_path / read_skill 调用）
     * @param skillId
     */
    void bumpView(String skillId);

    /**
     * 技能被使用（use_skill 调用）
     * @param skillId
     */
    void bumpUse(String skillId);

    /**
     * 是否启用使用量追踪
     * @return
     */
    default boolean isEnabled() {
        return true;
    }
}
