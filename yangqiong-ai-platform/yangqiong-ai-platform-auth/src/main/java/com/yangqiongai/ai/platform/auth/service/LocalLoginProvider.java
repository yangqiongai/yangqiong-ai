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
package com.yangqiongai.ai.platform.auth.service;

/**
 * 本地登录提供方
 * <p>
 * 认证模块的本地账号登录扩展点：社区版 system 模块提供基于用户表的实现，
 * 商业版可替换为 LDAP/SSO 账号源实现。
 * </p>
 * @author yangqiong
 */
public interface LocalLoginProvider {

    /**
     * 校验用户名密码并签发系统 JWT
     * @param username
     * @param password
     * @return
     */
    LocalLoginResult login(String username, String password);
}
