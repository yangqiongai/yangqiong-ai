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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;

import java.util.List;
import java.util.Objects;

/**
 * 数据库连接器实例配置
 * <p>
 * 解析实例config_json中的非敏感参数：行数截断、执行超时、表白名单，
 * 全局配置兜底默认值，单实例覆盖并施加硬上限保护。
 * </p>
 * @author yangqiong
 * @param maxRows 查询结果最大行数
 * @param timeoutSeconds 执行超时秒数
 * @param schemaWhitelist 表白名单（空=不限制）
 */
public record DatabaseInstanceConfig(int maxRows, int timeoutSeconds, List<String> schemaWhitelist) {

    /**
     * 行数硬上限（防单实例配置过大拖垮源库）
     */
    private static final int MAX_ROWS_LIMIT = 5000;

    /**
     * 超时硬上限（秒）
     */
    private static final int TIMEOUT_LIMIT = 60;

    /**
     * 从实例config_json解析配置（全局默认值兜底）
     * @param instance
     * @param properties
     * @return
     */
    public static DatabaseInstanceConfig parse(ConnectorInstance instance, ConnectorProperties properties) {
        int maxRows = properties.getDatabaseMaxRows();
        int timeoutSeconds = properties.getDatabaseTimeoutSeconds();
        List<String> whitelist = List.of();
        String configJson = instance == null ? null : instance.getConfigJson();
        if (configJson != null && !configJson.isBlank()) {
            try {
                JSONObject json = JSON.parseObject(configJson);
                Integer rows = json.getInteger("maxRows");
                if (rows != null && rows > 0) {
                    maxRows = rows;
                }
                Integer timeout = json.getInteger("timeoutSeconds");
                if (timeout != null && timeout > 0) {
                    timeoutSeconds = timeout;
                }
                JSONArray tables = json.getJSONArray("schemaWhitelist");
                if (tables != null) {
                    whitelist = tables.stream()
                            .filter(Objects::nonNull)
                            .map(String::valueOf)
                            .map(String::trim)
                            .filter(item -> !item.isEmpty())
                            .toList();
                }
            } catch (Exception ignored) {
                // 配置JSON非法时使用全局默认值
            }
        }
        return new DatabaseInstanceConfig(
                Math.min(maxRows, MAX_ROWS_LIMIT),
                Math.min(Math.max(timeoutSeconds, 1), TIMEOUT_LIMIT),
                whitelist);
    }
}
