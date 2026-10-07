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
package com.yangqiongai.ai.trust.profile;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.trust.profile.entity.AgentPermissionProfile;
import com.yangqiongai.ai.trust.profile.mapper.AgentPermissionProfileMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent权限画像服务测试
 * @author yangqiong
 */
@DisplayName("Agent权限画像服务测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PermissionProfileServiceImplTest {

    @Mock
    private AgentPermissionProfileMapper profileMapper;

    private PermissionProfileServiceImpl service;

    @BeforeAll
    static void initTableInfo() {
        // LambdaQueryWrapper依赖实体元数据缓存，单元测试环境需手动初始化
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                AgentPermissionProfile.class);
    }

    @BeforeEach
    void setUp() {
        service = new PermissionProfileServiceImpl(profileMapper);
    }

    private AgentPermissionProfile profile(String toolWhitelist, String status) {
        AgentPermissionProfile item = new AgentPermissionProfile();
        item.setAgentCode("agent-a");
        item.setToolWhitelist(toolWhitelist);
        item.setStatus(status);
        item.setDataMaskLevel("BASIC");
        return item;
    }

    @Test
    @DisplayName("无画像默认全放行")
    void noProfileShouldAllowAll() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        assertThat(service.isToolAllowed("agent-a", "sendEmail")).isTrue();
        assertThat(service.isEgressAllowed("agent-a", "api.example.com")).isTrue();
    }

    @Test
    @DisplayName("画像禁用后全放行(回退)")
    void disabledProfileShouldAllowAll() {
        when(profileMapper.selectOne(any())).thenReturn(profile("[\"sendEmail\"]", "DISABLED"));
        assertThat(service.isToolAllowed("agent-a", "sendEmail")).isTrue();
        assertThat(service.isToolAllowed("agent-a", "anyTool")).isTrue();
        assertThat(service.isEgressAllowed("agent-a", "evil.com")).isTrue();
    }

    @Test
    @DisplayName("工具白名单命中放行未命中拒绝")
    void toolWhitelistMatchShouldDecide() {
        when(profileMapper.selectOne(any())).thenReturn(profile("[\"sendEmail\",\"crmQuery\"]", "ENABLED"));
        assertThat(service.isToolAllowed("agent-a", "sendEmail")).isTrue();
        assertThat(service.isToolAllowed("agent-a", "deleteUser")).isFalse();
    }

    @Test
    @DisplayName("工具白名单空缺失不限制")
    void emptyToolWhitelistShouldAllowAll() {
        when(profileMapper.selectOne(any())).thenReturn(profile(null, "ENABLED"));
        assertThat(service.isToolAllowed("agent-a", "anyTool")).isTrue();

        when(profileMapper.selectOne(any())).thenReturn(profile("[]", "ENABLED"));
        assertThat(service.isToolAllowed("agent-a", "anyTool")).isTrue();
    }

    @Test
    @DisplayName("出口白名单精确与通配匹配")
    void egressWhitelistMatchShouldDecide() {
        AgentPermissionProfile item = profile(null, "ENABLED");
        item.setEgressWhitelist("[\"api.example.com\",\"*.internal.corp\"]");
        when(profileMapper.selectOne(any())).thenReturn(item);
        assertThat(service.isEgressAllowed("agent-a", "api.example.com")).isTrue();
        assertThat(service.isEgressAllowed("agent-a", "svc.internal.corp")).isTrue();
        assertThat(service.isEgressAllowed("agent-a", "evil.com")).isFalse();
    }

    @Test
    @DisplayName("出口白名单空缺失不限制")
    void emptyEgressWhitelistShouldAllowAll() {
        when(profileMapper.selectOne(any())).thenReturn(profile(null, "ENABLED"));
        assertThat(service.isEgressAllowed("agent-a", "any.host")).isTrue();
    }

    @Test
    @DisplayName("清单JSON解析失败视为不限制")
    void brokenJsonShouldAllowAll() {
        when(profileMapper.selectOne(any())).thenReturn(profile("not-json", "ENABLED"));
        assertThat(service.isToolAllowed("agent-a", "anyTool")).isTrue();
    }

    @Test
    @DisplayName("保存画像按Agent编码upsert")
    void saveShouldUpsertByAgentCode() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        AgentPermissionProfile created = profile("[\"t1\"]", "ENABLED");
        service.save(created);
        verify(profileMapper).insert(created);
        verify(profileMapper, never()).updateById(any(AgentPermissionProfile.class));

        AgentPermissionProfile existing = profile("[\"t1\"]", "ENABLED");
        existing.setId(99L);
        when(profileMapper.selectOne(any())).thenReturn(existing);
        AgentPermissionProfile updated = profile("[\"t2\"]", "ENABLED");
        service.save(updated);
        verify(profileMapper).updateById(updated);
        assertThat(updated.getId()).isEqualTo(99L);
    }

    @Test
    @DisplayName("保存画像缺省值自动填充")
    void saveShouldFillDefaults() {
        when(profileMapper.selectOne(any())).thenReturn(null);
        AgentPermissionProfile item = new AgentPermissionProfile();
        item.setAgentCode("agent-a");
        service.save(item);
        assertThat(item.getStatus()).isEqualTo("ENABLED");
        assertThat(item.getDataMaskLevel()).isEqualTo("NONE");
    }

    @Test
    @DisplayName("切换画像启用禁用状态")
    void toggleShouldSwitchStatus() {
        AgentPermissionProfile enabled = profile(null, "ENABLED");
        when(profileMapper.selectById(1L)).thenReturn(enabled);
        service.toggle(1L);
        assertThat(enabled.getStatus()).isEqualTo("DISABLED");
        verify(profileMapper).updateById(enabled);

        service.toggle(1L);
        assertThat(enabled.getStatus()).isEqualTo("ENABLED");
    }

    @Test
    @DisplayName("列表查询按条件返回")
    void listShouldReturnFromMapper() {
        AgentPermissionProfile item = profile(null, "ENABLED");
        when(profileMapper.selectList(any())).thenReturn(List.of(item));
        List<AgentPermissionProfile> result = service.list("agent-a", "ENABLED");
        assertThat(result).containsExactly(item);
    }
}
