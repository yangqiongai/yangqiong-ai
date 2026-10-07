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
package com.yangqiongai.ai.common.utils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 工作区路径校验
 * <p>
 * 供工作区管理（白名单根校验）与Agent运行时注入（沙箱根再校验）两处复用，
 * 统一canonical归一化与组件级前缀比较，避免各处自行实现的差异与漏洞。
 * </p>
 * @author yangqiong
 */
public final class WorkspacePathUtils {

    private WorkspacePathUtils() {
    }

    /**
     * 归一化路径：转绝对路径、解析符号链接、统一盘符大小写与分隔符
     * @param path 原始路径
     * @return canonical归一化后的标准路径字符串，非法路径返回null
     */
    public static String canonical(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            Path real = Paths.get(path).toAbsolutePath().toRealPath();
            return real.toString();
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 校验目录存在且为目录
     * @param path 目录路径
     * @return 存在且为目录返回true
     */
    public static boolean isDirectory(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        return Files.isDirectory(Paths.get(path));
    }

    /**
     * 创建目录（含父级），已存在时直接成功
     * @param path 目录路径
     * @return 创建成功返回true
     */
    public static boolean createDirectories(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        try {
            Files.createDirectories(Paths.get(path));
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 校验子路径是否位于根目录之内（组件级前缀比较，防../穿越与同前缀名目录误判）
     * @param root 根目录（已canonical）
     * @param child 子路径（已canonical）
     * @return 在根内返回true
     */
    public static boolean isUnderRoot(String root, String child) {
        if (root == null || child == null) {
            return false;
        }
        Path rootPath = Paths.get(root).normalize();
        Path childPath = Paths.get(child).normalize();
        return childPath.startsWith(rootPath);
    }
}
