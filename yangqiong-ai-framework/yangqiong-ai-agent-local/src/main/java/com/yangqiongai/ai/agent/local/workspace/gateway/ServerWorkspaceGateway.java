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

import com.yangqiongai.ai.agent.local.workspace.UserWorkspaceService;

import java.util.List;
import java.util.Map;

/**
 * 服务器工作区网关
 * <p>
 * 包装UserWorkspaceService的文件操作能力，归属校验、roots白名单与路径越界校验
 * 保留在服务调用链上，SERVER类型行为与现状完全一致。
 * </p>
 * @author yangqiong
 */
public class ServerWorkspaceGateway implements WorkspaceGateway {

    private final UserWorkspaceService userWorkspaceService;

    /**
     * 包装用户工作区服务
     * @param userWorkspaceService
     */
    public ServerWorkspaceGateway(UserWorkspaceService userWorkspaceService) {
        this.userWorkspaceService = userWorkspaceService;
    }

    /**
     * 是否支持指定工作区类型
     * @param type
     * @return
     */
    @Override
    public boolean supports(String type) {
        return WorkspaceRef.TYPE_SERVER.equalsIgnoreCase(type);
    }

    /**
     * 列目录树（懒加载一级）
     * @param ref
     * @param dir
     * @return
     */
    @Override
    public List<Map<String, Object>> tree(WorkspaceRef ref, String dir) {
        return userWorkspaceService.tree(ref.getWorkspaceId(), ref.getUserId(), dir);
    }

    /**
     * 读文件内容（含结构化预览，服务器网关按服务端固定上限执行，忽略自定义上限）
     * @param ref
     * @param path
     * @param options
     * @return
     */
    @Override
    public Map<String, Object> read(WorkspaceRef ref, String path, PreviewOptions options) {
        return userWorkspaceService.preview(ref.getWorkspaceId(), ref.getUserId(), path);
    }

    /**
     * 写文件内容（创建或覆盖，自动补建父目录）
     * @param ref
     * @param path
     * @param content
     * @return
     */
    @Override
    public WriteResult write(WorkspaceRef ref, String path, byte[] content) {
        Map<String, Object> result = userWorkspaceService.writeFile(
                ref.getWorkspaceId(), ref.getUserId(), path, content);
        return WriteResult.of(String.valueOf(result.get("path")), (Long) result.get("size"));
    }

    /**
     * 在工作区内创建文件夹
     * @param ref
     * @param path
     * @param name
     * @return
     */
    @Override
    public Map<String, Object> mkdir(WorkspaceRef ref, String path, String name) {
        return userWorkspaceService.createDirectory(ref.getWorkspaceId(), ref.getUserId(), path, name);
    }

    /**
     * 移动文件或目录到目标目录
     * @param ref
     * @param path
     * @param targetDir
     * @return
     */
    @Override
    public Map<String, Object> move(WorkspaceRef ref, String path, String targetDir) {
        return userWorkspaceService.moveFile(ref.getWorkspaceId(), ref.getUserId(), path, targetDir);
    }

    /**
     * 重命名文件或目录
     * @param ref
     * @param path
     * @param name
     */
    @Override
    public void rename(WorkspaceRef ref, String path, String name) {
        userWorkspaceService.renameFile(ref.getWorkspaceId(), ref.getUserId(), path, name);
    }

    /**
     * 删除文件或目录（目录递归删除）
     * @param ref
     * @param path
     */
    @Override
    public void delete(WorkspaceRef ref, String path) {
        userWorkspaceService.deleteFile(ref.getWorkspaceId(), ref.getUserId(), path);
    }

    /**
     * 复制文件或目录到目标目录
     * @param ref
     * @param path
     * @param targetDir
     * @return
     */
    @Override
    public Map<String, Object> copy(WorkspaceRef ref, String path, String targetDir) {
        return userWorkspaceService.copyFile(ref.getWorkspaceId(), ref.getUserId(), path, targetDir);
    }
}
