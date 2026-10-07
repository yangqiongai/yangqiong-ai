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
package com.yangqiongai.ai.agent.local.workspace.zip;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * zip压缩打包
 * <p>
 * 打包工作区内文件/目录为zip，条目名保留相对路径结构（/分隔），写入前统一校验条目与体积限额。
 * </p>
 * @author yangqiong
 */
public class ZipPacker {

    private static final Logger log = LoggerFactory.getLogger(ZipPacker.class);

    /**
     * zip条目总数上限
     */
    private static final int MAX_ENTRIES = 2000;

    /**
     * 源文件总大小上限（100MB）
     */
    private static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;

    private ZipPacker() {
    }

    /**
     * 打包结果
     */
    public static class PackResult {

        /**
         * 写入条目数量（含目录条目）
         */
        private final int entryCount;

        /**
         * 源文件总字节数
         */
        private final long totalBytes;

        PackResult(int entryCount, long totalBytes) {
            this.entryCount = entryCount;
            this.totalBytes = totalBytes;
        }

        public int getEntryCount() {
            return entryCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }
    }

    /**
     * 将工作区内文件/目录清单打包为zip
     * @param root 工作区根目录
     * @param paths 相对工作区根的文件/目录清单
     * @param zipTarget zip输出路径
     * @return
     * @throws IOException
     */
    public static PackResult pack(Path root, List<String> paths, Path zipTarget) throws IOException {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("paths不能为空");
        }
        Set<Path> sources = new LinkedHashSet<>();
        for (String rel : paths) {
            sources.add(resolveSource(root, rel));
        }
        Set<Path> dirs = new LinkedHashSet<>();
        Set<Path> files = new LinkedHashSet<>();
        long totalBytes = collectEntries(sources, dirs, files);
        if (zipTarget.getParent() != null) {
            Files.createDirectories(zipTarget.getParent());
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipTarget), StandardCharsets.UTF_8)) {
            for (Path dir : dirs) {
                zos.putNextEntry(new ZipEntry(entryName(root, dir) + "/"));
                zos.closeEntry();
            }
            for (Path file : files) {
                zos.putNextEntry(new ZipEntry(entryName(root, file)));
                Files.copy(file, zos);
                zos.closeEntry();
            }
        }
        log.info("zip已打包: {}（{}条目，{}字节）", zipTarget, dirs.size() + files.size(), totalBytes);
        return new PackResult(dirs.size() + files.size(), totalBytes);
    }

    /**
     * 解析并校验单个待打包路径（禁止越界，必须存在）
     * @param root
     * @param rel
     * @return
     */
    private static Path resolveSource(Path root, String rel) {
        if (rel == null || rel.isBlank()) {
            throw new IllegalArgumentException("paths含空路径");
        }
        if (rel.contains("..")) {
            throw new IllegalArgumentException("路径非法或越界，仅允许打包工作区内的文件：" + rel);
        }
        Path resolved = root.resolve(rel).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("路径非法或越界，仅允许打包工作区内的文件：" + rel);
        }
        if (!Files.exists(resolved)) {
            throw new IllegalArgumentException("路径不存在：" + rel);
        }
        return resolved;
    }

    /**
     * 收集全部待写条目并校验条目数/总大小限额
     * @param sources
     * @param dirs
     * @param files
     * @return 源文件总字节数
     * @throws IOException
     */
    private static long collectEntries(Set<Path> sources, Set<Path> dirs, Set<Path> files) throws IOException {
        long totalBytes = 0;
        for (Path source : sources) {
            if (Files.isDirectory(source)) {
                try (Stream<Path> stream = Files.walk(source)) {
                    for (Path path : stream.sorted().toList()) {
                        addEntry(dirs, files, path, Files.isDirectory(path));
                        if (!Files.isDirectory(path)) {
                            totalBytes = addBytes(totalBytes, Files.size(path));
                        }
                    }
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
            } else {
                addEntry(dirs, files, source, false);
                totalBytes = addBytes(totalBytes, Files.size(source));
            }
        }
        return totalBytes;
    }

    /**
     * 登记条目并校验条目数上限
     * @param dirs
     * @param files
     * @param path
     * @param directory
     */
    private static void addEntry(Set<Path> dirs, Set<Path> files, Path path, boolean directory) {
        if (directory) {
            dirs.add(path);
        } else {
            files.add(path);
        }
        int entryCount = dirs.size() + files.size();
        if (entryCount > MAX_ENTRIES) {
            throw new IllegalArgumentException("条目总数超上限：" + entryCount + " > " + MAX_ENTRIES
                    + "，请减少打包范围");
        }
    }

    /**
     * 累计源文件字节数并校验总大小上限
     * @param totalBytes
     * @param bytes
     * @return
     */
    private static long addBytes(long totalBytes, long bytes) {
        long sum = totalBytes + bytes;
        if (sum > MAX_TOTAL_BYTES) {
            throw new IllegalArgumentException("源文件总大小超上限：" + (sum / 1024 / 1024) + "MB > "
                    + (MAX_TOTAL_BYTES / 1024 / 1024) + "MB，请减少打包范围");
        }
        return sum;
    }

    /**
     * 计算zip条目名（相对工作区根，/分隔）
     * @param root
     * @param path
     * @return
     */
    private static String entryName(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }
}
