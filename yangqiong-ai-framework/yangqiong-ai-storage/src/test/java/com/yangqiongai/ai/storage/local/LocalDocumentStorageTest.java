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
package com.yangqiongai.ai.storage.local;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.DefaultObjectKeyResolver;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalDocumentStorage 功能测试
 * @author yangqiong
 */
@DisplayName("LocalDocumentStorage 本地文件存储测试")
class LocalDocumentStorageTest {

    @TempDir
    Path tempDir;

    private LocalDocumentStorage service;

    @BeforeEach
    void setUp() {
        LocalProperties properties = new LocalProperties();
        properties.setBaseDir(tempDir.toString());
        service = new LocalDocumentStorage(properties);
        ReflectionTestUtils.setField(service, "objectKeyResolver", new DefaultObjectKeyResolver());
    }

    @Test
    @DisplayName("uploadFile: 写入文件并返回原始 objectKey")
    void uploadFile_writesFileAndReturnsOriginalKey() throws Exception {
        InputStream is = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

        String result = service.uploadFile("bucket", "obj/001.txt", is, "text/plain");

        assertThat(result).isEqualTo("obj/001.txt");
        Path written = tempDir.resolve("bucket/obj/001.txt");
        assertThat(written).exists();
        assertThat(Files.readString(written)).isEqualTo("hello");
    }

    @Test
    @DisplayName("uploadFile: 自动创建父目录")
    void uploadFile_createsParentDirs() {
        InputStream is = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

        service.uploadFile("bucket", "a/b/c/deep.txt", is, "text/plain");

        assertThat(tempDir.resolve("bucket/a/b/c/deep.txt")).exists();
    }

    @Test
    @DisplayName("uploadFile: 作用域解析器写入 scopeId 前缀路径")
    void uploadFile_usesScopePrefix_withTenantResolver() {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        InputStream is = new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8));

        service.uploadFile("bucket", "obj/001.txt", is, "text/plain");

        assertThat(tempDir.resolve("bucket/tenant-001/obj/001.txt")).exists();
    }

    @Test
    @DisplayName("uploadFile: 覆盖已存在的同名文件")
    void uploadFile_overwritesExistingFile() throws Exception {
        service.uploadFile("bucket", "obj/001.txt",
                new ByteArrayInputStream("v1".getBytes(StandardCharsets.UTF_8)), "text/plain");
        service.uploadFile("bucket", "obj/001.txt",
                new ByteArrayInputStream("v2".getBytes(StandardCharsets.UTF_8)), "text/plain");

        assertThat(Files.readString(tempDir.resolve("bucket/obj/001.txt"))).isEqualTo("v2");
    }

    @Test
    @DisplayName("downloadFile: 返回文件内容")
    void downloadFile_returnsContent() throws Exception {
        service.uploadFile("bucket", "obj/001.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "text/plain");

        try (InputStream is = service.downloadFile("bucket", "obj/001.txt")) {
            assertThat(is.readAllBytes()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("downloadFile: 文件不存在抛 AiException")
    void downloadFile_throwsWhenMissing() {
        assertThatThrownBy(() -> service.downloadFile("bucket", "missing.txt"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    @DisplayName("deleteFile: 删除已存在的文件")
    void deleteFile_removesFile() throws Exception {
        service.uploadFile("bucket", "obj/001.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "text/plain");

        service.deleteFile("bucket", "obj/001.txt");

        assertThat(tempDir.resolve("bucket/obj/001.txt")).doesNotExist();
    }

    @Test
    @DisplayName("deleteFile: 文件不存在不抛异常")
    void deleteFile_ignoresMissing() {
        service.deleteFile("bucket", "missing.txt");
    }

    @Test
    @DisplayName("getFileUrl: 返回 local:// 标识")
    void getFileUrl_returnsLocalUri() {
        String url = service.getFileUrl("bucket", "obj/001.txt");

        assertThat(url).isEqualTo("local://bucket/obj/001.txt");
    }

    @Test
    @DisplayName("fileExists: 存在返回 true，不存在返回 false")
    void fileExists_trueAndFalse() {
        service.uploadFile("bucket", "obj/001.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "text/plain");

        assertThat(service.fileExists("bucket", "obj/001.txt")).isTrue();
        assertThat(service.fileExists("bucket", "missing.txt")).isFalse();
    }

    @Test
    @DisplayName("listFiles: 按前缀递归过滤，分隔符统一为 /")
    void listFiles_filtersByPrefixRecursively() {
        service.uploadFile("bucket", "docs/a.txt",
                new ByteArrayInputStream("1".getBytes(StandardCharsets.UTF_8)), "text/plain");
        service.uploadFile("bucket", "docs/sub/b.txt",
                new ByteArrayInputStream("2".getBytes(StandardCharsets.UTF_8)), "text/plain");
        service.uploadFile("bucket", "other/c.txt",
                new ByteArrayInputStream("3".getBytes(StandardCharsets.UTF_8)), "text/plain");

        List<String> keys = service.listFiles("bucket", "docs/");

        assertThat(keys).containsExactly("docs/a.txt", "docs/sub/b.txt");
    }

    @Test
    @DisplayName("listFiles: 桶目录不存在返回空列表")
    void listFiles_emptyWhenBucketDirMissing() {
        assertThat(service.listFiles("no-such-bucket", "")).isEmpty();
    }

    @Test
    @DisplayName("listFiles: 作用域解析器按解析后前缀过滤")
    void listFiles_usesScopePrefix_withTenantResolver() {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        service.uploadFile("bucket", "obj/001.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "text/plain");

        List<String> keys = service.listFiles("bucket", "");

        assertThat(keys).containsExactly("tenant-001/obj/001.txt");
    }

    @Test
    @DisplayName("createBucketIfNotExists: 创建目录且幂等")
    void createBucketIfNotExists_createsDirAndIdempotent() {
        service.createBucketIfNotExists("bucket");
        service.createBucketIfNotExists("bucket");

        assertThat(tempDir.resolve("bucket")).isDirectory();
    }

    @Test
    @DisplayName("copyFile: 复制文件并自动创建目标父目录")
    void copyFile_copiesAndCreatesParentDirs() throws Exception {
        service.uploadFile("bucket", "src/a.txt",
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), "text/plain");

        service.copyFile("bucket", "src/a.txt", "other", "dest/b.txt");

        assertThat(Files.readString(tempDir.resolve("other/dest/b.txt"))).isEqualTo("hello");
        assertThat(tempDir.resolve("bucket/src/a.txt")).exists();
    }

    @Test
    @DisplayName("copyFile: 源文件不存在抛 AiException")
    void copyFile_throwsWhenSourceMissing() {
        assertThatThrownBy(() -> service.copyFile("bucket", "missing.txt", "other", "dest.txt"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("复制文件失败");
    }

    @Test
    @DisplayName("路径安全: objectKey 含 .. 越出根目录时抛 AiException")
    void uploadFile_rejectsPathTraversal() {
        InputStream is = new ByteArrayInputStream("evil".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.uploadFile("bucket", "../escape.txt", is, "text/plain"))
                .isInstanceOf(AiException.class)
                .hasMessageContaining("非法的文件路径");

        assertThat(tempDir.resolve("escape.txt")).doesNotExist();
    }
}
