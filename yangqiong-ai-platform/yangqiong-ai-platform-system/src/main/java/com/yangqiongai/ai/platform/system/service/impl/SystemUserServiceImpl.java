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
package com.yangqiongai.ai.platform.system.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.platform.system.entity.SystemUser;
import com.yangqiongai.ai.platform.system.mapper.SystemUserMapper;
import com.yangqiongai.ai.platform.system.service.SystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 平台用户管理
 * @author yangqiong
 */
@Service
public class SystemUserServiceImpl implements SystemUserService {

    /**
     * 用户启用状态
     */
    private static final int STATUS_ENABLED = 1;

    /**
     * 用户停用状态
     */
    private static final int STATUS_DISABLED = 0;

    /**
     * 默认作用域标识
     */
    private static final String DEFAULT_SCOPE_ID = "default";

    /**
     * BCrypt 密码编码器
     */
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    @Autowired
    private SystemUserMapper systemUserMapper;

    @Override
    public Page<SystemUser> list(int page, int size, String username, Integer status) {
        return list(page, size, username, status, null);
    }

    @Override
    public Page<SystemUser> list(int page, int size, String username, Integer status, Long orgId) {
        LambdaQueryWrapper<SystemUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(username)) {
            wrapper.like(SystemUser::getUsername, username);
        }
        if (status != null) {
            wrapper.eq(SystemUser::getStatus, status);
        }
        if (orgId != null) {
            wrapper.eq(SystemUser::getOrgId, orgId);
        }
        wrapper.orderByDesc(SystemUser::getCreateTime);
        return systemUserMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public SystemUser getByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        LambdaQueryWrapper<SystemUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemUser::getUsername, username);
        // 用户名在scope内唯一，限定当前作用域避免跨scope命中多条记录
        wrapper.eq(SystemUser::getScopeId, ScopeContext.getScopeId());
        return systemUserMapper.selectOne(wrapper);
    }

    @Override
    public SystemUser getById(Long id) {
        SystemUser systemUser = systemUserMapper.selectById(id);
        if (systemUser == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        return systemUser;
    }

    @Override
    public SystemUser create(SystemUser systemUser) {
        // scope 内校验用户名唯一
        if (getByUsername(systemUser.getUsername()) != null) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "用户名已存在: " + systemUser.getUsername());
        }
        // 密码必填并加密
        if (!StringUtils.hasText(systemUser.getPassword())) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "密码不能为空");
        }
        systemUser.setPassword(PASSWORD_ENCODER.encode(systemUser.getPassword()));
        if (systemUser.getStatus() == null) {
            systemUser.setStatus(STATUS_ENABLED);
        }
        // 未指定作用域时默认归属 default 作用域
        if (!StringUtils.hasText(systemUser.getScopeId())) {
            systemUser.setScopeId(DEFAULT_SCOPE_ID);
        }
        systemUserMapper.insert(systemUser);
        return systemUser;
    }

    @Override
    public SystemUser update(Long id, SystemUser systemUser) {
        SystemUser existUser = systemUserMapper.selectById(id);
        if (existUser == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        systemUser.setId(id);
        // 不允许通过更新接口修改密码、用户名与作用域
        systemUser.setUsername(null);
        systemUser.setPassword(null);
        systemUser.setScopeId(null);
        systemUserMapper.updateById(systemUser);
        return systemUserMapper.selectById(id);
    }

    @Override
    public void delete(Long id) {
        SystemUser existUser = systemUserMapper.selectById(id);
        if (existUser == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        systemUserMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        SystemUser existUser = systemUserMapper.selectById(id);
        if (existUser == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        if (status == null || (status != STATUS_ENABLED && status != STATUS_DISABLED)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "非法状态值: " + status);
        }
        SystemUser update = new SystemUser();
        update.setId(id);
        update.setStatus(status);
        systemUserMapper.updateById(update);
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        SystemUser existUser = systemUserMapper.selectById(id);
        if (existUser == null) {
            throw new AiException(AiErrorCode.NOT_FOUND, "用户不存在: " + id);
        }
        if (!StringUtils.hasText(newPassword)) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "新密码不能为空");
        }
        SystemUser update = new SystemUser();
        update.setId(id);
        update.setPassword(PASSWORD_ENCODER.encode(newPassword));
        systemUserMapper.updateById(update);
    }

    @Override
    public SystemUser verifyCredentials(String username, String rawPassword) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(rawPassword)) {
            return null;
        }
        SystemUser systemUser = getByUsername(username);
        if (systemUser == null) {
            return null;
        }
        if (systemUser.getStatus() == null || systemUser.getStatus() != STATUS_ENABLED) {
            return null;
        }
        if (!PASSWORD_ENCODER.matches(rawPassword, systemUser.getPassword())) {
            return null;
        }
        return systemUser;
    }
}
