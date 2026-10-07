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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("只读SQL查询工具单元测试")
class DatabaseQueryToolTest {

    /**
     * 测试内嵌库地址（JVM内共享，DB_CLOSE_DELAY=-1保持常驻）
     */
    private static final String JDBC_URL =
            "jdbc:h2:mem:dbquery;MODE=MySQL;DB_CLOSE_DELAY=-1";

    private ConnectorInstance instance;

    private ConnectorCredentialView credential;

    private DatabaseConnectionManager connectionManager;

    @BeforeAll
    static void initSchema() throws Exception {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS user_info("
                    + "id INT, name VARCHAR(50), password VARCHAR(64), remark VARCHAR(500))");
            statement.execute("DELETE FROM user_info");
            statement.execute("INSERT INTO user_info VALUES(1, '张三', 'super-secret-123', NULL)");
            statement.execute("INSERT INTO user_info VALUES(2, '李四', 'pwd-456', '" + "长".repeat(300) + "')");
            statement.execute("INSERT INTO user_info VALUES(3, '王五', 'sk-789', 'a|b')");
            statement.execute("CREATE TABLE IF NOT EXISTS t_secret(s VARCHAR(10))");
            statement.execute("DELETE FROM t_secret");
            statement.execute("INSERT INTO t_secret VALUES('x')");
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
     * 构建查询工具
     * @param configJson 实例配置JSON可空
     * @return
     */
    private DatabaseQueryTool buildTool(String configJson) {
        instance.setConfigJson(configJson);
        return new DatabaseQueryTool(instance,
                DatabaseInstanceConfig.parse(instance, new ConnectorProperties()),
                credential, connectionManager);
    }

    /**
     * 同步调用工具
     * @param tool
     * @param sql 可空
     * @return
     */
    private AgentToolResultBlock call(DatabaseQueryTool tool, String sql) {
        Map<String, Object> input = sql == null ? Map.of() : Map.of("sql", sql);
        return tool.callAsync(new AgentToolCallParam(input)).block(Duration.ofSeconds(30));
    }

    @Test
    @DisplayName("正常查询返回Markdown表格")
    void shouldReturnMarkdownTable() {
        AgentToolResultBlock result = call(buildTool(null), "SELECT id, name FROM user_info WHERE id = 1");

        assertThat(result.isError()).isFalse();
        String text = result.getTextContent();
        // H2元数据列名大写，按忽略大小写断言
        assertThat(text).startsWith("|")
                .containsIgnoringCase("id").containsIgnoringCase("name").contains("张三");
    }

    @Test
    @DisplayName("写操作被安全拒绝")
    void shouldRejectWriteOperation() {
        String text = call(buildTool(null), "DELETE FROM user_info").getTextContent();

        assertThat(text).contains("SQL安全校验未通过").contains("DELETE");
    }

    @Test
    @DisplayName("多语句被拒绝")
    void shouldRejectMultiStatement() {
        String text = call(buildTool(null), "SELECT 1; DELETE FROM user_info").getTextContent();

        assertThat(text).contains("多语句");
    }

    @Test
    @DisplayName("越权表白名单拒绝")
    void shouldRejectTableOutsideWhitelist() {
        String text = call(buildTool("{\"schemaWhitelist\":[\"t_secret\"]}"),
                "SELECT * FROM user_info").getTextContent();

        assertThat(text).contains("不在白名单内");
    }

    @Test
    @DisplayName("白名单内表正常查询")
    void shouldQueryWhitelistedTable() {
        String text = call(buildTool("{\"schemaWhitelist\":[\"t_secret\"]}"),
                "SELECT s FROM t_secret").getTextContent();

        assertThat(text).contains("x");
    }

    @Test
    @DisplayName("超出最大行数自动截断")
    void shouldTruncateRowsBeyondMaxRows() {
        String text = call(buildTool("{\"maxRows\":2}"), "SELECT id FROM user_info").getTextContent();

        assertThat(text).contains("[结果已截断，仅返回前2行]");
    }

    @Test
    @DisplayName("敏感列值自动脱敏")
    void shouldMaskSensitiveColumn() {
        String text = call(buildTool(null), "SELECT name, password FROM user_info WHERE id = 1").getTextContent();

        assertThat(text).contains("张三");
        assertThat(text).doesNotContain("super-secret-123");
        assertThat(text).contains("su****23");
    }

    @Test
    @DisplayName("超长单元格截断且竖线转义")
    void shouldTruncateLongCellAndEscapePipe() {
        String text = call(buildTool(null), "SELECT name, remark FROM user_info").getTextContent();

        // 300字符单元格截断为200+省略号
        assertThat(text).contains("…");
        // 竖线转义防破坏Markdown表格
        assertThat(text).contains("a\\|b");
        assertThat(text).contains("王五");
    }

    @Test
    @DisplayName("NULL值渲染为NULL")
    void shouldRenderNullValue() {
        String text = call(buildTool(null), "SELECT remark FROM user_info WHERE id = 1").getTextContent();

        assertThat(text).contains("NULL");
    }

    @Test
    @DisplayName("SQL缺失时拒绝")
    void shouldRejectMissingSql() {
        String text = call(buildTool(null), null).getTextContent();

        assertThat(text).contains("SQL不能为空");
    }

    @Test
    @DisplayName("执行错误返回错误结果块")
    void shouldReturnErrorBlockOnBadSql() {
        AgentToolResultBlock result = call(buildTool(null), "SELECT * FROM no_such_table");

        assertThat(result.isError()).isTrue();
        assertThat(result.getTextContent()).contains("查询执行失败");
    }

    @Test
    @DisplayName("超时查询返回错误结果")
    void shouldReturnErrorOnQueryTimeout() {
        String text = call(buildTool("{\"timeoutSeconds\":1}"),
                "SELECT COUNT(*) FROM SYSTEM_RANGE(1, 1000000) a, SYSTEM_RANGE(1, 1000) b").getTextContent();

        assertThat(text).contains("查询执行失败");
    }
}
