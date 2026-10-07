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
package com.yangqiongai.ai.platform.api.agent;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.workspace.UserWorkspaceService;
import com.yangqiongai.ai.agent.local.workspace.gateway.PreviewOptions;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGateway;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceGatewayRouter;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.agent.local.workspace.gateway.WriteResult;
import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 用户工作区管理
 * @author yangqiong
 */
@Tag(name = "用户工作区管理接口")
@RestController
@RequestMapping("/api/workspace")
public class WorkspaceController {

    @Autowired
    private UserWorkspaceService userWorkspaceService;

    /**
     * 工作区网关组合路由器（按工作区类型分派文件操作网关）
     */
    @Autowired
    private WorkspaceGatewayRouter workspaceGatewayRouter;

    /**
     * 解析归属工作区引用
     * @param id
     * @param userId
     * @return
     */
    private WorkspaceRef resolveRef(Long id, String userId) {
        return WorkspaceRef.of(userWorkspaceService.requireOwned(id, userId));
    }

    /**
     * 查询当前用户工作区列表
     * @param userId
     * @return
     */
    @Operation(summary = "查询用户工作区列表", description = "按用户过滤，附目录健康状态")
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(
            @Parameter(name = "userId", description = "用户ID") @RequestParam String userId) {
        return ApiResult.ok(userWorkspaceService.list(userId));
    }

    /**
     * 创建工作区
     * @param request
     * @return
     */
    @Operation(summary = "创建工作区", description = "路径需存在（autoCreate=true时不存在则自动创建）且在白名单根内")
    @PostMapping
    public ApiResult<WorkspaceInfo> create(@RequestBody CreateWorkspaceRequest request) {
        return ApiResult.ok(userWorkspaceService.create(
                request.getUserId(),
                request.getName(),
                request.getRootPath(),
                request.getDescription(),
                request.getApprovalMode(),
                request.isAutoCreate(),
                request.getType()));
    }

    /**
     * 更新工作区
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "更新工作区", description = "仅名称/描述/审批层级/状态可更新，路径不可变更")
    @PutMapping("/{id}")
    public ApiResult<Void> update(@PathVariable Long id, @RequestBody UpdateWorkspaceRequest request) {
        userWorkspaceService.update(id, request.getUserId(), request.getName(),
                request.getDescription(), request.getApprovalMode(), request.getStatus());
        return ApiResult.ok(null);
    }

    /**
     * 删除工作区
     * @param id
     * @param userId
     * @return
     */
    @Operation(summary = "删除工作区", description = "仅移除登记，不删除磁盘目录")
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable Long id, @RequestParam String userId) {
        userWorkspaceService.delete(id, userId);
        return ApiResult.ok(null);
    }

    /**
     * 浏览本地目录供登记工作区手选
     * @param path
     * @return
     */
    @Operation(summary = "浏览本地目录", description = "仅返回子目录列表，path为空时返回根列表；白名单非空时限定根内浏览")
    @GetMapping("/browse-dirs")
    public ApiResult<List<Map<String, Object>>> browseDirs(
            @Parameter(name = "path", description = "目录绝对路径，空表示根列表") @RequestParam(required = false) String path) {
        return ApiResult.ok(userWorkspaceService.listDirectories(path));
    }

    /**
     * 懒加载列目录
     * @param id
     * @param userId
     * @param path
     * @return
     */
    @Operation(summary = "工作区文件树懒加载", description = "只列一级目录，path为空时列根目录")
    @GetMapping("/{id}/tree")
    public ApiResult<List<Map<String, Object>>> tree(@PathVariable Long id,
                                                     @RequestParam String userId,
                                                     @RequestParam(required = false) String path) {
        WorkspaceRef ref = resolveRef(id, userId);
        return ApiResult.ok(workspaceGatewayRouter.route(ref.getType()).tree(ref, path));
    }

    /**
     * 只读预览文件
     * @param id
     * @param userId
     * @param path
     * @return
     */
    @Operation(summary = "工作区文件只读预览", description = "表格/docx/zip/pdf结构化返回，文本限200KB，pdf限10MB")
    @GetMapping("/{id}/file")
    public ApiResult<Map<String, Object>> preview(@PathVariable Long id,
                                                  @RequestParam String userId,
                                                  @RequestParam String path) {
        WorkspaceRef ref = resolveRef(id, userId);
        return ApiResult.ok(workspaceGatewayRouter.route(ref.getType()).read(ref, path, PreviewOptions.empty()));
    }

    /**
     * 文本内容保存上限（与预览截断上限一致）
     */
    private static final int MAX_SAVE_BYTES = 200 * 1024;

    /**
     * 保存文本文件内容（仅覆盖预览类型为text的文件）
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "保存工作区文本文件内容", description = "覆盖工作区内已有文本文件，UTF-8编码写入，内容上限200KB，超限截断文件与二进制拒绝保存")
    @PutMapping("/{id}/file/content")
    public ApiResult<WriteResult> saveFileContent(@PathVariable Long id, @RequestBody FileContentSaveRequest request) {
        WorkspaceRef ref = resolveRef(id, request.getUserId());
        Map<String, Object> preview = workspaceGatewayRouter.route(ref.getType())
                .read(ref, request.getPath(), PreviewOptions.empty());
        if (!"text".equals(preview.get("type"))) {
            throw new IllegalArgumentException("仅文本文件支持在线编辑保存");
        }
        if (Boolean.TRUE.equals(preview.get("truncated"))) {
            throw new IllegalArgumentException("文件超200KB已截断，不支持在线编辑保存");
        }
        byte[] bytes = request.getContent() == null ? new byte[0] : request.getContent().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SAVE_BYTES) {
            throw new IllegalArgumentException("文件内容超过200KB，不支持在线保存");
        }
        return ApiResult.ok(workspaceGatewayRouter.route(ref.getType()).write(ref, request.getPath(), bytes));
    }

    /**
     * 复制文件或目录
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "复制工作区文件", description = "递归复制，目标同名冲突自动追加副本后缀")
    @PostMapping("/{id}/file/copy")
    public ApiResult<Map<String, Object>> copyFile(@PathVariable Long id, @RequestBody FileCopyRequest request) {
        WorkspaceRef ref = resolveRef(id, request.getUserId());
        return ApiResult.ok(workspaceGatewayRouter.route(ref.getType()).copy(ref, request.getPath(), request.getTargetDir()));
    }

    /**
     * 重命名文件或目录
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "重命名工作区文件", description = "新名称为纯名称，禁止路径分隔符与Windows保留字符")
    @PostMapping("/{id}/file/rename")
    public ApiResult<Void> renameFile(@PathVariable Long id, @RequestBody FileRenameRequest request) {
        WorkspaceRef ref = resolveRef(id, request.getUserId());
        workspaceGatewayRouter.route(ref.getType()).rename(ref, request.getPath(), request.getNewName());
        return ApiResult.ok(null);
    }

    /**
     * 删除文件或目录
     * @param id
     * @param userId
     * @param path
     * @return
     */
    @Operation(summary = "删除工作区文件", description = "目录递归删除，不允许删除工作区根目录")
    @DeleteMapping("/{id}/file")
    public ApiResult<Void> deleteFile(@PathVariable Long id,
                                      @RequestParam String userId,
                                      @RequestParam String path) {
        WorkspaceRef ref = resolveRef(id, userId);
        workspaceGatewayRouter.route(ref.getType()).delete(ref, path);
        return ApiResult.ok(null);
    }

    /**
     * 创建文件夹
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "工作区创建文件夹", description = "在指定父目录下创建新文件夹，同名冲突拒绝")
    @PostMapping("/{id}/file/mkdir")
    public ApiResult<Map<String, Object>> createDirectory(@PathVariable Long id, @RequestBody FileMkdirRequest request) {
        WorkspaceRef ref = resolveRef(id, request.getUserId());
        return ApiResult.ok(workspaceGatewayRouter.route(ref.getType()).mkdir(ref, request.getPath(), request.getName()));
    }

    /**
     * 移动文件或目录
     * @param id
     * @param request
     * @return
     */
    @Operation(summary = "移动工作区文件", description = "移动到目标目录，同名冲突拒绝，不允许移动到源目录自身内部")
    @PostMapping("/{id}/file/move")
    public ApiResult<Map<String, Object>> moveFile(@PathVariable Long id, @RequestBody FileMoveRequest request) {
        WorkspaceRef ref = resolveRef(id, request.getUserId());
        return ApiResult.ok(workspaceGatewayRouter.route(ref.getType()).move(ref, request.getPath(), request.getTargetDir()));
    }

    /**
     * 创建文件夹请求
     */
    public static class FileMkdirRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 父目录相对路径，空表示根目录
         */
        private String path;

        /**
         * 新文件夹名称
         */
        private String name;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /**
     * 移动文件请求
     */
    public static class FileMoveRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 源路径（相对工作区根）
         */
        private String path;

        /**
         * 目标目录（相对工作区根，空表示根目录）
         */
        private String targetDir;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTargetDir() {
            return targetDir;
        }

        public void setTargetDir(String targetDir) {
            this.targetDir = targetDir;
        }
    }

    /**
     * 创建工作区请求
     */
    public static class CreateWorkspaceRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 工作区名称
         */
        private String name;

        /**
         * 根目录绝对路径
         */
        private String rootPath;

        /**
         * 描述
         */
        private String description;

        /**
         * 审批层级：MANUAL/AUTO/FULL_ACCESS/CUSTOM
         */
        private String approvalMode;

        /**
         * 工作区类型（当前仅支持SERVER，空白默认SERVER）
         */
        private String type;

        /**
         * 目录不存在时是否自动创建
         */
        private boolean autoCreate;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getApprovalMode() {
            return approvalMode;
        }

        public void setApprovalMode(String approvalMode) {
            this.approvalMode = approvalMode;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public boolean isAutoCreate() {
            return autoCreate;
        }

        public void setAutoCreate(boolean autoCreate) {
            this.autoCreate = autoCreate;
        }
    }

    /**
     * 更新工作区请求
     */
    public static class UpdateWorkspaceRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 工作区名称
         */
        private String name;

        /**
         * 描述
         */
        private String description;

        /**
         * 审批层级：MANUAL/AUTO/FULL_ACCESS/CUSTOM
         */
        private String approvalMode;

        /**
         * 状态（1启用0停用）
         */
        private Integer status;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getApprovalMode() {
            return approvalMode;
        }

        public void setApprovalMode(String approvalMode) {
            this.approvalMode = approvalMode;
        }

        public Integer getStatus() {
            return status;
        }

        public void setStatus(Integer status) {
            this.status = status;
        }
    }

    /**
     * 保存文本内容请求
     */
    public static class FileContentSaveRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 文件路径（相对工作区根）
         */
        private String path;

        /**
         * 文本内容（UTF-8）
         */
        private String content;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }

    /**
     * 复制文件请求
     */
    public static class FileCopyRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 源路径（相对工作区根）
         */
        private String path;

        /**
         * 目标目录（相对工作区根，空表示根目录）
         */
        private String targetDir;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getTargetDir() {
            return targetDir;
        }

        public void setTargetDir(String targetDir) {
            this.targetDir = targetDir;
        }
    }

    /**
     * 重命名文件请求
     */
    public static class FileRenameRequest {

        /**
         * 用户ID
         */
        private String userId;

        /**
         * 源路径（相对工作区根）
         */
        private String path;

        /**
         * 新名称（纯名称，不含路径）
         */
        private String newName;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public String getNewName() {
            return newName;
        }

        public void setNewName(String newName) {
            this.newName = newName;
        }
    }
}
