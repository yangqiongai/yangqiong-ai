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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 只读SQL查询工具
 * <p>
 * 工具入参即SQL，安全封装全部在执行侧：只读校验（关键字+连接池read-only双保险）、
 * 表白名单、执行超时、行数截断、敏感列脱敏与单元格截断。
 * </p>
 * @author yangqiong
 */
public class DatabaseQueryTool implements AgentTool {

    private static final Logger log = LoggerFactory.getLogger(DatabaseQueryTool.class);

    /**
     * 工具名（实例挂载后由命名空间装饰器加前缀）
     */
    public static final String NAME = "query_database";

    /**
     * 单元格内容截断长度
     */
    private static final int CELL_MAX_LENGTH = 200;

    /**
     * 敏感列名关键字（命中即掩码，防凭证/密钥类数据经LLM外泄）
     */
    private static final String[] SENSITIVE_KEYWORDS = {
            "password", "passwd", "pwd", "secret", "token", "app_key", "appkey",
            "access_key", "private_key", "secret_key", "密钥", "密码"
    };

    /**
     * 连接器实例
     */
    private final ConnectorInstance instance;

    /**
     * 实例配置（行数/超时/白名单）
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

    public DatabaseQueryTool(ConnectorInstance instance, DatabaseInstanceConfig config,
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
        return "对授权数据库执行只读SQL查询并返回结果表格。仅允许SELECT/SHOW/DESC/EXPLAIN/WITH语句，"
                + "越权表与写操作会被拒绝，超出最大行数时自动截断。";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> sql = new LinkedHashMap<>();
        sql.put("type", "string");
        sql.put("description", "要执行的只读SQL语句，如 SELECT id, name FROM t_user WHERE status=1 LIMIT 10");
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Map.of("sql", sql));
        schema.put("required", List.of("sql"));
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
     * 执行安全校验与查询
     * @param param
     * @return
     */
    private AgentToolResultBlock doCall(AgentToolCallParam param) {
        String sql = param.getInput() == null ? null
                : (param.getInput().get("sql") == null ? null : String.valueOf(param.getInput().get("sql")));
        String reject = SqlSafetyValidator.checkReadOnly(sql);
        if (reject != null) {
            return text("SQL安全校验未通过: " + reject);
        }
        reject = SqlSafetyValidator.checkWhitelist(sql, config.schemaWhitelist());
        if (reject != null) {
            return text("SQL安全校验未通过: " + reject);
        }
        try (Connection connection = connectionManager.getConnection(instance, config, credential);
             Statement statement = connection.createStatement()) {
            statement.setQueryTimeout(config.timeoutSeconds());
            statement.setMaxRows(config.maxRows() + 1);
            try (ResultSet resultSet = statement.executeQuery(stripTrailingSemicolon(sql))) {
                return formatResult(resultSet);
            }
        } catch (Exception e) {
            // 工具边界统一兜底：连接池构建、驱动缺失等RuntimeException也返回可读错误
            log.warn("连接器查询执行失败: instance={}", instance.getInstanceCode(), e);
            return AgentToolResultBlock.error("查询执行失败: " + e.getMessage());
        }
    }

    /**
     * 格式化查询结果为Markdown表格（含截断与脱敏）
     * @param resultSet
     * @return
     * @throws SQLException
     */
    private AgentToolResultBlock formatResult(ResultSet resultSet) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<String> columns = new ArrayList<>(columnCount);
        List<Boolean> sensitive = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            String columnName = metaData.getColumnLabel(i);
            columns.add(columnName);
            sensitive.add(isSensitiveColumn(columnName));
        }
        List<List<String>> rows = new ArrayList<>();
        boolean truncated = false;
        while (resultSet.next()) {
            if (rows.size() >= config.maxRows()) {
                truncated = true;
                break;
            }
            List<String> row = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                row.add(renderCell(resultSet.getString(i), sensitive.get(i - 1)));
            }
            rows.add(row);
        }
        StringBuilder text = new StringBuilder();
        text.append("| ").append(String.join(" | ", columns)).append(" |\n");
        text.append("|").append(" --- |".repeat(columnCount)).append("\n");
        for (List<String> row : rows) {
            text.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        if (truncated) {
            text.append("\n[结果已截断，仅返回前").append(config.maxRows()).append("行]");
        } else if (rows.isEmpty()) {
            text.insert(0, "查询结果为空。\n\n");
        }
        return text(text.toString());
    }

    /**
     * 渲染单元格（脱敏与长度截断）
     * @param value 原始值可空
     * @param masked 是否敏感列
     * @return
     */
    private String renderCell(String value, boolean masked) {
        if (value == null) {
            return "NULL";
        }
        String content = masked ? maskValue(value) : value;
        if (content.length() > CELL_MAX_LENGTH) {
            content = content.substring(0, CELL_MAX_LENGTH) + "…";
        }
        return content.replace("\r", " ").replace("\n", " ").replace("|", "\\|");
    }

    /**
     * 值脱敏（保留首尾各2字符）
     * @param value
     * @return
     */
    private String maskValue(String value) {
        if (value.length() <= 4) {
            return "****";
        }
        return value.substring(0, 2) + "****" + value.substring(value.length() - 2);
    }

    /**
     * 列名是否敏感（大小写不敏感的包含匹配）
     * @param columnName
     * @return
     */
    private boolean isSensitiveColumn(String columnName) {
        if (columnName == null) {
            return false;
        }
        String lower = columnName.toLowerCase(Locale.ROOT);
        for (String keyword : SENSITIVE_KEYWORDS) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 去除语句尾部分号（部分驱动不接受）
     * @param sql
     * @return
     */
    private String stripTrailingSemicolon(String sql) {
        return sql.trim().endsWith(";") ? sql.trim().substring(0, sql.trim().length() - 1) : sql;
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
