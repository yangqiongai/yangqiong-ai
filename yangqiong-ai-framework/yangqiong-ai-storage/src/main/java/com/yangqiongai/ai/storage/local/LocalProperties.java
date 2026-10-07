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
package com.yangqiongai.ai.storage.local;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本地文件存储配置属性
 * @author yangqiong
 */
@ConfigurationProperties(prefix = "ai.storage.local")
public class LocalProperties {

    /**
     * 文件存储根目录
     */
    private String baseDir = "./data/files";

    public String getBaseDir() {
        return baseDir;
    }

    public void setBaseDir(String baseDir) {
        this.baseDir = baseDir;
    }
}
