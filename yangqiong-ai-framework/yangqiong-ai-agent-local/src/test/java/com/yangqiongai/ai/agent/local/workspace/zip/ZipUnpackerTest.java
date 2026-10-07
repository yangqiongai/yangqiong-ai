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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * zip解包测试
 * @author yangqiong
 */
class ZipUnpackerTest {

    @TempDir
    Path tempDir;

    /**
     * 在工作区内打包一份样例文件目录结构
     * @param root
     * @return zip路径
     * @throws Exception
     */
    private Path packSampleZip(Path root) throws Exception {
        Path docs = root.resolve("资料");
        Files.createDirectories(docs.resolve("子目录"));
        Files.writeString(docs.resolve("说明.txt"), "解包测试", StandardCharsets.UTF_8);
        Files.writeString(docs.resolve("子目录").resolve("明细.csv"), "列A,列B\n1,2", StandardCharsets.UTF_8);
        Path zipFile = root.resolve("归档.zip");
        ZipPacker.pack(root, List.of("资料"), zipFile);
        return zipFile;
    }

    /**
     * 手工构建zip（可塞入任意条目名，目录条目内容传null）
     * @param zipFile
     * @param entries 条目名→内容
     * @throws Exception
     */
    private void buildZip(Path zipFile, Map<String, String> entries) throws Exception {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile), StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                if (e.getValue() != null) {
                    zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                }
                zos.closeEntry();
            }
        }
    }

    @Test
    void 正常解包到指定目录() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        Path zipFile = packSampleZip(root);

        ZipUnpacker.UnpackResult result = ZipUnpacker.unpack(root, zipFile, "导出");

        Path destDir = root.resolve("导出");
        assertThat(Files.isDirectory(destDir)).isTrue();
        assertThat(result.getFileCount()).isEqualTo(2);
        assertThat(result.getDirCount()).isEqualTo(2);
        assertThat(result.getTotalBytes()).isEqualTo("解包测试".getBytes(StandardCharsets.UTF_8).length
                + "列A,列B\n1,2".getBytes(StandardCharsets.UTF_8).length);
        assertThat(Files.readString(destDir.resolve("资料").resolve("说明.txt"), StandardCharsets.UTF_8))
                .isEqualTo("解包测试");
        assertThat(Files.readString(destDir.resolve("资料").resolve("子目录").resolve("明细.csv"),
                StandardCharsets.UTF_8)).isEqualTo("列A,列B\n1,2");
    }

    @Test
    void 目标目录为空或越界被拒绝() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        Path zipFile = packSampleZip(root);

        assertThatThrownBy(() -> ZipUnpacker.unpack(root, zipFile, ""))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("destDir不能为空");

        assertThatThrownBy(() -> ZipUnpacker.unpack(root, zipFile, "../outside"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("destDir非法或越界");
    }

    @Test
    void 恶意条目路径被拒绝() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        List<String> evilNames = List.of("../evil.txt", "/abs/evil.txt", "C:/evil.txt", "正常/../../evil.txt");
        for (String evilName : evilNames) {
            Path zipFile = root.resolve("evil.zip");
            buildZip(zipFile, Map.of(evilName, "恶意内容"));
            assertThatThrownBy(() -> ZipUnpacker.unpack(root, zipFile, "导出"))
                    .as("条目%s应被拒绝", evilName)
                    .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("已拒绝解包");
        }
        // 越界文件绝不能落在工作区内外的预期位置
        assertThat(Files.exists(tempDir.resolve("evil.txt"))).isFalse();
        assertThat(Files.exists(root.resolve("evil.txt"))).isFalse();
    }

    @Test
    void 条目数超上限被拒绝() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        Path zipFile = root.resolve("many.zip");
        Map<String, String> entries = new LinkedHashMap<>();
        for (int i = 0; i <= 2000; i++) {
            entries.put("f" + i + ".txt", "x");
        }
        buildZip(zipFile, entries);

        assertThatThrownBy(() -> ZipUnpacker.unpack(root, zipFile, "导出"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("zip条目数超上限");
    }

    @Test
    void 解压总大小超上限被拒绝() throws Exception {
        Path root = tempDir.resolve("工作区");
        Files.createDirectories(root);
        Path zipFile = root.resolve("bomb.zip");
        byte[] chunk = new byte[8192];
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile), StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("bomb.bin"));
            for (long written = 0; written <= 100L * 1024 * 1024; written += chunk.length) {
                zos.write(chunk);
            }
            zos.closeEntry();
        }

        assertThatThrownBy(() -> ZipUnpacker.unpack(root, zipFile, "导出"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("解压总大小超上限");
    }
}
