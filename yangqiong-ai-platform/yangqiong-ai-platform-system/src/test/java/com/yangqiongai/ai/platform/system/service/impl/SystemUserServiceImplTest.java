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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.system.entity.SystemUser;
import com.yangqiongai.ai.platform.system.mapper.SystemUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 平台用户管理单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class SystemUserServiceImplTest {

    /**
     * BCrypt 密码编码器
     */
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    @Mock
    private SystemUserMapper systemUserMapper;

    private SystemUserServiceImpl systemUserService;

    @BeforeEach
    void setUp() {
        systemUserService = new SystemUserServiceImpl();
        ReflectionTestUtils.setField(systemUserService, "systemUserMapper", systemUserMapper);
    }

    /**
     * 构建用户
     * @param id
     * @param username
     * @param password
     * @param status
     * @return
     */
    private SystemUser buildUser(Long id, String username, String password, Integer status) {
        SystemUser systemUser = new SystemUser();
        systemUser.setId(id);
        systemUser.setUsername(username);
        systemUser.setPassword(password);
        systemUser.setStatus(status);
        return systemUser;
    }

    @Test
    void createEncodesPasswordAndDefaultsStatus() {
        SystemUser systemUser = buildUser(null, "alice", "plain123", null);
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        systemUserService.create(systemUser);

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(systemUserMapper).insert(captor.capture());
        SystemUser inserted = captor.getValue();
        // 密码已加密且可校验，默认状态启用
        assertThat(inserted.getPassword()).isNotEqualTo("plain123");
        assertThat(ENCODER.matches("plain123", inserted.getPassword())).isTrue();
        assertThat(inserted.getStatus()).isEqualTo(1);
    }

    @Test
    void createDefaultsScopeIdWhenBlank() {
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        SystemUser systemUser = buildUser(null, "dave", "pwd123", 1);
        systemUserService.create(systemUser);

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(systemUserMapper).insert(captor.capture());
        // 未指定作用域时默认归属 default
        assertThat(captor.getValue().getScopeId()).isEqualTo("default");
    }

    @Test
    void createKeepsExplicitScopeId() {
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        SystemUser systemUser = buildUser(null, "erin", "pwd123", 1);
        systemUser.setScopeId("custom-scope");
        systemUserService.create(systemUser);

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(systemUserMapper).insert(captor.capture());
        // 已指定作用域时保留原值
        assertThat(captor.getValue().getScopeId()).isEqualTo("custom-scope");
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(systemUserMapper.selectOne(any(Wrapper.class)))
                .thenReturn(buildUser(1L, "alice", "x", 1));

        assertThatThrownBy(() -> systemUserService.create(buildUser(null, "alice", "pwd", null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("用户名已存在");
        verify(systemUserMapper, never()).insert(any(SystemUser.class));
    }

    @Test
    void createRejectsBlankPassword() {
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> systemUserService.create(buildUser(null, "bob", " ", null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("密码不能为空");
    }

    @Test
    void updateKeepsPasswordUsernameAndScopeUnchanged() {
        SystemUser exist = buildUser(5L, "alice", ENCODER.encode("old"), 1);
        exist.setScopeId("s1");
        when(systemUserMapper.selectById(5L)).thenReturn(exist);

        SystemUser changes = buildUser(null, "hacker", "newpass", 0);
        changes.setScopeId("evil");
        changes.setDisplayName("新名字");

        SystemUser updated = systemUserService.update(5L, changes);

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(systemUserMapper).updateById(captor.capture());
        // 用户名/密码/作用域强制置空，不允许通过更新接口篡改
        assertThat(captor.getValue().getUsername()).isNull();
        assertThat(captor.getValue().getPassword()).isNull();
        assertThat(captor.getValue().getScopeId()).isNull();
        assertThat(updated.getUsername()).isEqualTo("alice");
    }

    @Test
    void updateRejectsMissingUser() {
        when(systemUserMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> systemUserService.update(9L, new SystemUser()))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void deleteRemovesExistingUser() {
        when(systemUserMapper.selectById(3L)).thenReturn(buildUser(3L, "carol", "x", 1));

        systemUserService.delete(3L);

        verify(systemUserMapper).deleteById(3L);
    }

    @Test
    void deleteRejectsMissingUser() {
        when(systemUserMapper.selectById(3L)).thenReturn(null);

        assertThatThrownBy(() -> systemUserService.delete(3L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("用户不存在");
    }

    @Test
    void updateStatusValidatesValue() {
        when(systemUserMapper.selectById(3L)).thenReturn(buildUser(3L, "carol", "x", 1));

        assertThatThrownBy(() -> systemUserService.updateStatus(3L, 2))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("非法状态值");

        systemUserService.updateStatus(3L, 0);
        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(systemUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(0);
    }

    @Test
    void resetPasswordEncodesNewPassword() {
        when(systemUserMapper.selectById(3L)).thenReturn(buildUser(3L, "carol", "x", 1));

        systemUserService.resetPassword(3L, "freshPwd");

        ArgumentCaptor<SystemUser> captor = ArgumentCaptor.forClass(SystemUser.class);
        verify(systemUserMapper).updateById(captor.capture());
        assertThat(ENCODER.matches("freshPwd", captor.getValue().getPassword())).isTrue();
    }

    @Test
    void resetPasswordRejectsBlank() {
        when(systemUserMapper.selectById(3L)).thenReturn(buildUser(3L, "carol", "x", 1));

        assertThatThrownBy(() -> systemUserService.resetPassword(3L, ""))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("新密码不能为空");
    }

    @Test
    void verifyCredentialsReturnsUserOnlyWhenValid() {
        String rawPassword = "rightPwd";
        SystemUser active = buildUser(1L, "alice", ENCODER.encode(rawPassword), 1);
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(active);

        // 正确凭证
        assertThat(systemUserService.verifyCredentials("alice", rawPassword)).isSameAs(active);
        // 密码错误
        assertThat(systemUserService.verifyCredentials("alice", "wrongPwd")).isNull();
        // 用户不存在
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        assertThat(systemUserService.verifyCredentials("nobody", rawPassword)).isNull();
        // 停用用户
        SystemUser disabled = buildUser(1L, "alice", ENCODER.encode(rawPassword), 0);
        when(systemUserMapper.selectOne(any(Wrapper.class))).thenReturn(disabled);
        assertThat(systemUserService.verifyCredentials("alice", rawPassword)).isNull();
        // 空参数
        assertThat(systemUserService.verifyCredentials("", rawPassword)).isNull();
        assertThat(systemUserService.verifyCredentials("alice", null)).isNull();
    }

    @Test
    void listDelegatesToMapperPage() {
        Page<SystemUser> page = new Page<>(1, 10);
        page.setRecords(List.of(buildUser(1L, "alice", "x", 1)));
        when(systemUserMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        Page<SystemUser> result = systemUserService.list(1, 10, "ali", 1);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getUsername()).isEqualTo("alice");
    }

    @Test
    void getByIdRejectsMissingUser() {
        when(systemUserMapper.selectById(7L)).thenReturn(null);

        assertThatThrownBy(() -> systemUserService.getById(7L))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("用户不存在");
    }
}
