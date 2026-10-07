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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 本地账号登录单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class LocalLoginServiceImplTest {

    @Mock
    private SystemUserService systemUserService;

    @Mock
    private TokenExchangeService tokenExchangeService;

    private LocalLoginServiceImpl localLoginService;

    @BeforeEach
    void setUp() {
        localLoginService = new LocalLoginServiceImpl();
        ReflectionTestUtils.setField(localLoginService, "systemUserService", systemUserService);
        ReflectionTestUtils.setField(localLoginService, "tokenExchangeService", tokenExchangeService);
    }

    @Test
    void implementsLocalLoginProviderSpi() {
        assertThat(localLoginService).isInstanceOf(LocalLoginProvider.class);
    }

    @Test
    void loginReturnsResultOnValidCredentials() {
        SystemUser systemUser = new SystemUser();
        systemUser.setId(9L);
        systemUser.setDisplayName("张三");
        when(systemUserService.verifyCredentials("zhangsan", "pwd123")).thenReturn(systemUser);
        when(tokenExchangeService.issueToken("9", "local")).thenReturn("jwt-token");

        LocalLoginResult result = localLoginService.login("zhangsan", "pwd123");

        assertThat(result).isNotNull();
        assertThat(result.getToken()).isEqualTo("jwt-token");
        assertThat(result.getUserId()).isEqualTo("9");
        assertThat(result.getDisplayName()).isEqualTo("张三");
    }

    @Test
    void loginReturnsNullOnInvalidCredentials() {
        when(systemUserService.verifyCredentials(anyString(), anyString())).thenReturn(null);

        assertThat(localLoginService.login("nobody", "bad")).isNull();
        verify(tokenExchangeService, never()).issueToken(anyString(), anyString());
    }

    @Test
    void loginRejectsBlankUsernameOrPassword() {
        assertThatThrownBy(() -> localLoginService.login("", "pwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名和密码不能为空");
        assertThatThrownBy(() -> localLoginService.login("user", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("用户名和密码不能为空");
        verify(systemUserService, never()).verifyCredentials(anyString(), anyString());
    }
}
