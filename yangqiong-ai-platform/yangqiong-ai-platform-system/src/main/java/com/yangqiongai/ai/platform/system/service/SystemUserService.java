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
package com.yangqiongai.ai.platform.system.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.platform.system.entity.SystemUser;

/**
 * 平台用户管理
 * @author yangqiong
 */
public interface SystemUserService {

    /**
     * 分页查询用户列表
     * @param page
     * @param size
     * @param username
     * @param status
     * @return
     */
    Page<SystemUser> list(int page, int size, String username, Integer status);

    /**
     * 分页查询用户列表（按组织过滤）
     * @param page
     * @param size
     * @param username
     * @param status
     * @param orgId
     * @return
     */
    Page<SystemUser> list(int page, int size, String username, Integer status, Long orgId);

    /**
     * 按用户名查询启用状态的用户（登录校验用）
     * @param username
     * @return
     */
    SystemUser getByUsername(String username);

    /**
     * 查询用户详情
     * @param id
     * @return
     */
    SystemUser getById(Long id);

    /**
     * 创建用户
     * @param systemUser
     * @return
     */
    SystemUser create(SystemUser systemUser);

    /**
     * 更新用户信息（不允许修改用户名与密码）
     * @param id
     * @param systemUser
     * @return
     */
    SystemUser update(Long id, SystemUser systemUser);

    /**
     * 删除用户
     * @param id
     * @return
     */
    void delete(Long id);

    /**
     * 更新用户状态
     * @param id
     * @param status
     * @return
     */
    void updateStatus(Long id, Integer status);

    /**
     * 重置用户密码
     * @param id
     * @param newPassword
     * @return
     */
    void resetPassword(Long id, String newPassword);

    /**
     * 校验用户名密码是否匹配（登录用，含状态校验）
     * @param username
     * @param rawPassword
     * @return
     */
    SystemUser verifyCredentials(String username, String rawPassword);
}
