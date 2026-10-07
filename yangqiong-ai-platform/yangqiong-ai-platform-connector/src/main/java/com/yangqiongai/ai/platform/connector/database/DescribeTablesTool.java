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

import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;
import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.agent.runtime.tool.AgentToolCallParam;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 表结构描述工具
 * <p>
 * 输出授权表的元数据（列名/类型/可空性），不返回任何行数据，
 * 与query_database配套供Agent自然语言查表前了解schema；
 * 空表名时默认描述白名单内全部表，白名单外的表拒绝。
 * </p>
 * @author yangqiong
 */
public class DescribeTablesTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(DescribeTablesTool.class);

    /**
     * 工具名（实例挂载后由命名空间装饰器加前缀）
     */
    public static final String NAME = "describe_tables";

    /**
     * 列元数据查询（MySQL方言：DATABASE()+COLUMN_COMMENT）
     */
    private static final String COLUMNS_SQL_MYSQL =
            "SELECT table_name, column_name, column_type, is_nullable, column_comment"
                    + " FROM information_schema.columns WHERE table_schema = DATABASE()"
                    + " AND table_name IN (%s) ORDER BY table_name, ordinal_position";

    /**
     * 列元数据查询（兼容方言：SCHEMA()+REMARKS，H2/PostgreSQL等）
     */
    private static final String COLUMNS_SQL_FALLBACK =
            "SELECT table_name, column_name, data_type, is_nullable, remarks"
                    + " FROM information_schema.columns WHERE table_schema = SCHEMA()"
                    + " AND table_name IN (%s) ORDER BY table_name, ordinal_position";

    /**
     * 连接器实例
     */
    private final ConnectorInstance instance;

    /**
     * 实例配置（白名单）
     */
    private final DatabaseInstanceConfig config;

    /**
     * 解密后的凭证视图
     */
    private final ConnectorCredentialView credential;

    /**
     * 连接池管理
     */
    private final DatabaseConnectionManager connectionManager;

    public DescribeTablesTool(ConnectorInstance instance, DatabaseInstanceConfig config,
                              ConnectorCredentialView credential, DatabaseConnectionManager connectionManager) {
        this.instance = instance;
        this.config = config;
        this.credential = credential;
        this.connectionManager = connectionManager;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String getDescription() {
        return "查询授权表的表结构（列名/类型/可空性），不返回业务数据。"
                + "构造查询SQL前先用本工具了解表结构；不传tables时默认描述白名单内全部表。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> tables = new LinkedHashMap<>();
        tables.put("type", "array");
        tables.put("items", Map.of("type", "string"));
        tables.put("description", "待描述的表名列表，留空时描述白名单内全部表");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("tables", tables));
        return schema;
    }

    @Override
    public String getToolCategory() {
        return "connector";
    }

    @Override
    public Mono<AgentToolResultBlock> callAsync(AgentToolCallParam param) {
        // JDBC为阻塞调用，调度至弹性线程执行
        return Mono.fromCallable(() -> doCall(param)).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 执行白名单校验与元数据查询
     * @param param
     * @return
     */
    private AgentToolResultBlock doCall(AgentToolCallParam param) {
        List<String> tables = extractTables(param);
        // 空表名时默认描述白名单内全部表（去掉库名前缀）
        if (tables.isEmpty() && !config.schemaWhitelist().isEmpty()) {
            tables = config.schemaWhitelist().stream()
                    .map(item -> item.contains(".") ? item.substring(item.lastIndexOf('.') + 1) : item)
                    .toList();
        }
        if (tables.isEmpty()) {
            return text("请通过tables参数指定表名，或在实例配置中设置schemaWhitelist");
        }
        String reject = SqlSafetyValidator.checkTables(tables, config.schemaWhitelist());
        if (reject != null) {
            return text("SQL安全校验未通过: " + reject);
        }
        try (Connection connection = connectionManager.getConnection(instance, config, credential)) {
            return formatColumns(connection, tables);
        } catch (Exception e) {
            // 工具边界统一兜底：连接池构建、驱动缺失等RuntimeException也返回可读错误
            log.warn("连接器表结构查询失败: instance={}", instance.getInstanceCode(), e);
            return AgentToolResultBlock.error("表结构查询失败: " + e.getMessage());
        }
    }

    /**
     * 提取入参表名清单
     * @param param
     * @return
     */
    private List<String> extractTables(AgentToolCallParam param) {
        Object raw = param.getInput() == null ? null : param.getInput().get("tables");
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(item -> item != null && !String.valueOf(item).isBlank())
                .map(item -> String.valueOf(item).trim())
                .toList();
    }

    /**
     * 查询并格式化列元数据（MySQL方言失败时降级兼容方言）
     * @param connection
     * @param tables
     * @return
     * @throws SQLException
     */
    private AgentToolResultBlock formatColumns(Connection connection, List<String> tables) throws SQLException {
        String placeholders = String.join(",", java.util.Collections.nCopies(tables.size(), "UPPER(?)"));
        try {
            return queryColumns(connection, String.format(COLUMNS_SQL_MYSQL, placeholders), tables);
        } catch (SQLException e) {
            log.debug("按MySQL方言查询表结构失败,降级兼容方言: {}", e.getMessage());
            return queryColumns(connection, String.format(COLUMNS_SQL_FALLBACK, placeholders), tables);
        }
    }

    /**
     * 执行列元数据查询并按表分组输出
     * @param connection
     * @param sql
     * @param tables
     * @return
     * @throws SQLException
     */
    private AgentToolResultBlock queryColumns(Connection connection, String sql, List<String> tables)
            throws SQLException {
        Map<String, List<String>> tableColumns = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < tables.size(); i++) {
                statement.setString(i + 1, tables.get(i));
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    String tableName = resultSet.getString(1);
                    // is_nullable跨方言为YES/NO或布尔值，统一按字符串判断
                    String nullable = resultSet.getString(4);
                    boolean notNull = "NO".equalsIgnoreCase(nullable) || "0".equals(nullable);
                    String line = resultSet.getString(2) + " " + resultSet.getString(3)
                            + (notNull ? " NOT NULL" : "")
                            + (resultSet.getString(5) == null || resultSet.getString(5).isBlank()
                            ? "" : " -- " + resultSet.getString(5));
                    tableColumns.computeIfAbsent(tableName, key -> new ArrayList<>()).add(line);
                }
            }
        }
        if (tableColumns.isEmpty()) {
            return text("未找到指定表的结构信息，请确认表名是否正确");
        }
        StringBuilder content = new StringBuilder();
        for (Map.Entry<String, List<String>> entry : tableColumns.entrySet()) {
            content.append("表 ").append(entry.getKey()).append(":\n");
            for (String column : entry.getValue()) {
                content.append("  - ").append(column).append("\n");
            }
        }
        return text(content.toString());
    }

    /**
     * 文本结果包装
     * @param content
     * @return
     */
    private AgentToolResultBlock text(String content) {
        return AgentToolResultBlock.of(List.of(AgentTextBlock.builder().text(content).build()));
    }
}
