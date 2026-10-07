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

import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("连接器提供商注册中心单元测试")
class ConnectorRegistryTest {

    /**
     * 构建指定编码的模拟提供商
     * @param providerCode
     * @return
     */
    private ConnectorProvider mockProvider(String providerCode) {
        return new ConnectorProvider() {
            @Override
            public String providerCode() {
                return providerCode;
            }

            @Override
            public ConnectorDescriptor descriptor() {
                return ConnectorDescriptor.of("模拟提供商", "test").build();
            }

            @Override
            public List<com.yangqiongai.ai.agent.runtime.tool.AgentTool> createTools(
                    com.yangqiongai.ai.platform.connector.entity.ConnectorInstance instance,
                    com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView credential) {
                return List.of();
            }
        };
    }

    @Test
    @DisplayName("按providerCode索引并列出全部目录")
    void shouldIndexAndListProviders() {
        ConnectorProvider dingtalk = mockProvider("dingtalk");
        ConnectorProvider database = mockProvider("database");

        ConnectorRegistry registry = new ConnectorRegistry(List.of(dingtalk, database));

        assertThat(registry.listProviders()).containsExactly(dingtalk, database);
        assertThat(registry.getProvider("dingtalk")).isSameAs(dingtalk);
        assertThat(registry.getDescriptor("database")).isNotNull();
    }

    @Test
    @DisplayName("providerCode重复时构建失败")
    void shouldRejectDuplicateProviderCode() {
        assertThatThrownBy(() -> new ConnectorRegistry(List.of(mockProvider("dingtalk"), mockProvider("dingtalk"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("重复");
    }

    @Test
    @DisplayName("未知providerCode返回null")
    void shouldReturnNullForUnknownProvider() {
        ConnectorRegistry registry = new ConnectorRegistry(List.of());

        assertThat(registry.getProvider("unknown")).isNull();
        assertThat(registry.getDescriptor("unknown")).isNull();
    }
}
