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

import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("数据库连接池管理单元测试")
class DatabaseConnectionManagerTest {

    /**
     * 测试内嵌库地址（JVM内共享，DB_CLOSE_DELAY=-1保持常驻）
     */
    private static final String JDBC_URL = "jdbc:h2:mem:dbpool;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private ConnectorInstance instance;

    private ConnectorCredentialView credential;

    private DatabaseConnectionManager manager;

    @BeforeEach
    void setUp() {
        instance = new ConnectorInstance();
        instance.setDbId(1L);
        instance.setInstanceCode("db1");
        instance.setProviderCode("database");
        instance.setStatus("ENABLED");
        instance.setConfigJson("{\"timeoutSeconds\":2}");
        credential = new ConnectorCredentialView(Map.of(
                "jdbcUrl", JDBC_URL,
                "username", "sa",
                "password", ""));
        manager = new DatabaseConnectionManager();
    }

    /**
     * 构建实例配置
     * @return
     */
    private DatabaseInstanceConfig config() {
        return DatabaseInstanceConfig.parse(instance, new ConnectorProperties());
    }

    @Test
    @DisplayName("获取连接并可执行查询")
    void shouldGetWorkingConnection() throws Exception {
        try (Connection connection = manager.getConnection(instance, config(), credential);
             Statement statement = connection.createStatement()) {
            connection.setReadOnly(true);
            try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("凭证连通性测试成功返回null")
    void shouldPassConnectionTest() {
        assertThat(manager.testConnection(JDBC_URL, "sa", "", 5)).isNull();
    }

    @Test
    @DisplayName("凭证连通性测试失败返回原因")
    void shouldReturnFailureReasonOnTest() {
        assertThat(manager.testConnection("jdbc:mysql://127.0.0.1:1/db", "u", "p", 1)).isNotBlank();
        assertThat(manager.testConnection(null, "u", "p", 1)).contains("jdbcUrl");
    }

    @Test
    @DisplayName("凭证指纹变化时重建连接池仍可用")
    void shouldRebuildPoolOnCredentialChange() throws Exception {
        try (Connection ignored = manager.getConnection(instance, config(), credential)) {
            // 首次建池
        }
        // H2内嵌库密码以首次连接为准，改用不同jdbcUrl构成指纹变化
        ConnectorCredentialView changed = new ConnectorCredentialView(Map.of(
                "jdbcUrl", "jdbc:h2:mem:dbpool-b;MODE=MySQL;DB_CLOSE_DELAY=-1",
                "username", "sa",
                "password", ""));
        try (Connection connection = manager.getConnection(instance, config(), changed)) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    @DisplayName("生命周期回调回收连接池")
    void shouldEvictPoolOnLifecycleEvent() throws Exception {
        try (Connection ignored = manager.getConnection(instance, config(), credential)) {
            // 首次建池
        }
        manager.onDeleted("db1");
        try (Connection connection = manager.getConnection(instance, config(), credential)) {
            // 回收后重建仍可用
            assertThat(connection.isValid(1)).isTrue();
        }
        instance.setStatus("DISABLED");
        manager.onChanged(instance);
        assertThat(manager.testConnection(JDBC_URL, "sa", "", 5)).isNull();
    }

    @Test
    @DisplayName("close后可重建连接池且重复close安全")
    void shouldRebuildPoolAfterClose() throws Exception {
        try (Connection ignored = manager.getConnection(instance, config(), credential)) {
            // 首次建池
        }
        manager.close();
        // 容器停机后再次获取按需重建（重复close静默幂等）
        manager.close();
        try (Connection connection = manager.getConnection(instance, config(), credential)) {
            assertThat(connection.isValid(1)).isTrue();
        }
    }

    @Test
    @DisplayName("缺少jdbcUrl时拒绝")
    void shouldRejectMissingJdbcUrl() {
        ConnectorCredentialView empty = new ConnectorCredentialView(Map.of());

        assertThatThrownBy(() -> manager.getConnection(instance, config(), empty).close())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("jdbcUrl");
    }

    @Test
    @DisplayName("连接获取超时返回失败")
    void shouldFailOnConnectionTimeout() {
        // 不可路由地址触发连接超时（timeoutSeconds=1时池等待约6秒内失败）
        ConnectorCredentialView unreachable = new ConnectorCredentialView(Map.of(
                "jdbcUrl", "jdbc:h2:tcp://10.255.255.1:9092/nope",
                "username", "sa",
                "password", ""));

        assertThatThrownBy(() -> {
            try (Connection ignored = manager.getConnection(instance, config(), unreachable)) {
                // 不会到达
            }
        }).isInstanceOf(RuntimeException.class);
    }
}
