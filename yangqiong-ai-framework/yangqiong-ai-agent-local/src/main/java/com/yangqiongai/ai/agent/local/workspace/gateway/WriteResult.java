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
package com.yangqiongai.ai.agent.local.workspace.gateway;

/**
 * 文件写入结果
 * @author yangqiong
 */
public class WriteResult {

    /**
     * 写入后的工作区相对路径
     */
    private String path;

    /**
     * 写入字节数
     */
    private long size;

    public WriteResult() {
    }

    private WriteResult(String path, long size) {
        this.path = path;
        this.size = size;
    }

    /**
     * 构建写入结果
     * @param path
     * @param size
     * @return
     */
    public static WriteResult of(String path, long size) {
        return new WriteResult(path, size);
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
