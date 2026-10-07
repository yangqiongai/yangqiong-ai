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
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * 文件递归操作
 * @author yangqiong
 */
public final class FileOperatorUtils {

    /**
     * Windows文件名非法字符
     */
    private static final String INVALID_NAME_CHARS = "\\/:*?\"<>|";

    private FileOperatorUtils() {
    }

    /**
     * 递归复制文件或目录
     * <p>目录连同全部子级复制，目标同名文件覆盖</p>
     * @param source 源文件或目录
     * @param target 目标路径
     */
    public static void copyRecursively(Path source, Path target) {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(p -> {
                Path dest = target.resolve(source.relativize(p).toString());
                try {
                    if (Files.isDirectory(p)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.createDirectories(dest.getParent());
                        Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("复制失败：" + e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("复制失败：" + e.getMessage(), e);
        }
    }

    /**
     * 递归删除文件或目录
     * @param root 待删除的文件或目录
     */
    public static void deleteRecursively(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            // 逆序排列保证先删子级后删父级
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new UncheckedIOException("删除失败：" + e.getMessage(), e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException("删除失败：" + e.getMessage(), e);
        }
    }

    /**
     * 校验文件名合法性（禁止路径分隔符与Windows保留字符，限255字符）
     * @param name 文件名
     * @return 合法返回true
     */
    public static boolean isValidFileName(String name) {
        if (name == null || name.isBlank() || name.length() > 255) {
            return false;
        }
        if (".".equals(name) || "..".equals(name)) {
            return false;
        }
        return name.chars().noneMatch(c -> INVALID_NAME_CHARS.indexOf(c) >= 0);
    }
}
