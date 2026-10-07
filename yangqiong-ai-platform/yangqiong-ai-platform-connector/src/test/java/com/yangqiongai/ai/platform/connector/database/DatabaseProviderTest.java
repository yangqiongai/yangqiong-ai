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
package com.yangqiongai.ai.platform.connector.database;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorField;
import com.yangqiongai.ai.platform.connector.spi.ConnectorToolDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("数据库查询连接器单元测试")
class DatabaseProviderTest {

    private DatabaseConnectionManager connectionManager;

    private DatabaseProvider provider;

    @BeforeEach
    void setUp() {
        connectionManager = mock(DatabaseConnectionManager.class);
        provider = new DatabaseProvider(new ConnectorProperties(), connectionManager);
    }

    /**
     * 构建完整凭证视图
     * @return
     */
    private ConnectorCredentialView fullCredential() {
        return new ConnectorCredentialView(Map.of(
                "jdbcUrl", "jdbc:mysql://127.0.0.1:3306/db",
                "username", "u",
                "password", "p"));
    }

    @Test
    @DisplayName("提供商标识与目录声明完整")
    void shouldDeclareCompleteDescriptor() {
        assertThat(provider.providerCode()).isEqualTo("database");

        ConnectorDescriptor descriptor = provider.descriptor();
        assertThat(descriptor.getDisplayName()).isEqualTo("数据库查询");
        assertThat(descriptor.getCategory()).isEqualTo("database");
        assertThat(descriptor.isInboundSupported()).isFalse();

        List<String> credentialNames = descriptor.getCredentialFields().stream()
                .map(ConnectorField::getName).toList();
        assertThat(credentialNames).containsExactly("jdbcUrl", "username", "password");
        assertThat(descriptor.getCredentialFields().get(2).isSecret()).isTrue();

        assertThat(descriptor.getConfigFields()).hasSize(3);
        assertThat(descriptor.getTools()).extracting(ConnectorToolDefinition::getName)
                .containsExactly("query_database", "describe_tables");
        assertThat(descriptor.getTools().get(0).getParametersSchema()).contains("sql");
    }

    @Test
    @DisplayName("按实例产出两个工具")
    void shouldCreateTwoTools() {
        ConnectorInstance instance = new ConnectorInstance();
        instance.setDbId(1L);
        instance.setInstanceCode("db1");
        instance.setProviderCode("database");
        instance.setConfigJson("{\"maxRows\":50,\"schemaWhitelist\":[\"t_user\"]}");

        List<AgentTool> tools = provider.createTools(instance, fullCredential());

        assertThat(tools).hasSize(2);
        assertThat(tools.get(0).getName()).isEqualTo("query_database");
        assertThat(tools.get(1).getName()).isEqualTo("describe_tables");
        assertThat(tools.get(0).getToolCategory()).isEqualTo("connector");
        // 白名单配置传导到工具
        assertThat(tools.get(0).getParameters()).containsKeys("type", "properties", "required");
    }

    @Test
    @DisplayName("凭证缺少必填字段时拒绝产出")
    void shouldRejectIncompleteCredential() {
        ConnectorInstance instance = new ConnectorInstance();
        instance.setDbId(1L);
        instance.setInstanceCode("db1");
        instance.setProviderCode("database");

        assertThatThrownBy(() -> provider.createTools(instance, new ConnectorCredentialView(Map.of())))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("jdbcUrl");
    }

    @Test
    @DisplayName("数据库连接器不支持入站网关")
    void shouldNotSupportInboundGateway() {
        ConnectorInstance instance = new ConnectorInstance();
        instance.setDbId(1L);
        instance.setInstanceCode("db1");

        assertThat(provider.createGateway(instance, fullCredential())).isNull();
    }

    @Test
    @DisplayName("连通性测试委托连接池管理器")
    void shouldDelegateCredentialTest() {
        when(connectionManager.testConnection(any(), eq("u"), eq("p"), anyInt())).thenReturn("连接被拒绝");

        assertThat(provider.testCredential(fullCredential())).isEqualTo("连接被拒绝");
    }
}
