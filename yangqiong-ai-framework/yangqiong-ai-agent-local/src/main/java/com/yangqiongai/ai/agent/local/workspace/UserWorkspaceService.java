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
package com.yangqiongai.ai.agent.local.workspace;

import com.yangqiongai.ai.agent.local.model.WorkspaceInfo;
import com.yangqiongai.ai.agent.local.repository.UserWorkspaceRepository;
import com.yangqiongai.ai.agent.local.workspace.docx.DocxReader;
import com.yangqiongai.ai.agent.local.workspace.excel.ExcelReader;
import com.yangqiongai.ai.agent.local.workspace.excel.PlainTextTableReader;
import com.yangqiongai.ai.agent.local.workspace.gateway.WorkspaceRef;
import com.yangqiongai.ai.common.utils.FileOperatorUtils;
import com.yangqiongai.ai.common.utils.WorkspacePathUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 用户工作区管理
 * @author yangqiong
 */
public class UserWorkspaceService {

    /**
     * 文件预览最大字节数
     */
    private static final int MAX_PREVIEW_BYTES = 200 * 1024;

    /**
     * 图片预览最大字节数（base64 内联上限）
     */
    private static final long MAX_IMAGE_PREVIEW_BYTES = 5L * 1024 * 1024;

    /**
     * PDF预览最大字节数（base64 内联上限）
     */
    private static final long MAX_PDF_PREVIEW_BYTES = 10L * 1024 * 1024;

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    /**
     * zip条目清单读回上限
     */
    private static final int MAX_ZIP_ENTRIES = 500;

    /**
     * 虚拟根scheme前缀（如connector://、browser://，根路径由对应网关执行端保管，非本地磁盘路径）
     */
    private static final Pattern VIRTUAL_ROOT_SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.\\-]*://");

    /**
     * 可内联预览的图片扩展名与MIME映射
     */
    private static final Map<String, String> IMAGE_MIME = Map.of(
            ".png", "image/png",
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".bmp", "image/bmp",
            ".svg", "image/svg+xml"
    );

    /**
     * 暂不支持前端预览的二进制扩展名（docx/zip/pdf已支持结构化预览）
     */
    private static final Set<String> BINARY_EXTENSIONS = Set.of(
            ".doc", ".rar", ".7z", ".jar", ".exe", ".dll", ".bin", ".dat"
    );

    /**
     * 合法审批层级
     */
    private static final Set<String> APPROVAL_MODES = Set.of("MANUAL", "AUTO", "FULL_ACCESS", "CUSTOM");

    @Autowired
    private UserWorkspaceRepository userWorkspaceRepository;

    @Autowired
    private WorkspaceProperties workspaceProperties;

    /**
     * 事件发布器（允许为空，纯单测环境无Spring上下文时不发布）
     */
    @Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    /**
     * 查询用户工作区列表（附目录健康状态）
     * @param userId
     * @return
     */
    public List<Map<String, Object>> list(String userId) {
        checkEnabled();
        List<Map<String, Object>> result = new ArrayList<>();
        for (WorkspaceInfo info : userWorkspaceRepository.listByUserId(userId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            // 类型空值默认SERVER，与登记约定保持一致
            String type = info.getType() == null ? WorkspaceRef.TYPE_SERVER : info.getType();
            item.put("id", info.getId());
            item.put("name", info.getName());
            // SERVER类型根路径是服务端内部目录，统一输出server://+目录名固定格式，不对外暴露服务器部署路径
            item.put("rootPath", WorkspaceRef.TYPE_SERVER.equals(type)
                    ? "server://" + lastSegment(info.getRootPath()) : info.getRootPath());
            item.put("description", info.getDescription());
            item.put("type", type);
            item.put("approvalMode", info.getApprovalMode());
            item.put("status", info.getStatus());
            item.put("createTime", info.getCreateTime());
            item.put("updateTime", info.getUpdateTime());
            // 本地目录健康检查仅适用SERVER类型，连接器/浏览器桥根路径是不透明URI不做本地检查
            item.put("valid", WorkspaceRef.TYPE_SERVER.equals(type)
                    ? WorkspacePathUtils.isDirectory(info.getRootPath()) : true);
            result.add(item);
        }
        return result;
    }

    /**
     * 浏览本地目录供登记工作区手选（仅返回子目录，白名单非空时限定根内浏览）
     * @param path 目录绝对路径，空表示查询根列表（盘符/根目录）
     * @return
     */
    public List<Map<String, Object>> listDirectories(String path) {
        checkEnabled();
        List<Map<String, Object>> result = new ArrayList<>();
        if (path == null || path.isBlank()) {
            // 白名单非空时根列表直接给出白名单根，保证浏览起点始终在允许范围内
            if (!workspaceProperties.getRoots().isEmpty()) {
                for (String root : workspaceProperties.getRoots()) {
                    String canonicalRoot = WorkspacePathUtils.canonical(root);
                    if (canonicalRoot != null) {
                        result.add(directoryItem(canonicalRoot));
                    }
                }
                return result;
            }
            for (File root : File.listRoots()) {
                result.add(directoryItem(root.getPath()));
            }
            return result;
        }
        File dir;
        try {
            dir = new File(path).getCanonicalFile();
        } catch (IOException e) {
            throw new IllegalArgumentException("目录路径无效");
        }
        // 白名单非空时浏览范围限定在白名单根内，避免远端部署暴露服务器目录结构
        if (!workspaceProperties.getRoots().isEmpty() && !isUnderAnyRoot(dir.getPath())) {
            throw new IllegalArgumentException("仅可浏览白名单根内的目录");
        }
        File[] children = dir.listFiles(File::isDirectory);
        if (children == null) {
            throw new IllegalArgumentException("目录不存在或不可访问");
        }
        for (File child : children) {
            result.add(directoryItem(child.getPath()));
        }
        return result;
    }

    /**
     * 取路径末段目录名（server://展示格式用，不含任何父路径信息）
     * @param path
     * @return
     */
    private String lastSegment(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int idx = normalized.lastIndexOf('/');
        return idx >= 0 ? normalized.substring(idx + 1) : normalized;
    }

    /**
     * 构建目录浏览条目（path完整路径 + name展示名）
     * @param path
     * @return
     */
    private Map<String, Object> directoryItem(String path) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("path", path);
        String name = path;
        int idx = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        if (idx >= 0 && idx < path.length() - 1) {
            name = path.substring(idx + 1);
        }
        item.put("name", name);
        return item;
    }

    /**
     * 创建工作区
     * @param userId
     * @param name
     * @param rootPath
     * @param description
     * @param approvalMode
     * @param autoCreate 目录不存在时是否自动创建（默认目录一键创建用）
     * @return
     */
    public WorkspaceInfo create(String userId, String name, String rootPath,
                                String description, String approvalMode, boolean autoCreate) {
        return create(userId, name, rootPath, description, approvalMode, autoCreate, null);
    }

    /**
     * 创建工作区（带类型，当前仅支持SERVER类型登记）
     * @param userId
     * @param name
     * @param rootPath
     * @param description
     * @param approvalMode
     * @param autoCreate 目录不存在时是否自动创建（默认目录一键创建用）
     * @param type 工作区类型，空白默认SERVER
     * @return
     */
    public WorkspaceInfo create(String userId, String name, String rootPath,
                                String description, String approvalMode, boolean autoCreate, String type) {
        checkEnabled();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("工作区名称不能为空");
        }
        if (rootPath == null || rootPath.isBlank()) {
            throw new IllegalArgumentException("工作区路径不能为空");
        }
        String mode = normalizeApprovalMode(approvalMode);
        String workspaceType = normalizeType(type);
        if (userWorkspaceRepository.countByUserId(userId) >= workspaceProperties.getMaxPerUser()) {
            throw new IllegalArgumentException("工作区数量已达上限（" + workspaceProperties.getMaxPerUser() + "个）");
        }

        // 目录不存在时按需自动创建，常规新增仍要求目录已存在
        if (!WorkspacePathUtils.isDirectory(rootPath)) {
            if (autoCreate && WorkspacePathUtils.createDirectories(rootPath)) {
                // 自动创建成功，继续走归一化
            } else {
                throw new IllegalArgumentException("目录不存在，请先创建目录");
            }
        }

        String canonical = WorkspacePathUtils.canonical(rootPath);
        if (canonical == null) {
            throw new IllegalArgumentException("目录路径非法或无法访问");
        }
        checkRootsWhitelist(canonical);
        if (userWorkspaceRepository.existsByRootPath(canonical)) {
            throw new IllegalArgumentException("该目录已被登记为工作区");
        }

        WorkspaceInfo info = new WorkspaceInfo();
        info.setUserId(userId);
        info.setName(name.trim());
        info.setRootPath(canonical);
        info.setDescription(description);
        info.setType(workspaceType);
        info.setApprovalMode(mode);
        info.setStatus(1);
        userWorkspaceRepository.save(info);
        return info;
    }

    /**
     * 更新工作区（归属校验）
     * @param id
     * @param userId
     * @param name
     * @param description
     * @param approvalMode
     * @param status
     */
    public void update(Long id, String userId, String name, String description, String approvalMode, Integer status) {
        WorkspaceInfo info = requireOwned(id, userId);
        if (name != null && !name.isBlank()) {
            info.setName(name.trim());
        }
        if (description != null) {
            info.setDescription(description);
        }
        if (approvalMode != null) {
            info.setApprovalMode(normalizeApprovalMode(approvalMode));
        }
        if (status != null) {
            info.setStatus(status);
        }
        userWorkspaceRepository.updateById(info);
    }

    /**
     * 删除工作区（归属校验，仅移除登记不影响磁盘目录；发布删除事件供扩展侧级联清理）
     * @param id
     * @param userId
     */
    public void delete(Long id, String userId) {
        WorkspaceInfo info = requireOwned(id, userId);
        userWorkspaceRepository.deleteById(id);
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new UserWorkspaceDeletedEvent(info, userId));
        }
    }

    /**
     * 按ID与用户取启用中的工作区（Agent运行时注入入口）
     * @param id
     * @param userId
     * @return 校验失败返回null，由调用方降级处理
     */
    public WorkspaceInfo resolveEnabled(Long id, String userId) {
        if (id == null || !workspaceProperties.isEnabled()) {
            return null;
        }
        WorkspaceInfo info = userWorkspaceRepository.findByIdAndUserId(id, userId);
        if (info == null || info.getStatus() == null || info.getStatus() != 1) {
            return null;
        }
        // 虚拟根（连接器/浏览器桥）不做本地路径校验，路径安全由对应网关执行端约束
        if (!isVirtualRoot(info.getRootPath())) {
            // 运行时再校验：目录可能被移动/符号链接替换，canonical归一化后必须与登记值一致且仍在白名单内
            String canonical = WorkspacePathUtils.canonical(info.getRootPath());
            if (canonical == null || !canonical.equals(info.getRootPath())) {
                return null;
            }
            if (!workspaceProperties.getRoots().isEmpty() && !isUnderAnyRoot(canonical)) {
                return null;
            }
        }
        return info;
    }

    /**
     * 是否虚拟工作区根（带URL式scheme前缀，非本地磁盘路径，文件操作经对应网关执行端完成）
     * @param rootPath
     * @return
     */
    public static boolean isVirtualRoot(String rootPath) {
        return rootPath != null && VIRTUAL_ROOT_SCHEME.matcher(rootPath).find();
    }

    /**
     * 懒加载列目录（只列一级，归属校验+路径越界校验）
     * @param id
     * @param userId
     * @param path 相对工作区根的路径，空表示根目录
     * @return
     */
    public List<Map<String, Object>> tree(Long id, String userId, String path) {
        Path dir = resolveInWorkspace(id, userId, path);
        // 相对路径统一以工作区根为基准（懒加载子目录时dir已非根，必须补回父前缀，否则path越级指向根目录同名文件）
        String parent = (path == null || path.isBlank()) ? "" : path.replace('\\', '/') + "/";
        List<Map<String, Object>> nodes = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.sorted((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) {
                    return aDir ? -1 : 1;
                }
                return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString());
            }).forEach(p -> {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("name", p.getFileName().toString());
                node.put("path", parent + p.getFileName().toString());
                node.put("dir", Files.isDirectory(p));
                if (!Files.isDirectory(p)) {
                    try {
                        node.put("size", Files.size(p));
                    } catch (IOException ignored) {
                        node.put("size", 0L);
                    }
                }
                nodes.add(node);
            });
        } catch (IOException e) {
            throw new IllegalArgumentException("读取目录失败：" + e.getMessage());
        }
        return nodes;
    }

    /**
     * 只读预览文件内容（归属校验+路径越界校验）
     * <p>表格类按魔数分流POI/文本解析，docx返回段落表格结构，zip返回条目清单，
     * pdf与图片走base64内联，其余二进制返回占位，文本限200KB</p>
     * @param id
     * @param userId
     * @param path 相对工作区根的路径
     * @return
     */
    public Map<String, Object> preview(Long id, String userId, String path) {
        Path file = resolveInWorkspace(id, userId, path);
        if (Files.isDirectory(file)) {
            throw new IllegalArgumentException("目标为目录，仅支持文件预览");
        }
        long size;
        try {
            size = Files.size(file);
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败：" + e.getMessage());
        }
        String name = file.getFileName().toString();
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        // Office锁文件（~$开头）直接占位，避免解析报错
        if (name.startsWith("~$")) {
            return binaryPlaceholder(name, path, size);
        }
        if (isSpreadsheetName(lower)) {
            return previewSpreadsheet(name, path, size, file);
        }
        int dot = lower.lastIndexOf('.');
        String mime = dot >= 0 ? IMAGE_MIME.get(lower.substring(dot)) : null;
        if (mime != null) {
            return previewImage(name, path, size, file, mime);
        }
        if (lower.endsWith(".docx")) {
            return previewDocx(name, path, size, file);
        }
        if (lower.endsWith(".zip")) {
            return previewZip(name, path, size, file);
        }
        if (lower.endsWith(".pdf")) {
            return previewPdf(name, path, size, file);
        }
        if (dot >= 0 && BINARY_EXTENSIONS.contains(lower.substring(dot))) {
            return binaryPlaceholder(name, path, size);
        }
        return previewText(name, path, size, file);
    }

    /**
     * 在工作区内写文件（创建或覆盖，自动补建父目录）
     * @param id
     * @param userId
     * @param path 相对工作区根的路径
     * @param content 文件字节内容
     * @return
     */
    public Map<String, Object> writeFile(Long id, String userId, String path, byte[] content) {
        WorkspaceInfo info = requireOwned(id, userId);
        Path root = Paths.get(info.getRootPath());
        if (path == null || path.isBlank() || path.contains("..")) {
            throw new IllegalArgumentException("路径非法或越界，仅允许操作工作区内的文件");
        }
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("路径非法或越界，仅允许操作工作区内的文件");
        }
        byte[] bytes = content == null ? new byte[0] : content;
        try {
            if (target.getParent() != null && !Files.exists(target.getParent())) {
                Files.createDirectories(target.getParent());
            }
            Files.write(target, bytes);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", root.relativize(target).toString().replace('\\', '/'));
        result.put("size", (long) bytes.length);
        return result;
    }

    /**
     * 复制工作区内文件或目录
     * <p>递归复制，目标同名冲突自动追加副本后缀</p>
     * @param id
     * @param userId
     * @param path 源路径（相对工作区根）
     * @param targetDir 目标目录（相对工作区根，空表示根目录）
     * @return
     */
    public Map<String, Object> copyFile(Long id, String userId, String path, String targetDir) {
        Path root = requireOwnedRoot(id, userId);
        Path source = resolveInWorkspace(id, userId, path);
        if (root.equals(source)) {
            throw new IllegalArgumentException("不允许复制工作区根目录");
        }
        Path target = resolveInWorkspace(id, userId, targetDir);
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("目标不是目录：" + targetDir);
        }
        if (target.startsWith(source)) {
            throw new IllegalArgumentException("不允许复制到源目录自身内部");
        }
        Path dest = target.resolve(source.getFileName());
        if (Files.exists(dest)) {
            dest = uniqueCopyTarget(target, source.getFileName().toString());
        }
        try {
            FileOperatorUtils.copyRecursively(source, dest);
        } catch (UncheckedIOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", root.relativize(dest).toString().replace('\\', '/'));
        return result;
    }

    /**
     * 在工作区内创建文件夹
     * @param id
     * @param userId
     * @param path 父目录相对路径，空表示根目录
     * @param name 新文件夹名称
     * @return
     */
    public Map<String, Object> createDirectory(Long id, String userId, String path, String name) {
        String folderName = name == null ? "" : name.trim();
        if (!FileOperatorUtils.isValidFileName(folderName)) {
            throw new IllegalArgumentException("文件夹名称非法或为空");
        }
        Path root = requireOwnedRoot(id, userId);
        Path parent = resolveInWorkspace(id, userId, path);
        if (!Files.isDirectory(parent)) {
            throw new IllegalArgumentException("父目录不存在：" + path);
        }
        Path dest = parent.resolve(folderName);
        if (Files.exists(dest)) {
            throw new IllegalArgumentException("同名文件或目录已存在：" + folderName);
        }
        try {
            Files.createDirectories(dest);
        } catch (IOException e) {
            throw new IllegalArgumentException("创建文件夹失败：" + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", root.relativize(dest).toString().replace('\\', '/'));
        return result;
    }

    /**
     * 移动工作区内文件或目录到目标目录（同名冲突拒绝，移动后原位置不存在）
     * @param id
     * @param userId
     * @param path 源路径（相对工作区根）
     * @param targetDir 目标目录（相对工作区根，空表示根目录）
     * @return
     */
    public Map<String, Object> moveFile(Long id, String userId, String path, String targetDir) {
        Path root = requireOwnedRoot(id, userId);
        Path source = resolveInWorkspace(id, userId, path);
        if (root.equals(source)) {
            throw new IllegalArgumentException("不允许移动工作区根目录");
        }
        Path target = resolveInWorkspace(id, userId, targetDir);
        if (!Files.isDirectory(target)) {
            throw new IllegalArgumentException("目标不是目录：" + targetDir);
        }
        if (target.startsWith(source)) {
            throw new IllegalArgumentException("不允许移动到源目录自身内部");
        }
        Path dest = target.resolve(source.getFileName());
        if (Files.exists(dest)) {
            throw new IllegalArgumentException("目标已存在同名文件或目录：" + source.getFileName());
        }
        try {
            Files.move(source, dest);
        } catch (IOException e) {
            throw new IllegalArgumentException("移动失败：" + e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("path", root.relativize(dest).toString().replace('\\', '/'));
        return result;
    }

    /**
     * 重命名工作区内文件或目录
     * @param id
     * @param userId
     * @param path 相对工作区根的路径
     * @param newName 新名称（纯名称，不含路径）
     */
    public void renameFile(Long id, String userId, String path, String newName) {
        String name = newName == null ? "" : newName.trim();
        if (!FileOperatorUtils.isValidFileName(name)) {
            throw new IllegalArgumentException("新名称非法或为空");
        }
        Path root = requireOwnedRoot(id, userId);
        Path source = resolveInWorkspace(id, userId, path);
        if (root.equals(source)) {
            throw new IllegalArgumentException("不允许重命名工作区根目录");
        }
        Path target = source.getParent().resolve(name);
        if (Files.exists(target)) {
            throw new IllegalArgumentException("同名文件或目录已存在：" + name);
        }
        try {
            Files.move(source, target);
        } catch (IOException e) {
            throw new IllegalArgumentException("重命名失败：" + e.getMessage());
        }
    }

    /**
     * 删除工作区内文件或目录
     * <p>目录递归删除</p>
     * @param id
     * @param userId
     * @param path 相对工作区根的路径
     */
    public void deleteFile(Long id, String userId, String path) {
        Path root = requireOwnedRoot(id, userId);
        Path source = resolveInWorkspace(id, userId, path);
        if (root.equals(source)) {
            throw new IllegalArgumentException("不允许删除工作区根目录");
        }
        try {
            if (Files.isDirectory(source)) {
                FileOperatorUtils.deleteRecursively(source);
            } else {
                Files.delete(source);
            }
        } catch (UncheckedIOException e) {
            throw new IllegalArgumentException(e.getMessage());
        } catch (IOException e) {
            throw new IllegalArgumentException("删除失败：" + e.getMessage());
        }
    }

    /**
     * 表格类文件预览（真二进制工作簿走POI，其余按CSV/TSV文本表格降级解析）
     * @param name
     * @param path
     * @param size
     * @param file
     * @return
     */
    private Map<String, Object> previewSpreadsheet(String name, String path, long size, Path file) {
        Map<String, Object> result = baseResult(name, path, size);
        result.put("type", "excel");
        if (isBinaryWorkbook(file)) {
            try {
                result.put("workbook", ExcelReader.readAsMap(file));
            } catch (Exception e) {
                throw new IllegalArgumentException("解析Excel失败：" + e.getMessage());
            }
            return result;
        }
        try {
            result.put("workbook", PlainTextTableReader.readAsMap(file));
        } catch (Exception e) {
            throw new IllegalArgumentException("解析表格文件失败：" + e.getMessage());
        }
        return result;
    }

    /**
     * 图片预览（base64 dataUrl内联，限5MB）
     * @param name
     * @param path
     * @param size
     * @param file
     * @param mime
     * @return
     */
    private Map<String, Object> previewImage(String name, String path, long size, Path file, String mime) {
        if (size > MAX_IMAGE_PREVIEW_BYTES) {
            throw new IllegalArgumentException("图片超过预览上限（5MB）");
        }
        Map<String, Object> result = baseResult(name, path, size);
        result.put("type", "image");
        result.put("content", readDataUrl(file, mime));
        return result;
    }

    /**
     * Word文档预览（段落与表格结构化，10MB内附带dataUrl供前端docx-preview原样渲染）
     * @param name
     * @param path
     * @param size
     * @param file
     * @return
     */
    private Map<String, Object> previewDocx(String name, String path, long size, Path file) {
        try {
            Map<String, Object> result = baseResult(name, path, size);
            result.put("type", "docx");
            result.put("document", DocxReader.readAsMap(file));
            if (size <= MAX_PDF_PREVIEW_BYTES) {
                result.put("content", readDataUrl(file, DOCX_MIME));
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("解析Word失败：" + e.getMessage());
        }
    }

    /**
     * zip压缩包预览（返回条目清单，超500条截断）
     * @param name
     * @param path
     * @param size
     * @param file
     * @return
     */
    private Map<String, Object> previewZip(String name, String path, long size, Path file) {
        List<Map<String, Object>> entries = new ArrayList<>();
        boolean truncated = false;
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                if (entries.size() >= MAX_ZIP_ENTRIES) {
                    truncated = true;
                    break;
                }
                var entry = iterator.next();
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("name", entry.getName());
                item.put("dir", entry.isDirectory());
                item.put("size", entry.getSize());
                item.put("compressedSize", entry.getCompressedSize());
                entries.add(item);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("解析压缩包失败：" + e.getMessage());
        }
        Map<String, Object> result = baseResult(name, path, size);
        result.put("type", "zip");
        result.put("entries", entries);
        result.put("truncated", truncated);
        return result;
    }

    /**
     * PDF预览（base64 dataUrl内联，限10MB，前端iframe走浏览器原生渲染）
     * @param name
     * @param path
     * @param size
     * @param file
     * @return
     */
    private Map<String, Object> previewPdf(String name, String path, long size, Path file) {
        if (size > MAX_PDF_PREVIEW_BYTES) {
            throw new IllegalArgumentException("PDF超过预览上限（10MB）");
        }
        Map<String, Object> result = baseResult(name, path, size);
        result.put("type", "pdf");
        result.put("content", readDataUrl(file, "application/pdf"));
        return result;
    }

    /**
     * 文本预览（UTF-8读取，限200KB超出截断）
     * @param name
     * @param path
     * @param size
     * @param file
     * @return
     */
    private Map<String, Object> previewText(String name, String path, long size, Path file) {
        boolean truncated = size > MAX_PREVIEW_BYTES;
        String content;
        try {
            if (truncated) {
                byte[] bytes = new byte[MAX_PREVIEW_BYTES];
                try (var in = Files.newInputStream(file)) {
                    int read = in.read(bytes);
                    content = new String(bytes, 0, Math.max(read, 0), java.nio.charset.StandardCharsets.UTF_8);
                }
            } else {
                content = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败：" + e.getMessage());
        }
        Map<String, Object> result = baseResult(name, path, size);
        result.put("type", "text");
        result.put("truncated", truncated);
        result.put("content", content);
        return result;
    }

    /**
     * 二进制格式占位结果
     * @param name
     * @param path
     * @param size
     * @return
     */
    private Map<String, Object> binaryPlaceholder(String name, String path, long size) {
        Map<String, Object> result = baseResult(name, path, size);
        result.put("type", "binary");
        result.put("content", "该格式暂不支持在线预览");
        return result;
    }

    /**
     * 预览结果公共字段
     * @param name
     * @param path
     * @param size
     * @return
     */
    private Map<String, Object> baseResult(String name, String path, long size) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("path", path);
        result.put("size", size);
        return result;
    }

    /**
     * 判断是否表格类扩展名
     * @param lower 小写文件名
     * @return
     */
    private boolean isSpreadsheetName(String lower) {
        return lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".xlsm")
                || lower.endsWith(".csv") || lower.endsWith(".tsv");
    }

    /**
     * 按魔数判断是否二进制工作簿（PK=zip类容器，D0CF11E0=OLE2）
     * @param file
     * @return
     */
    private boolean isBinaryWorkbook(Path file) {
        byte[] head = new byte[4];
        try (var in = Files.newInputStream(file)) {
            int read = in.readNBytes(head, 0, head.length);
            if (read < head.length) {
                return false;
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败：" + e.getMessage());
        }
        boolean zipLike = head[0] == 0x50 && head[1] == 0x4B;
        boolean ole2 = head[0] == (byte) 0xD0 && head[1] == (byte) 0xCF && head[2] == 0x11 && head[3] == (byte) 0xE0;
        return zipLike || ole2;
    }

    /**
     * 读取文件为base64 dataUrl
     * @param file
     * @param mime
     * @return
     */
    private String readDataUrl(Path file, String mime) {
        try {
            return "data:" + mime + ";base64,"
                    + java.util.Base64.getEncoder().encodeToString(Files.readAllBytes(file));
        } catch (IOException e) {
            throw new IllegalArgumentException("读取文件失败：" + e.getMessage());
        }
    }

    /**
     * 目标同名冲突时生成唯一副本路径（名称 - 副本、名称 - 副本 (2)…）
     * @param dir 目标目录
     * @param fileName 原文件名
     * @return
     */
    private Path uniqueCopyTarget(Path dir, String fileName) {
        int dot = fileName.lastIndexOf('.');
        String base = dot > 0 ? fileName.substring(0, dot) : fileName;
        String ext = dot > 0 ? fileName.substring(dot) : "";
        for (int i = 1; ; i++) {
            String candidate = i == 1 ? base + " - 副本" + ext : base + " - 副本 (" + i + ")" + ext;
            Path path = dir.resolve(candidate);
            if (!Files.exists(path)) {
                return path;
            }
        }
    }

    /**
     * 校验归属并返回工作区根目录
     * @param id
     * @param userId
     * @return
     */
    private Path requireOwnedRoot(Long id, String userId) {
        WorkspaceInfo info = requireOwned(id, userId);
        return Paths.get(info.getRootPath());
    }

    /**
     * 校验归属并返回工作区（供路由侧构建WorkspaceRef复用）
     * @param id
     * @param userId
     * @return
     */
    public WorkspaceInfo requireOwned(Long id, String userId) {
        checkEnabled();
        WorkspaceInfo info = userWorkspaceRepository.findById(id);
        // 区分查无记录与归属不匹配：前者多为登记被删除后的残留引用，后者才是权限问题
        if (info == null) {
            throw new IllegalArgumentException("工作区不存在或已被删除");
        }
        if (!info.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该工作区");
        }
        return info;
    }

    private void checkEnabled() {
        if (!workspaceProperties.isEnabled()) {
            throw new IllegalArgumentException("用户工作区功能未启用");
        }
    }

    private String normalizeApprovalMode(String approvalMode) {
        String mode = approvalMode == null || approvalMode.isBlank() ? "MANUAL" : approvalMode.trim().toUpperCase();
        if (!APPROVAL_MODES.contains(mode)) {
            throw new IllegalArgumentException("非法审批层级：" + approvalMode);
        }
        return mode;
    }

    /**
     * 归一化工作区类型（空白默认SERVER，当前仅支持SERVER类型登记）
     * @param type
     * @return
     */
    private String normalizeType(String type) {
        String normalized = type == null || type.isBlank()
                ? WorkspaceRef.TYPE_SERVER : type.trim().toUpperCase(java.util.Locale.ROOT);
        if (!WorkspaceRef.TYPE_SERVER.equals(normalized)) {
            throw new IllegalArgumentException("暂不支持登记该类型工作区：" + type);
        }
        return normalized;
    }

    private void checkRootsWhitelist(String canonical) {
        if (workspaceProperties.getRoots().isEmpty()) {
            return;
        }
        if (!isUnderAnyRoot(canonical)) {
            throw new IllegalArgumentException("目录不在允许登记的白名单根内");
        }
    }

    private boolean isUnderAnyRoot(String canonical) {
        for (String root : workspaceProperties.getRoots()) {
            String canonicalRoot = WorkspacePathUtils.canonical(root);
            if (canonicalRoot != null && WorkspacePathUtils.isUnderRoot(canonicalRoot, canonical)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析工作区内绝对路径并校验归属与越界
     * @param id
     * @param userId
     * @param path 相对路径
     * @return
     */
    private Path resolveInWorkspace(Long id, String userId, String path) {
        WorkspaceInfo info = requireOwned(id, userId);
        Path root = Paths.get(info.getRootPath());
        if (path == null || path.isBlank() || ".".equals(path) || "/".equals(path) || "\\".equals(path)) {
            return root;
        }
        Path target = root.resolve(path).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("路径越界，不允许访问工作区之外的内容");
        }
        if (!Files.exists(target)) {
            throw new IllegalArgumentException("路径不存在：" + path);
        }
        return target;
    }
}
