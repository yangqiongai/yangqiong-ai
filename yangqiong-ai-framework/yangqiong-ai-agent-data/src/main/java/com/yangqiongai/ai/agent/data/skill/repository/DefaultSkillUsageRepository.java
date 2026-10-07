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
import com.yangqiongai.ai.agent.skill.config.SkillProperties;
import com.yangqiongai.ai.agent.data.skill.entity.SkillUsageEntity;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillUsageMapper;
import com.yangqiongai.ai.agent.skill.model.SkillUsageInfo;
import com.yangqiongai.ai.agent.skill.repository.SkillUsageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能使用量仓库默认实现
 * @author yangqiong
 */
public class DefaultSkillUsageRepository extends ServiceImpl<SkillUsageMapper, SkillUsageEntity> implements SkillUsageRepository {

    private static final Logger log = LoggerFactory.getLogger(DefaultSkillUsageRepository.class);

    private final SkillProperties properties;

    public DefaultSkillUsageRepository(SkillProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean isEnabled() {
        return properties.getUsageTracking().isEnabled();
    }

    @Override
    public void bumpView(String skillId) {
        SkillUsageEntity entity = getOrCreate(skillId);
        entity.setViewCount(entity.getViewCount() + 1);
        entity.setLastViewedAt(LocalDateTime.now());
        updateById(entity);
    }

    @Override
    public void bumpUse(String skillId) {
        SkillUsageEntity entity = getOrCreate(skillId);
        entity.setUseCount(entity.getUseCount() + 1);
        entity.setLastUsedAt(LocalDateTime.now());
        updateById(entity);
    }

    @Override
    public void bumpPatch(String skillId) {
        SkillUsageEntity entity = getOrCreate(skillId);
        entity.setPatchCount(entity.getPatchCount() + 1);
        entity.setLastPatchedAt(LocalDateTime.now());
        updateById(entity);
    }

    @Override
    public boolean setState(String skillId, String state) {
        SkillUsageEntity entity = getBySkillIdEntity(skillId);
        if (entity == null) return false;
        entity.setState(state);
        return updateById(entity);
    }

    @Override
    public boolean setPinned(String skillId, boolean pinned) {
        SkillUsageEntity entity = getBySkillIdEntity(skillId);
        if (entity == null) return false;
        entity.setPinned(pinned);
        return updateById(entity);
    }

    @Override
    public SkillUsageInfo getBySkillId(String skillId) {
        SkillUsageEntity entity = getBySkillIdEntity(skillId);
        return entity != null ? toInfo(entity) : null;
    }

    @Override
    public List<SkillUsageInfo> listTopByUseCount(int limit) {
        int maxLimit = properties.getUsageTracking().getTopLimit();
        return list(new LambdaQueryWrapper<SkillUsageEntity>()
                .orderByDesc(SkillUsageEntity::getUseCount)
                .orderByDesc(SkillUsageEntity::getViewCount)
                .last("LIMIT " + Math.min(limit, maxLimit))).stream().map(this::toInfo).toList();
    }

    private SkillUsageEntity getBySkillIdEntity(String skillId) {
        return getOne(new LambdaQueryWrapper<SkillUsageEntity>()
                .eq(SkillUsageEntity::getSkillId, skillId));
    }

    private SkillUsageEntity getOrCreate(String skillId) {
        SkillUsageEntity entity = getBySkillIdEntity(skillId);
        if (entity != null) {
            return entity;
        }
        entity = new SkillUsageEntity();
        entity.setSkillId(skillId);
        entity.setViewCount(0L);
        entity.setUseCount(0L);
        entity.setPatchCount(0L);
        entity.setState("ACTIVE");
        entity.setPinned(false);
        save(entity);
        return entity;
    }

    private SkillUsageInfo toInfo(SkillUsageEntity entity) {
        SkillUsageInfo info = new SkillUsageInfo();
        info.setSkillId(entity.getSkillId());
        info.setViewCount(entity.getViewCount());
        info.setUseCount(entity.getUseCount());
        info.setPatchCount(entity.getPatchCount());
        info.setState(entity.getState());
        info.setPinned(entity.getPinned());
        info.setLastViewedAt(entity.getLastViewedAt());
        info.setLastUsedAt(entity.getLastUsedAt());
        info.setLastPatchedAt(entity.getLastPatchedAt());
        return info;
    }
}
