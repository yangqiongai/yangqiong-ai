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
package com.yangqiongai.ai.platform.connector.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 连接器配置
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.connector")
public class ConnectorProperties {

    /**
     * 实例工具装配缓存TTL（毫秒）
     */
    private long toolCacheMs = 60_000;

    /**
     * 入站消息异步处理线程数
     */
    private int gatewayAsyncWorkers = 4;

    /**
     * 数据库连接器默认行数截断
     */
    private int databaseMaxRows = 200;

    /**
     * 数据库连接器执行超时（秒）
     */
    private int databaseTimeoutSeconds = 10;

    /**
     * 扫描件OCR兜底
     */
    private boolean docparserOcrEnabled = true;

    /**
     * 文档解析分块尺寸（字符）
     */
    private int docparserChunkSize = 500;

    /**
     * 文档解析分块重叠（字符）
     */
    private int docparserChunkOverlap = 50;

    /**
     * 文档解析最大文件大小（MB）
     */
    private int docparserMaxFileSizeMb = 20;

    public long getToolCacheMs() {
        return toolCacheMs;
    }

    public void setToolCacheMs(long toolCacheMs) {
        this.toolCacheMs = toolCacheMs;
    }

    public int getGatewayAsyncWorkers() {
        return gatewayAsyncWorkers;
    }

    public void setGatewayAsyncWorkers(int gatewayAsyncWorkers) {
        this.gatewayAsyncWorkers = gatewayAsyncWorkers;
    }

    public int getDatabaseMaxRows() {
        return databaseMaxRows;
    }

    public void setDatabaseMaxRows(int databaseMaxRows) {
        this.databaseMaxRows = databaseMaxRows;
    }

    public int getDatabaseTimeoutSeconds() {
        return databaseTimeoutSeconds;
    }

    public void setDatabaseTimeoutSeconds(int databaseTimeoutSeconds) {
        this.databaseTimeoutSeconds = databaseTimeoutSeconds;
    }

    public boolean isDocparserOcrEnabled() {
        return docparserOcrEnabled;
    }

    public void setDocparserOcrEnabled(boolean docparserOcrEnabled) {
        this.docparserOcrEnabled = docparserOcrEnabled;
    }

    public int getDocparserChunkSize() {
        return docparserChunkSize;
    }

    public void setDocparserChunkSize(int docparserChunkSize) {
        this.docparserChunkSize = docparserChunkSize;
    }

    public int getDocparserChunkOverlap() {
        return docparserChunkOverlap;
    }

    public void setDocparserChunkOverlap(int docparserChunkOverlap) {
        this.docparserChunkOverlap = docparserChunkOverlap;
    }

    public int getDocparserMaxFileSizeMb() {
        return docparserMaxFileSizeMb;
    }

    public void setDocparserMaxFileSizeMb(int docparserMaxFileSizeMb) {
        this.docparserMaxFileSizeMb = docparserMaxFileSizeMb;
    }
}
