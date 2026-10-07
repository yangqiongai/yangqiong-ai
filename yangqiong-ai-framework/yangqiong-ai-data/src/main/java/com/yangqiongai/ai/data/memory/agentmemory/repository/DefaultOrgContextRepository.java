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
package com.yangqiongai.ai.data.memory.agentmemory.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.data.memory.agentmemory.entity.OrgContext;
import com.yangqiongai.ai.data.memory.agentmemory.mapper.OrgContextMapper;
import com.yangqiongai.ai.memory.agentmemory.model.OrgContextInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.OrgContextRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 组织上下文记忆
 * @author yangqiong
 */
public class DefaultOrgContextRepository implements OrgContextRepository {

    @Autowired
    private OrgContextMapper orgContextMapper;

    @Override
    public Long insert(OrgContextInfo model) {
        OrgContext entity = toEntity(model);
        orgContextMapper.insert(entity);
        model.setId(entity.getId());
        return entity.getId();
    }

    @Override
    public void update(OrgContextInfo model) {
        orgContextMapper.updateById(toEntity(model));
    }

    @Override
    public OrgContextInfo selectById(Long id) {
        OrgContext entity = orgContextMapper.selectById(id);
        return entity != null ? toModel(entity) : null;
    }

    @Override
    public void deleteById(Long id) {
        orgContextMapper.deleteById(id);
    }

    @Override
    public List<OrgContextInfo> findEnabledByType(String contextType) {
        LambdaQueryWrapper<OrgContext> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgContext::getContextType, contextType)
                .eq(OrgContext::getEnabled, 1)
                .orderByAsc(OrgContext::getId);
        return orgContextMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<OrgContextInfo> findEnabledByAlias(String alias) {
        LambdaQueryWrapper<OrgContext> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgContext::getContextType, OrgContextInfo.TYPE_ALIAS)
                .eq(OrgContext::getTerm, alias)
                .eq(OrgContext::getEnabled, 1);
        return orgContextMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<OrgContextInfo> findByTypeAndTerm(String contextType, String term) {
        LambdaQueryWrapper<OrgContext> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgContext::getContextType, contextType)
                .eq(OrgContext::getTerm, term);
        return orgContextMapper.selectList(wrapper).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public List<OrgContextInfo> findAll() {
        return orgContextMapper.selectList(new LambdaQueryWrapper<>()).stream()
                .map(this::toModel).collect(Collectors.toList());
    }

    @Override
    public long countByType(String contextType) {
        LambdaQueryWrapper<OrgContext> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgContext::getContextType, contextType);
        return orgContextMapper.selectCount(wrapper);
    }

    @Override
    public long countConflicts() {
        LambdaQueryWrapper<OrgContext> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OrgContext::getConflictFlag, 1);
        return orgContextMapper.selectCount(wrapper);
    }

    private OrgContext toEntity(OrgContextInfo model) {
        OrgContext entity = new OrgContext();
        entity.setId(model.getId());
        entity.setContextType(model.getContextType());
        entity.setTerm(model.getTerm());
        entity.setTargetTerm(model.getTargetTerm());
        entity.setRemark(model.getRemark());
        entity.setConflictFlag(model.getConflictFlag());
        entity.setEnabled(model.getEnabled());
        return entity;
    }

    private OrgContextInfo toModel(OrgContext entity) {
        OrgContextInfo model = new OrgContextInfo();
        model.setId(entity.getId());
        model.setContextType(entity.getContextType());
        model.setTerm(entity.getTerm());
        model.setTargetTerm(entity.getTargetTerm());
        model.setRemark(entity.getRemark());
        model.setConflictFlag(entity.getConflictFlag());
        model.setEnabled(entity.getEnabled());
        return model;
    }
}
