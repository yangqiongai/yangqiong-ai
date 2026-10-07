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
package com.yangqiongai.ai.agent.skill.repository;

import com.yangqiongai.ai.agent.core.provider.SkillUsageTracker;
import com.yangqiongai.ai.agent.skill.model.SkillUsageInfo;

import java.util.List;

/**
 * 技能使用量仓库
 * @author yangqiong
 */
public interface SkillUsageRepository extends SkillUsageTracker {

    /**
     * 技能被修改
     * @param skillId
     */
    void bumpPatch(String skillId);

    /**
     * 设置生命周期状态
     * @param skillId
     * @param state
     * @return
     */
    boolean setState(String skillId, String state);

    /**
     * 设置置顶
     * @param skillId
     * @param pinned
     * @return
     */
    boolean setPinned(String skillId, boolean pinned);

    /**
     * 根据skillId查询
     * @param skillId
     * @return
     */
    SkillUsageInfo getBySkillId(String skillId);

    /**
     * 获取使用量排行（按使用次数降序）
     * @param limit
     * @return
     */
    List<SkillUsageInfo> listTopByUseCount(int limit);
}
