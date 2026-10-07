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

import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.platform.connector.entity.ConnectorCredential;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.mapper.ConnectorInstanceMapper;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("连接器工具收集者单元测试")
class ConnectorToolkitContributorTest {

    @Mock
    private ConnectorInstanceMapper instanceMapper;

    @Mock
    private ConnectorCredentialService credentialService;

    @Mock
    private ConnectorProvider provider;

    private ConnectorRegistry registry;

    private ConnectorToolkitContributor contributor;

    @BeforeEach
    void setUp() {
        lenient().when(provider.providerCode()).thenReturn("testprov");
        lenient().when(provider.supports(any())).thenReturn(true);
        registry = new ConnectorRegistry(List.of(provider));
        contributor = new ConnectorToolkitContributor();
        setField("instanceMapper", instanceMapper);
        setField("registry", registry);
        setField("credentialService", credentialService);
        setField("toolCacheMs", 60_000L);
    }

    /**
     * 反射注入私有字段（Contributor为字段注入风格）
     * @param name
     * @param value
     */
    private void setField(String name, Object value) {
        try {
            var field = ConnectorToolkitContributor.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(contributor, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * 构建启用的模拟实例
     * @param instanceCode
     * @param credentialId 可空
     * @return
     */
    private ConnectorInstance buildEnabledInstance(String instanceCode, Long credentialId) {
        ConnectorInstance instance = new ConnectorInstance();
        instance.setDbId(1L);
        instance.setInstanceCode(instanceCode);
        instance.setProviderCode("testprov");
        instance.setStatus("ENABLED");
        instance.setCredentialId(credentialId);
        return instance;
    }

    /**
     * 构建指定名称的模拟AgentTool
     * @param name
     * @return
     */
    private AgentTool mockAgentTool(String name) {
        return new AgentTool() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getDescription() {
                return "工具" + name;
            }

            @Override
            public Map<String, Object> getParameters() {
                return Map.of();
            }

            @Override
            public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
                return Mono.just(AgentToolResultBlock.of(
                        List.of(AgentTextBlock.builder().text("ok").build())));
            }
        };
    }

    @Test
    @DisplayName("空agentCode返回空工具列表")
    void shouldReturnEmptyForBlankAgentCode() {
        assertThat(contributor.collectTools(null)).isEmpty();
        assertThat(contributor.collectTools(" ")).isEmpty();
    }

    @Test
    @DisplayName("启用实例产出命名空间化工具")
    void shouldCollectNamespacedToolsFromEnabledInstances() {
        when(instanceMapper.selectList(any())).thenReturn(List.of(buildEnabledInstance("dt1", null)));
        when(provider.createTools(any(), any())).thenReturn(List.of(mockAgentTool("send_notice")));

        List<AgentTool> tools = contributor.collectTools("agent-1");

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getName()).isEqualTo("connector_dt1_send_notice");
        assertThat(tools.get(0).getToolCategory()).isEqualTo("connector");
    }

    @Test
    @DisplayName("绑定凭证时解密视图传入提供商")
    void shouldPassDecryptedCredentialToProvider() {
        when(instanceMapper.selectList(any())).thenReturn(List.of(buildEnabledInstance("dt1", 5L)));
        when(credentialService.loadCredentialView(5L)).thenReturn(
                new ConnectorCredentialView(Map.of("appKey", "ak")));
        when(provider.createTools(any(), any())).thenReturn(List.of());

        contributor.collectTools("agent-1");

        verify(provider).createTools(any(), any());
    }

    @Test
    @DisplayName("实例工具产出异常时跳过不影响其余实例")
    void shouldSkipFailedInstance() {
        ConnectorInstance bad = buildEnabledInstance("bad", null);
        ConnectorInstance good = buildEnabledInstance("good", null);
        when(instanceMapper.selectList(any())).thenReturn(List.of(bad, good));
        when(provider.createTools(any(), any()))
                .thenThrow(new RuntimeException("配置错误"))
                .thenReturn(List.of(mockAgentTool("query")));

        List<AgentTool> tools = contributor.collectTools("agent-1");

        assertThat(tools).hasSize(1);
        assertThat(tools.get(0).getName()).isEqualTo("connector_good_query");
    }

    @Test
    @DisplayName("缓存TTL内重复收集不再查询数据库")
    void shouldCacheToolsWithinTtl() {
        when(instanceMapper.selectList(any())).thenReturn(List.of(buildEnabledInstance("dt1", null)));
        when(provider.createTools(any(), any())).thenReturn(List.of(mockAgentTool("send_notice")));

        List<AgentTool> first = contributor.collectTools("agent-1");
        List<AgentTool> second = contributor.collectTools("agent-1");

        assertThat(second).isSameAs(first);
        verify(instanceMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("未知提供商或不适配实例跳过")
    void shouldSkipUnknownOrUnsupportedProvider() {
        ConnectorInstance unknownProviderInstance = buildEnabledInstance("u1", null);
        unknownProviderInstance.setProviderCode("no_such");
        ConnectorInstance unsupportedInstance = buildEnabledInstance("u2", null);
        when(instanceMapper.selectList(any())).thenReturn(List.of(unknownProviderInstance, unsupportedInstance));
        when(provider.supports(unsupportedInstance)).thenReturn(false);

        assertThat(contributor.collectTools("agent-1")).isEmpty();
    }
}
