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
package com.yangqiongai.ai.agent.data.core.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.agent.data.core.entity.WorkspaceEntity;
import com.yangqiongai.ai.agent.data.core.mapper.WorkspaceMapper;
import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.repository.UserWorkspaceRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户工作区仓库默认实现
 * @author yangqiong
 */
public class DefaultUserWorkspaceRepository implements UserWorkspaceRepository {

    @Autowired
    private WorkspaceMapper workspaceMapper;

    @Override
    public void save(WorkspaceInfo info) {
        WorkspaceEntity entity = toEntity(info);
        entity.setCreateTime(LocalDateTime.now());
        entity.setUpdateTime(LocalDateTime.now());
        workspaceMapper.insert(entity);
        info.setId(entity.getId());
    }

    @Override
    public void updateById(WorkspaceInfo info) {
        WorkspaceEntity entity = toEntity(info);
        entity.setUpdateTime(LocalDateTime.now());
        workspaceMapper.updateById(entity);
    }

    @Override
    public void deleteById(Long id) {
        workspaceMapper.deleteById(id);
    }

    @Override
    public WorkspaceInfo findById(Long id) {
        WorkspaceEntity entity = workspaceMapper.selectById(id);
        return entity != null ? toInfo(entity) : null;
    }

    @Override
    public WorkspaceInfo findByIdAndUserId(Long id, String userId) {
        WorkspaceEntity entity = workspaceMapper.selectOne(
                new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getId, id)
                        .eq(WorkspaceEntity::getUserId, userId));
        return entity != null ? toInfo(entity) : null;
    }

    @Override
    public List<WorkspaceInfo> listByUserId(String userId) {
        return workspaceMapper.selectList(
                new LambdaQueryWrapper<WorkspaceEntity>()
                        .eq(WorkspaceEntity::getUserId, userId)
                        .orderByDesc(WorkspaceEntity::getUpdateTime))
                .stream().map(this::toInfo).toList();
    }

    @Override
    public boolean existsByRootPath(String rootPath) {
        return workspaceMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceEntity>().eq(WorkspaceEntity::getRootPath, rootPath)) > 0;
    }

    @Override
    public long countByUserId(String userId) {
        return workspaceMapper.selectCount(
                new LambdaQueryWrapper<WorkspaceEntity>().eq(WorkspaceEntity::getUserId, userId));
    }

    private WorkspaceInfo toInfo(WorkspaceEntity entity) {
        WorkspaceInfo info = new WorkspaceInfo();
        info.setId(entity.getId());
        info.setScopeId(entity.getScopeId());
        info.setUserId(entity.getUserId());
        info.setName(entity.getName());
        info.setRootPath(entity.getRootPath());
        info.setDescription(entity.getDescription());
        info.setApprovalMode(entity.getApprovalMode());
        // 存量行经迁移脚本补默认值，此处再兜底为SERVER防止历史数据空类型
        info.setType(entity.getType() == null || entity.getType().isBlank()
                ? "SERVER" : entity.getType());
        info.setStatus(entity.getStatus());
        info.setCreateTime(entity.getCreateTime());
        info.setUpdateTime(entity.getUpdateTime());
        return info;
    }

    private WorkspaceEntity toEntity(WorkspaceInfo info) {
        WorkspaceEntity entity = new WorkspaceEntity();
        entity.setId(info.getId());
        entity.setScopeId(info.getScopeId());
        entity.setUserId(info.getUserId());
        entity.setName(info.getName());
        entity.setRootPath(info.getRootPath());
        entity.setDescription(info.getDescription());
        entity.setApprovalMode(info.getApprovalMode());
        entity.setType(info.getType());
        entity.setStatus(info.getStatus());
        return entity;
    }
}
