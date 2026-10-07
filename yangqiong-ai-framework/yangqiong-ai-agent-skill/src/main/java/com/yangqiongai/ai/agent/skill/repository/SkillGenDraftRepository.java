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

import com.yangqiongai.ai.agent.skill.model.SkillDraftInfo;

import java.util.Optional;

/**
 * 技能生成草稿仓库
 * @author yangqiong
 */
public interface SkillGenDraftRepository {

    /**
     * 根据draftId查询
     * @param draftId
     * @return
     */
    Optional<SkillDraftInfo> getByDraftId(String draftId);

    /**
     * 保存草稿
     * @param draft
     */
    void save(SkillDraftInfo draft);

    /**
     * 更新生成状态
     * @param draftId
     * @param status
     * @return
     */
    boolean updateStatus(String draftId, String status);
}
