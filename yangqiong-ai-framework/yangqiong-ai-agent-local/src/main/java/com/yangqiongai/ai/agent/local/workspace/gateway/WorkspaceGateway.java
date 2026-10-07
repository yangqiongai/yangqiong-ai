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

import java.util.List;
import java.util.Map;

/**
 * 工作区文件操作网关
 * <p>
 * 收拢工作区文件操作出口，按工作区类型（SERVER/CONNECTOR/BROWSER）分派到对应执行位置，
 * 后续连接器与浏览器桥实现本SPI注册bean即可自动接入路由。
 * </p>
 * @author yangqiong
 */
public interface WorkspaceGateway {

    /**
     * 是否支持指定工作区类型
     * @param type
     * @return
     */
    boolean supports(String type);

    /**
     * 列目录树（懒加载一级）
     * @param ref
     * @param dir
     * @return
     */
    List<Map<String, Object>> tree(WorkspaceRef ref, String dir);

    /**
     * 读文件内容（含结构化预览）
     * @param ref
     * @param path
     * @param options
     * @return
     */
    Map<String, Object> read(WorkspaceRef ref, String path, PreviewOptions options);

    /**
     * 写文件内容（创建或覆盖，自动补建父目录）
     * @param ref
     * @param path
     * @param content
     * @return
     */
    WriteResult write(WorkspaceRef ref, String path, byte[] content);

    /**
     * 在工作区内创建文件夹
     * @param ref
     * @param path
     * @param name
     * @return
     */
    Map<String, Object> mkdir(WorkspaceRef ref, String path, String name);

    /**
     * 移动文件或目录到目标目录
     * @param ref
     * @param path
     * @param targetDir
     * @return
     */
    Map<String, Object> move(WorkspaceRef ref, String path, String targetDir);

    /**
     * 重命名文件或目录
     * @param ref
     * @param path
     * @param name
     */
    void rename(WorkspaceRef ref, String path, String name);

    /**
     * 删除文件或目录（目录递归删除）
     * @param ref
     * @param path
     */
    void delete(WorkspaceRef ref, String path);

    /**
     * 复制文件或目录到目标目录
     * @param ref
     * @param path
     * @param targetDir
     * @return
     */
    Map<String, Object> copy(WorkspaceRef ref, String path, String targetDir);
}
