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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * zip压缩打包测试
 * @author yangqiong
 */
class ZipPackerTest {

    @TempDir
    Path tempDir;

    /**
     * 在工作区内创建待打包的文件与目录结构
     * @param root
     * @return
     * @throws Exception
     */
    private Path createSourceTree(Path root) throws Exception {
        Path docs = root.resolve("资料");
        Files.createDirectories(docs.resolve("子目录"));
        Files.writeString(docs.resolve("说明.txt"), "打包测试", StandardCharsets.UTF_8);
        Files.writeString(docs.resolve("子目录").resolve("明细.csv"), "列A,列B\n1,2", StandardCharsets.UTF_8);
        Files.writeString(root.resolve("单文件.txt"), "顶层文件", StandardCharsets.UTF_8);
        return docs;
    }

    /**
     * 读回zip全部条目名
     * @param zipFile
     * @return
     * @throws Exception
     */
    private List<String> readEntryNames(Path zipFile) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    @Test
    void 文件与目录递归打包且条目名保留相对路径() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        createSourceTree(root);
        Path zipTarget = root.resolve("归档.zip");

        ZipPacker.PackResult result = ZipPacker.pack(root, List.of("资料", "单文件.txt"), zipTarget);

        assertThat(Files.exists(zipTarget)).isTrue();
        assertThat(result.getEntryCount()).isEqualTo(5);
        assertThat(result.getTotalBytes()).isEqualTo("打包测试".getBytes(StandardCharsets.UTF_8).length
                + "列A,列B\n1,2".getBytes(StandardCharsets.UTF_8).length
                + "顶层文件".getBytes(StandardCharsets.UTF_8).length);

        List<String> names = readEntryNames(zipTarget);
        assertThat(names).containsExactlyInAnyOrder(
                "资料/", "资料/说明.txt", "资料/子目录/", "资料/子目录/明细.csv", "单文件.txt");
        assertThat(names).allSatisfy(name -> assertThat(name).doesNotContain("\\"));
    }

    @Test
    void 路径不存在或越界或空清单报错() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        Path zipTarget = root.resolve("归档.zip");

        assertThatThrownBy(() -> ZipPacker.pack(root, List.of("不存在.txt"), zipTarget))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("路径不存在");

        assertThatThrownBy(() -> ZipPacker.pack(root, List.of("../outside.txt"), zipTarget))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("越界");

        assertThatThrownBy(() -> ZipPacker.pack(root, List.of(), zipTarget))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("paths不能为空");
    }

    @Test
    void 条目数超上限报错() throws Exception {
        Path root = tempDir.resolve("工作区");
        Path big = root.resolve("big");
        Files.createDirectories(big);
        for (int i = 0; i < 2000; i++) {
            Files.writeString(big.resolve("f" + i + ".txt"), "x");
        }
        Path zipTarget = root.resolve("归档.zip");

        // 目录条目1个 + 2000个文件条目 = 2001 超上限
        assertThatThrownBy(() -> ZipPacker.pack(root, List.of("big"), zipTarget))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("条目总数超上限");
    }

    @Test
    void 源文件总大小超上限报错() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        Path bigFile = root.resolve("big.bin");
        byte[] chunk = new byte[8192];
        try (OutputStream out = Files.newOutputStream(bigFile)) {
            for (long written = 0; written <= 100L * 1024 * 1024; written += chunk.length) {
                out.write(chunk);
            }
        }
        Path zipTarget = root.resolve("归档.zip");

        assertThatThrownBy(() -> ZipPacker.pack(root, List.of("big.bin"), zipTarget))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("源文件总大小超上限");
    }
}
