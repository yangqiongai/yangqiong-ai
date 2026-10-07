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
package com.yangqiongai.ai.platform.connector.service;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorInstanceMapper;
import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorField;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import com.yangqiongai.ai.platform.connector.spi.ConnectorToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("连接器实例管理单元测试")
class ConnectorInstanceServiceTest {

    @Mock
    private ConnectorInstanceMapper instanceMapper;

    @Mock
    private ConnectorProvider provider;

    @Mock
    private ConnectorInstanceLifecycleListener lifecycleListener;

    private ConnectorRegistry registry;

    private ConnectorInstanceService service;

    @BeforeEach
    void setUp() {
        lenient().when(provider.providerCode()).thenReturn("testprov");
        registry = new ConnectorRegistry(List.of(provider));
        service = new ConnectorInstanceService(instanceMapper, registry, List.of(lifecycleListener));
    }

    /**
     * 构建实例参数
     * @param id 可空
     * @param agentCode 可空
     * @return
     */
    private ConnectorInstance buildInstance(Long id, String agentCode) {
        ConnectorInstance instance = new ConnectorInstance();
        instance.setDbId(id);
        instance.setInstanceCode("inst-1");
        instance.setProviderCode("testprov");
        instance.setName("测试实例");
        instance.setAgentCode(agentCode);
        return instance;
    }

    @Test
    @DisplayName("出站型实例不绑Agent可新增")
    void shouldInsertOutboundInstance() {
        lenient().when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test").build());

        ConnectorInstance saved = service.save(buildInstance(null, null));

        assertThat(saved.getStatus()).isEqualTo("ENABLED");
        verify(instanceMapper).insert(saved);
    }

    @Test
    @DisplayName("入站型提供商实例必须绑定目标Agent")
    void shouldRequireAgentCodeForInboundProvider() {
        when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test").inboundSupported().build());

        assertThatThrownBy(() -> service.save(buildInstance(null, null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("Agent");
        verify(instanceMapper, never()).insert(any(ConnectorInstance.class));
    }

    @Test
    @DisplayName("入站型实例绑定Agent后可新增")
    void shouldInsertInboundInstanceWithAgent() {
        when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test").inboundSupported().build());

        service.save(buildInstance(null, "agent-1"));

        verify(instanceMapper).insert(any(ConnectorInstance.class));
    }

    @Test
    @DisplayName("实例编码在隔离域内重复时拒绝")
    void shouldRejectDuplicateInstanceCode() {
        lenient().when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test").build());
        when(instanceMapper.selectOne(any())).thenReturn(buildInstance(99L, null));

        assertThatThrownBy(() -> service.save(buildInstance(null, null)))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("实例编码相同但为同一条记录时允许更新")
    void shouldAllowUpdateSameInstance() {
        when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test").build());
        when(instanceMapper.selectOne(any())).thenReturn(buildInstance(1L, null));
        when(instanceMapper.selectById(1L)).thenReturn(buildInstance(1L, null));

        service.save(buildInstance(1L, null));

        verify(instanceMapper).updateById(any(ConnectorInstance.class));
    }

    @Test
    @DisplayName("未知提供商拒绝保存")
    void shouldRejectUnknownProvider() {
        ConnectorInstance instance = buildInstance(null, null);
        instance.setProviderCode("no_such");

        assertThatThrownBy(() -> service.save(instance))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("未知");
    }

    @Test
    @DisplayName("启停走乐观锁更新")
    void shouldChangeStatusWithOptimisticLock() {
        ConnectorInstance existing = buildInstance(1L, "agent-1");
        existing.setStatus("ENABLED");
        when(instanceMapper.selectById(1L)).thenReturn(existing);
        when(instanceMapper.updateById(existing)).thenReturn(1);

        service.changeStatus(1L, false);

        ArgumentCaptor<ConnectorInstance> captor = ArgumentCaptor.forClass(ConnectorInstance.class);
        verify(instanceMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    @DisplayName("启停乐观锁冲突时提示并发修改")
    void shouldRejectConcurrentStatusChange() {
        ConnectorInstance existing = buildInstance(1L, "agent-1");
        existing.setStatus("ENABLED");
        when(instanceMapper.selectById(1L)).thenReturn(existing);
        when(instanceMapper.updateById(existing)).thenReturn(0);

        assertThatThrownBy(() -> service.changeStatus(1L, false))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("并发");
    }

    @Test
    @DisplayName("工具清单实时读提供商描述")
    void shouldListToolsFromDescriptor() {
        ConnectorInstance existing = buildInstance(1L, null);
        when(instanceMapper.selectById(1L)).thenReturn(existing);
        when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test")
                .tool(ConnectorToolDefinition.of("send_notice", "发通知").build())
                .build());

        assertThat(service.listTools(1L)).hasSize(1).first()
                .extracting(ConnectorToolDefinition::getName)
                .isEqualTo("send_notice");
    }

    @Test
    @DisplayName("回调地址按实例编码拼接")
    void shouldBuildCallbackUrl() {
        ConnectorInstance existing = buildInstance(1L, null);
        when(instanceMapper.selectById(1L)).thenReturn(existing);

        assertThat(service.callbackUrl(1L, "https://platform.example.com/"))
                .isEqualTo("https://platform.example.com/open/connector/inst-1/callback");
    }

    @Test
    @DisplayName("按编码查询启用实例")
    void shouldFindEnabledInstanceByCode() {
        ConnectorInstance enabled = buildInstance(1L, null);
        enabled.setStatus("ENABLED");
        ConnectorInstance disabled = buildInstance(2L, null);
        disabled.setStatus("DISABLED");
        when(instanceMapper.selectOne(any())).thenReturn(enabled);

        assertThat(service.findEnabledByCode("inst-1")).isSameAs(enabled);

        when(instanceMapper.selectOne(any())).thenReturn(disabled);
        assertThat(service.findEnabledByCode("inst-1")).isNull();

        assertThat(service.findEnabledByCode(null)).isNull();
    }

    @Test
    @DisplayName("删除实例后通知生命周期监听器")
    void shouldNotifyListenerOnDelete() {
        ConnectorInstance existing = buildInstance(1L, null);
        when(instanceMapper.selectById(1L)).thenReturn(existing);

        service.delete(1L);

        verify(instanceMapper).deleteById(1L);
        verify(lifecycleListener).onDeleted("inst-1");
    }

    @Test
    @DisplayName("启停后通知生命周期监听器")
    void shouldNotifyListenerOnStatusChange() {
        ConnectorInstance existing = buildInstance(1L, "agent-1");
        existing.setStatus("ENABLED");
        when(instanceMapper.selectById(1L)).thenReturn(existing);
        when(instanceMapper.updateById(existing)).thenReturn(1);

        service.changeStatus(1L, false);

        verify(lifecycleListener).onChanged(existing);
    }

    @Test
    @DisplayName("更新实例后通知生命周期监听器")
    void shouldNotifyListenerOnUpdate() {
        when(provider.descriptor()).thenReturn(ConnectorDescriptor.of("测试", "test").build());
        when(instanceMapper.selectOne(any())).thenReturn(buildInstance(1L, null));
        when(instanceMapper.selectById(1L)).thenReturn(buildInstance(1L, null));

        service.save(buildInstance(1L, null));

        verify(lifecycleListener).onChanged(any(ConnectorInstance.class));
    }

    @Test
    @DisplayName("监听器异常不影响实例操作")
    void shouldSkipListenerFailure() {
        ConnectorInstance existing = buildInstance(1L, null);
        when(instanceMapper.selectById(1L)).thenReturn(existing);
        doThrow(new RuntimeException("回收失败")).when(lifecycleListener).onDeleted("inst-1");

        service.delete(1L);

        verify(instanceMapper).deleteById(1L);
    }
}
