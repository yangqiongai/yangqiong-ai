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
package com.yangqiongai.ai.platform.system.auth;

import com.yangqiongai.ai.platform.auth.service.LocalLoginProvider;
import com.yangqiongai.ai.platform.auth.service.LocalLoginResult;
import com.yangqiongai.ai.platform.auth.service.TokenExchangeService;
import com.yangqiongai.ai.platform.system.entity.SystemUser;
import com.yangqiongai.ai.platform.system.service.SystemUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 本地账号登录
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.auth.enabled", havingValue = "true")
public class LocalLoginServiceImpl implements LocalLoginProvider {

    /**
     * 登录提供方标识
     */
    private static final String PROVIDER = "local";

    /**
     * 用户管理服务
     */
    @Autowired
    private SystemUserService systemUserService;

    /**
     * JWT 签发服务
     */
    @Autowired
    private TokenExchangeService tokenExchangeService;

    /**
     * 校验用户名密码并签发系统 JWT
     * @param username
     * @param password
     * @return
     */
    @Override
    public LocalLoginResult login(String username, String password) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        // 凭证无效（不存在/停用/密码错误）统一返回 null，避免用户名枚举
        SystemUser systemUser = systemUserService.verifyCredentials(username, password);
        if (systemUser == null) {
            return null;
        }
        String token = tokenExchangeService.issueToken(String.valueOf(systemUser.getId()), PROVIDER);
        return new LocalLoginResult(token, String.valueOf(systemUser.getId()), systemUser.getDisplayName());
    }
}
