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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * zip解包
 * <p>
 * 解包zip到工作区指定目录，带zip-slip路径防护与条目数/解压总量限额（累计校验防炸弹）。
 * </p>
 * @author yangqiong
 */
public class ZipUnpacker {

    private static final Logger log = LoggerFactory.getLogger(ZipUnpacker.class);

    /**
     * zip条目总数上限
     */
    private static final int MAX_ENTRIES = 2000;

    /**
     * 解压总大小上限（100MB）
     */
    private static final long MAX_TOTAL_BYTES = 100L * 1024 * 1024;

    /**
     * 复制缓冲区大小
     */
    private static final int BUFFER_SIZE = 8192;

    private ZipUnpacker() {
    }

    /**
     * 解包结果
     */
    public static class UnpackResult {

        /**
         * 解出的文件数量
         */
        private final int fileCount;

        /**
         * 解出的目录条目数量
         */
        private final int dirCount;

        /**
         * 解压总字节数
         */
        private final long totalBytes;

        UnpackResult(int fileCount, int dirCount, long totalBytes) {
            this.fileCount = fileCount;
            this.dirCount = dirCount;
            this.totalBytes = totalBytes;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getDirCount() {
            return dirCount;
        }

        public long getTotalBytes() {
            return totalBytes;
        }
    }

    /**
     * 解包zip到工作区指定目录
     * @param root 工作区根目录
     * @param zipFile zip文件路径
     * @param destDirRel 解包目标目录（相对工作区根）
     * @return
     * @throws IOException
     */
    public static UnpackResult unpack(Path root, Path zipFile, String destDirRel) throws IOException {
        Path destDir = resolveDestDir(root, destDirRel);
        Files.createDirectories(destDir);
        int fileCount = 0;
        int dirCount = 0;
        int entryCount = 0;
        long totalBytes = 0;
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ENTRIES) {
                    throw new IllegalArgumentException("zip条目数超上限：" + MAX_ENTRIES + "，已拒绝解包");
                }
                Path target = resolveEntryTarget(destDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    dirCount++;
                } else {
                    if (target.getParent() != null) {
                        Files.createDirectories(target.getParent());
                    }
                    totalBytes += copyBounded(zis, target, totalBytes);
                    fileCount++;
                }
                zis.closeEntry();
            }
        }
        log.info("zip已解包: {} → {}（{}文件，{}目录，{}字节）", zipFile, destDir, fileCount, dirCount, totalBytes);
        return new UnpackResult(fileCount, dirCount, totalBytes);
    }

    /**
     * 解析并校验解包目标目录（禁止为空与越界）
     * @param root
     * @param destDirRel
     * @return
     */
    private static Path resolveDestDir(Path root, String destDirRel) {
        if (destDirRel == null || destDirRel.isBlank()) {
            throw new IllegalArgumentException("destDir不能为空");
        }
        if (destDirRel.contains("..")) {
            throw new IllegalArgumentException("destDir非法或越界，仅允许解包到工作区内：" + destDirRel);
        }
        Path destDir = root.resolve(destDirRel).normalize();
        if (!destDir.startsWith(root)) {
            throw new IllegalArgumentException("destDir非法或越界，仅允许解包到工作区内：" + destDirRel);
        }
        return destDir;
    }

    /**
     * 解析zip条目到目标路径（zip-slip防护：拒绝..与绝对路径）
     * @param destDir
     * @param entryName
     * @return
     */
    private static Path resolveEntryTarget(Path destDir, String entryName) {
        String name = entryName.replace('\\', '/');
        if (name.isBlank() || name.contains("..")) {
            throw new IllegalArgumentException("zip条目含非法相对路径，已拒绝解包：" + entryName);
        }
        // 绝对路径（含/开头、windows盘符如C:/）一律拒绝
        if (name.startsWith("/") || Paths.get(name).isAbsolute()
                || (name.length() >= 2 && name.charAt(1) == ':')) {
            throw new IllegalArgumentException("zip条目含绝对路径，已拒绝解包：" + entryName);
        }
        Path target = destDir.resolve(name).normalize();
        if (!target.startsWith(destDir)) {
            throw new IllegalArgumentException("zip条目越界，已拒绝解包：" + entryName);
        }
        return target;
    }

    /**
     * 有界复制条目流（累计校验防解压炸弹）
     * @param in
     * @param target
     * @param copiedBytes 已累计字节数
     * @return 本次复制字节数
     * @throws IOException
     */
    private static long copyBounded(InputStream in, Path target, long copiedBytes) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            byte[] buffer = new byte[BUFFER_SIZE];
            long total = copiedBytes;
            int read;
            while ((read = in.read(buffer)) > 0) {
                total += read;
                if (total > MAX_TOTAL_BYTES) {
                    throw new IllegalArgumentException("解压总大小超上限：" + (MAX_TOTAL_BYTES / 1024 / 1024)
                            + "MB，已拒绝解包");
                }
                out.write(buffer, 0, read);
            }
            return total - copiedBytes;
        }
    }
}
