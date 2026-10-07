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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.service.ConnectorInstanceLifecycleListener;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据库连接器连接池管理
 * <p>
 * 平台动态数据源体系外的独立只读小连接池（HikariCP），按实例维护，
 * 凭证或配置指纹变化时自动重建旧池；实例停用/删除经生命周期监听即时回收，
 * 防止连接悬挂拖垮源库。
 * </p>
 * @author yangqiong
 */
public class DatabaseConnectionManager implements ConnectorInstanceLifecycleListener {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConnectionManager.class);

    /**
     * 单实例连接池最大连接数
     */
    private static final int POOL_MAX_SIZE = 3;

    /**
     * 空闲连接回收时间（毫秒）
     */
    private static final long IDLE_TIMEOUT_MS = 60_000L;

    /**
     * 连接池索引（instanceCode -> 池条目）
     */
    private final Map<String, PoolEntry> pools = new ConcurrentHashMap<>();

    /**
     * 获取实例只读连接（池按凭证/配置指纹自动重建）
     * @param instance
     * @param config 实例配置
     * @param credential 解密后的凭证视图（jdbcUrl/username/password）
     * @return
     * @throws SQLException 连接获取失败
     */
    public Connection getConnection(ConnectorInstance instance, DatabaseInstanceConfig config,
                                    ConnectorCredentialView credential) throws SQLException {
        String jdbcUrl = credential.get("jdbcUrl");
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new AiException(AiErrorCode.PARAM_ERROR, "连接器凭证缺少jdbcUrl");
        }
        String username = credential.get("username");
        String password = credential.get("password");
        String fingerprint = jdbcUrl + "|" + username + "|" + (password == null ? "" : password)
                + "|" + config.timeoutSeconds();
        PoolEntry entry = pools.compute(instance.getInstanceCode(), (code, existing) -> {
            if (existing != null && existing.fingerprint.equals(fingerprint)) {
                return existing;
            }
            closeQuietly(existing);
            HikariDataSource dataSource = createDataSource(code, jdbcUrl, username, password, config);
            log.info("创建数据库连接器只读连接池: instance={}", code);
            return new PoolEntry(dataSource, fingerprint);
        });
        return entry.dataSource.getConnection();
    }

    /**
     * 凭证连通性测试（独立直连验证，不占用实例池）
     * @param jdbcUrl
     * @param username
     * @param password
     * @param timeoutSeconds 登录超时秒数
     * @return 失败原因，通过时返回null
     */
    public String testConnection(String jdbcUrl, String username, String password, int timeoutSeconds) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            return "jdbcUrl不能为空";
        }
        DriverManager.setLoginTimeout(Math.max(timeoutSeconds, 1));
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password);
             Statement statement = connection.createStatement()) {
            statement.execute("SELECT 1");
            return null;
        } catch (Exception e) {
            String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            return message.length() > 300 ? message.substring(0, 300) : message;
        }
    }

    /**
     * 创建独立只读小连接池
     * @param instanceCode 实例编码
     * @param jdbcUrl
     * @param username 可空
     * @param password 可空
     * @param config 实例配置
     * @return
     */
    private HikariDataSource createDataSource(String instanceCode, String jdbcUrl, String username,
                                              String password, DatabaseInstanceConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(jdbcUrl);
        hikari.setUsername(username);
        hikari.setPassword(password);
        hikari.setPoolName("connector-db-" + instanceCode);
        hikari.setMaximumPoolSize(POOL_MAX_SIZE);
        hikari.setMinimumIdle(0);
        hikari.setReadOnly(true);
        hikari.setIdleTimeout(IDLE_TIMEOUT_MS);
        hikari.setConnectionTimeout((config.timeoutSeconds() + 5) * 1000L);
        hikari.setValidationTimeout(5_000L);
        return new HikariDataSource(hikari);
    }

    /**
     * 回收实例连接池（实例停用/删除/重建时调用）
     * @param instanceCode 实例编码
     */
    public void evict(String instanceCode) {
        PoolEntry entry = pools.remove(instanceCode);
        closeQuietly(entry);
        if (entry != null) {
            log.info("回收数据库连接器连接池: instance={}", instanceCode);
        }
    }

    @Override
    public void onChanged(ConnectorInstance instance) {
        if (instance != null && instance.getInstanceCode() != null) {
            evict(instance.getInstanceCode());
        }
    }

    @Override
    public void onDeleted(String instanceCode) {
        if (instanceCode != null) {
            evict(instanceCode);
        }
    }

    /**
     * 关闭全部连接池（容器停机时调用）
     */
    public void close() {
        for (String instanceCode : pools.keySet()) {
            evict(instanceCode);
        }
    }

    /**
     * 静默关闭池条目
     * @param entry 可空
     */
    private void closeQuietly(PoolEntry entry) {
        if (entry != null) {
            entry.dataSource.close();
        }
    }

    /**
     * 连接池条目（池与配置指纹）
     * @param dataSource
     * @param fingerprint 凭证/超时指纹，变更即重建
     */
    private record PoolEntry(HikariDataSource dataSource, String fingerprint) {
    }
}
