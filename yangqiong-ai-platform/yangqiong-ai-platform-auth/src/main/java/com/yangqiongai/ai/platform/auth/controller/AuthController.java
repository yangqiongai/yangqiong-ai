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
package com.yangqiongai.ai.platform.auth.controller;

import com.yangqiongai.ai.platform.bss.scope.UserContext;
import com.yangqiongai.ai.platform.auth.service.LocalLoginProvider;
import com.yangqiongai.ai.platform.auth.service.LocalLoginResult;
import com.yangqiongai.ai.platform.auth.service.TokenExchangeService;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.util.RsaCipherUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 认证
 * @author yangqiong
 */
@Tag(name = "认证接口")
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "ai.auth.enabled", havingValue = "true")
public class AuthController {

    @Autowired
    private TokenExchangeService tokenExchangeService;

    /**
     * 本地登录提供方（可选，由引入账号体系的模块提供实现）
     */
    @Autowired
    private ObjectProvider<LocalLoginProvider> localLoginProvider;

    /**
     * 登录凭证RSA解密私钥（Base64编码PKCS#8，与前端公钥配对；未配置时rsa:密文登录报错，明文登录不受影响）
     */
    @Value("${ai.auth.rsa-private-key:}")
    private String rsaPrivateKey;

    /**
     * 登录，支持两种凭证：accessToken 换取系统 JWT；username/password 本地账号登录
     * @param credentials
     * @return
     */
    @Operation(summary = "登录")
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(
            @Parameter(name = "credentials", description = "登录凭证：accessToken 换票模式或 username/password 本地模式") @RequestBody Map<String, Object> credentials) {
        String accessToken = getStr(credentials, "accessToken");
        Map<String, Object> result = new HashMap<>();
        if (accessToken != null && !accessToken.isEmpty()) {
            String jwt;
            try {
                jwt = tokenExchangeService.exchangeToken(accessToken);
            } catch (IllegalArgumentException e) {
                return ApiResult.fail(e.getMessage());
            }
            result.put("token", jwt);
            result.put("tokenType", "Bearer");
            return ApiResult.ok(result);
        }
        String username = resolveCredential(credentials, "username");
        String password = resolveCredential(credentials, "password");
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return ApiResult.fail("登录凭证不能为空");
        }
        LocalLoginProvider provider = localLoginProvider.getIfAvailable();
        if (provider == null) {
            return ApiResult.fail("本地登录未启用");
        }
        LocalLoginResult loginResult;
        try {
            loginResult = provider.login(username, password);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
        if (loginResult == null) {
            return ApiResult.fail("用户名或密码错误");
        }
        result.put("token", loginResult.getToken());
        result.put("tokenType", "Bearer");
        result.put("userId", loginResult.getUserId());
        result.put("displayName", loginResult.getDisplayName());
        return ApiResult.ok(result);
    }

    /**
     * 平台管理员登录，本地账号校验后签发平台管理员令牌
     * @param credentials
     * @return
     */
    @Operation(summary = "平台管理员登录")
    @PostMapping("/login/platform-admin")
    public ApiResult<Map<String, Object>> loginPlatformAdmin(
            @Parameter(name = "credentials", description = "登录凭证：username/password 本地模式") @RequestBody Map<String, Object> credentials) {
        String username = resolveCredential(credentials, "username");
        String password = resolveCredential(credentials, "password");
        if (username == null || username.isEmpty() || password == null || password.isEmpty()) {
            return ApiResult.fail("登录凭证不能为空");
        }
        LocalLoginProvider provider = localLoginProvider.getIfAvailable();
        if (provider == null) {
            return ApiResult.fail("本地登录未启用");
        }
        LocalLoginResult loginResult;
        try {
            loginResult = provider.login(username, password);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
        if (loginResult == null) {
            return ApiResult.fail("用户名或密码错误");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("token", loginResult.getToken());
        result.put("tokenType", "Bearer");
        result.put("tenantId", "default");
        result.put("platformAdmin", Boolean.TRUE);
        Map<String, Object> user = new HashMap<>();
        user.put("id", loginResult.getUserId());
        user.put("name", loginResult.getDisplayName());
        result.put("user", user);
        return ApiResult.ok(result);
    }

    /**
     * 登出，吊销当前令牌并清除上下文
     * @param request
     * @return
     */
    @Operation(summary = "登出")
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            tokenExchangeService.logout(authHeader.substring(7));
        }
        UserContext.clear();
        return ApiResult.ok();
    }

    /**
     * 刷新令牌
     * @param body
     * @return
     */
    @Operation(summary = "刷新令牌")
    @PostMapping("/refresh-token")
    public ApiResult<Map<String, Object>> refreshToken(
            @Parameter(name = "body", description = "包含原令牌") @RequestBody Map<String, Object> body) {
        String oldToken = getStr(body, "token");
        if (oldToken == null || oldToken.isEmpty()) {
            return ApiResult.fail("token 不能为空");
        }
        if (!tokenExchangeService.validateToken(oldToken)) {
            return ApiResult.fail("令牌无效或已过期");
        }
        String newToken;
        try {
            newToken = tokenExchangeService.refreshToken(oldToken);
        } catch (IllegalArgumentException e) {
            return ApiResult.fail(e.getMessage());
        }
        Map<String, Object> result = new HashMap<>();
        result.put("token", newToken);
        result.put("tokenType", "Bearer");
        return ApiResult.ok(result);
    }

    /**
     * 获取当前登录用户
     * @return
     */
    @Operation(summary = "获取当前登录用户")
    @GetMapping("/current-user")
    public ApiResult<Map<String, Object>> currentUser() {
        String userId = UserContext.getUserId();
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        return ApiResult.ok(result);
    }

    /**
     * 从 Map 中安全取字符串
     * @param map
     * @param key
     * @return
     */
    private String getStr(Map<String, Object> map, String key) {
        if (map == null) {
            return null;
        }
        Object val = map.get(key);
        return val == null ? null : String.valueOf(val);
    }

    /**
     * 读取登录凭证并还原明文，rsa:前缀密文用私钥解密，其余原样返回
     * @param credentials
     * @param key
     * @return
     */
    private String resolveCredential(Map<String, Object> credentials, String key) {
        return RsaCipherUtil.resolveCredential(getStr(credentials, key), rsaPrivateKey);
    }
}
