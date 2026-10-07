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
package com.yangqiongai.ai.agent.data.skill.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.yangqiongai.ai.agent.data.skill.entity.SkillGenDraftEntity;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillGenDraftMapper;
import com.yangqiongai.ai.agent.skill.model.SkillDraftInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillGenDraftRepository;

import java.util.Optional;

/**
 * 技能生成草稿仓库默认实现
 * @author yangqiong
 */
public class DefaultSkillGenDraftRepository extends ServiceImpl<SkillGenDraftMapper, SkillGenDraftEntity> implements SkillGenDraftRepository {

    @Override
    public Optional<SkillDraftInfo> getByDraftId(String draftId) {
        SkillGenDraftEntity entity = getOne(new LambdaQueryWrapper<SkillGenDraftEntity>()
                .eq(SkillGenDraftEntity::getDraftId, draftId));
        return Optional.ofNullable(entity).map(this::toInfo);
    }

    @Override
    public void save(SkillDraftInfo draft) {
        SkillGenDraftEntity entity = toEntity(draft);
        save(entity);
    }

    @Override
    public boolean updateStatus(String draftId, String status) {
        SkillGenDraftEntity entity = getOne(new LambdaQueryWrapper<SkillGenDraftEntity>()
                .eq(SkillGenDraftEntity::getDraftId, draftId));
        if (entity == null) return false;
        entity.setGenerateStatus(status);
        return updateById(entity);
    }

    private SkillDraftInfo toInfo(SkillGenDraftEntity entity) {
        SkillDraftInfo info = new SkillDraftInfo();
        info.setDraftId(entity.getDraftId());
        info.setSkillName(entity.getSkillName());
        info.setSkillDescription(entity.getSkillDescription());
        info.setSkillContent(entity.getSkillContent());
        info.setBoundTools(entity.getBoundTools());
        info.setGenerateStatus(entity.getGenerateStatus());
        return info;
    }

    private SkillGenDraftEntity toEntity(SkillDraftInfo info) {
        SkillGenDraftEntity entity = new SkillGenDraftEntity();
        entity.setDraftId(info.getDraftId());
        entity.setSkillName(info.getSkillName());
        entity.setSkillDescription(info.getSkillDescription());
        entity.setSkillContent(info.getSkillContent());
        entity.setBoundTools(info.getBoundTools());
        entity.setGenerateStatus(info.getGenerateStatus());
        return entity;
    }
}
