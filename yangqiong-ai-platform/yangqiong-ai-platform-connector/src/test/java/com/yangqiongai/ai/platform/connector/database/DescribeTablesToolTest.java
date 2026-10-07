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

import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("表结构描述工具单元测试")
class DescribeTablesToolTest {

    /**
     * 测试内嵌库地址（JVM内共享，DB_CLOSE_DELAY=-1保持常驻）
     */
    private static final String JDBC_URL =
            "jdbc:h2:mem:dbdescribe;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private ConnectorInstance instance;

    private ConnectorCredentialView credential;

    private DatabaseConnectionManager connectionManager;

    @BeforeAll
    static void initSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS user_info("
                    + "id INT NOT NULL, name VARCHAR(50), mobile VARCHAR(20))");
            statement.execute("CREATE TABLE IF NOT EXISTS t_secret(s VARCHAR(10))");
        }
    }

    @BeforeEach
    void setUp() {
        instance = new ConnectorInstance();
        instance.setDbId(1L);
        instance.setInstanceCode("db1");
        instance.setProviderCode("database");
        instance.setStatus("ENABLED");
        credential = new ConnectorCredentialView(Map.of(
                "jdbcUrl", JDBC_URL,
                "username", "sa",
                "password", ""));
        connectionManager = new DatabaseConnectionManager();
    }

    /**
     * 构建描述工具
     * @param configJson 实例配置JSON可空
     * @return
     */
    private DescribeTablesTool buildTool(String configJson) {
        instance.setConfigJson(configJson);
        return new DescribeTablesTool(instance,
                DatabaseInstanceConfig.parse(instance, new ConnectorProperties()),
                credential, connectionManager);
    }

    /**
     * 同步调用工具
     * @param tool
     * @param tables 可空
     * @return
     */
    private AgentToolResultBlock call(DescribeTablesTool tool, List<String> tables) {
        Map<String, Object> input = tables == null ? Map.of() : Map.of("tables", tables);
        return tool.callAsync(new AgentToolCallParam(input)).block(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("指定表返回列元数据且不返回行数据")
    void shouldDescribeSpecifiedTable() {
        AgentToolResultBlock result = call(buildTool(null), List.of("user_info"));

        assertThat(result.isError()).isFalse();
        String text = result.getTextContent();
        assertThat(text).containsIgnoringCase("user_info");
        assertThat(text).containsIgnoringCase("id").containsIgnoringCase("name").containsIgnoringCase("mobile");
        // NOT NULL标记
        assertThat(text).contains("NOT NULL");
    }

    @Test
    @DisplayName("不传表名时默认描述白名单内全部表")
    void shouldDescribeAllWhitelistedTablesWhenEmpty() {
        String text = call(buildTool("{\"schemaWhitelist\":[\"user_info\"]}"), null).getTextContent();

        assertThat(text).containsIgnoringCase("user_info");
    }

    @Test
    @DisplayName("无表名且无白名单时提示")
    void shouldHintWhenNoTablesAndNoWhitelist() {
        String text = call(buildTool(null), null).getTextContent();

        assertThat(text).contains("tables参数").contains("schemaWhitelist");
    }

    @Test
    @DisplayName("白名单外的表拒绝")
    void shouldRejectTableOutsideWhitelist() {
        String text = call(buildTool("{\"schemaWhitelist\":[\"user_info\"]}"),
                List.of("t_secret")).getTextContent();

        assertThat(text).contains("不在白名单内");
    }

    @Test
    @DisplayName("不存在的表返回提示")
    void shouldHintWhenTableNotFound() {
        String text = call(buildTool(null), List.of("no_such_table")).getTextContent();

        assertThat(text).contains("未找到指定表");
    }

    @Test
    @DisplayName("多表同时描述并分组输出")
    void shouldDescribeMultipleTablesInGroups() {
        String text = call(buildTool(null), List.of("user_info", "t_secret")).getTextContent();

        assertThat(text).containsIgnoringCase("user_info");
        assertThat(text).containsIgnoringCase("t_secret");
        // H2表名元数据为大写，ORDER BY表名字母序：T_SECRET在前
        assertThat(text.indexOf("T_SECRET")).isLessThan(text.indexOf("USER_INFO"));
    }

    @Test
    @DisplayName("执行错误返回错误结果块")
    void shouldReturnErrorBlockOnFailure() {
        // 以非法驱动URL触发连接构建异常（工具边界兜底为可读错误块）
        ConnectorCredentialView badCredential = new ConnectorCredentialView(Map.of(
                "jdbcUrl", "jdbc:invalid:://localhost/db",
                "username", "sa",
                "password", ""));
        DescribeTablesTool tool = new DescribeTablesTool(instance,
                DatabaseInstanceConfig.parse(instance, new ConnectorProperties()),
                badCredential, connectionManager);

        AgentToolResultBlock result = tool.callAsync(
                new AgentToolCallParam(Map.of("tables", List.of("user_info"))))
                .block(Duration.ofSeconds(30));

        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("表结构查询失败");
    }
}
