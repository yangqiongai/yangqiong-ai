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

import com.yangqiongai.ai.agent.runtime.tool.AgentTool;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorDescriptor;
import com.yangqiongai.ai.platform.connector.spi.ConnectorField;
import com.yangqiongai.ai.platform.connector.spi.ConnectorInboundGateway;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import com.yangqiongai.ai.platform.connector.spi.ConnectorToolDefinition;

import java.util.List;

/**
 * 数据库查询连接器
 * <p>
 * 面向业务库的只读SQL问答：凭证托管JDBC参数，实例配置行数/超时/表白名单，
 * 产出query_database与describe_tables两个安全封装工具，不提供入站网关。
 * </p>
 * @author yangqiong
 */
public class DatabaseProvider implements ConnectorProvider {

    /**
     * 提供商标识
     */
    public static final String PROVIDER_CODE = "database";

    /**
     * query_database参数Schema
     */
    private static final String QUERY_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"sql\":{\"type\":\"string\","
                    + "\"description\":\"只读SQL语句\"}},\"required\":[\"sql\"]}";

    /**
     * describe_tables参数Schema
     */
    private static final String DESCRIBE_SCHEMA =
            "{\"type\":\"object\",\"properties\":{\"tables\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},"
                    + "\"description\":\"待描述的表名列表，留空时描述白名单内全部表\"}}}";

    /**
     * 连接器配置
     */
    private final ConnectorProperties properties;

    /**
     * 连接池管理
     */
    private final DatabaseConnectionManager connectionManager;

    public DatabaseProvider(ConnectorProperties properties, DatabaseConnectionManager connectionManager) {
        this.properties = properties;
        this.connectionManager = connectionManager;
    }

    @Override
    public String providerCode() {
        return PROVIDER_CODE;
    }

    @Override
    public ConnectorDescriptor descriptor() {
        return ConnectorDescriptor.of("数据库查询", "database")
                .icon("database")
                .description("面向业务库的只读SQL问答连接器：独立只读连接池、表白名单、"
                        + "行数截断与敏感列脱敏，配套表结构描述工具")
                .credentialField(ConnectorField.of("jdbcUrl", "JDBC地址").required()
                        .placeholder("jdbc:mysql://host:3306/db?useSSL=false").build())
                .credentialField(ConnectorField.of("username", "用户名").required().build())
                .credentialField(ConnectorField.of("password", "密码").required().secret().build())
                .configField(ConnectorField.of("maxRows", "最大返回行数")
                        .placeholder("默认" + properties.getDatabaseMaxRows()).build())
                .configField(ConnectorField.of("timeoutSeconds", "执行超时秒数")
                        .placeholder("默认" + properties.getDatabaseTimeoutSeconds()).build())
                .configField(ConnectorField.of("schemaWhitelist", "表白名单")
                        .placeholder("逗号分隔，如 order_info,user_info；留空不限制").build())
                .tool(ConnectorToolDefinition.of(DatabaseQueryTool.NAME, "执行只读SQL查询并返回结果表格")
                        .parametersSchema(QUERY_SCHEMA).build())
                .tool(ConnectorToolDefinition.of(DescribeTablesTool.NAME, "查询授权表的表结构")
                        .parametersSchema(DESCRIBE_SCHEMA).build())
                .build();
    }

    @Override
    public List<AgentTool> createTools(ConnectorInstance instance, ConnectorCredentialView credential) {
        credential.validate(descriptor().getCredentialFields());
        DatabaseInstanceConfig config = DatabaseInstanceConfig.parse(instance, properties);
        return List.of(
                new DatabaseQueryTool(instance, config, credential, connectionManager),
                new DescribeTablesTool(instance, config, credential, connectionManager));
    }

    @Override
    public ConnectorInboundGateway createGateway(ConnectorInstance instance, ConnectorCredentialView credential) {
        return null;
    }

    @Override
    public String testCredential(ConnectorCredentialView credential) {
        return connectionManager.testConnection(
                credential.get("jdbcUrl"), credential.get("username"), credential.get("password"), 5);
    }
}
