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
package com.yangqiongai.ai.storage.minio;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.DefaultObjectKeyResolver;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import io.minio.*;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MinioDocumentStorage 单元测试")
class MinioDocumentStorageTest {

    @Mock
    private MinioClient minioClient;

    private MinioDocumentStorage service;

    @BeforeEach
    void setUp() {
        service = new MinioDocumentStorage(minioClient);
        ObjectKeyResolver defaultResolver = new DefaultObjectKeyResolver();
        ReflectionTestUtils.setField(service, "objectKeyResolver", defaultResolver);
    }

    @Test
    @DisplayName("uploadFile: 上传成功返回objectKey")
    void uploadFile_uploadsSuccessfully() throws Exception {
        ObjectWriteResponse response = mock(ObjectWriteResponse.class);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(response);

        InputStream is = new ByteArrayInputStream("content".getBytes());
        String result = service.uploadFile("test-bucket", "test-key", is, "text/plain");

        assertThat(result).isEqualTo("test-key");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("uploadFile: 上传失败抛出AiException")
    void uploadFile_failure_throwsAiException() throws Exception {
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("upload failed"));

        InputStream is = new ByteArrayInputStream("content".getBytes());

        assertThatThrownBy(() -> service.uploadFile("test-bucket", "test-key", is, "text/plain"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("downloadFile: 下载成功返回InputStream")
    void downloadFile_downloadsSuccessfully() throws Exception {
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        InputStream result = service.downloadFile("test-bucket", "test-key");

        assertThat(result).isNotNull();
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    @DisplayName("downloadFile: 下载失败抛出AiException")
    void downloadFile_failure_throwsAiException() throws Exception {
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("download failed"));

        assertThatThrownBy(() -> service.downloadFile("test-bucket", "test-key"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("deleteFile: 删除成功")
    void deleteFile_deletesSuccessfully() throws Exception {
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        service.deleteFile("test-bucket", "test-key");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    @DisplayName("deleteFile: 删除失败抛出AiException")
    void deleteFile_failure_throwsAiException() throws Exception {
        doThrow(new RuntimeException("delete failed")).when(minioClient).removeObject(any(RemoveObjectArgs.class));

        assertThatThrownBy(() -> service.deleteFile("test-bucket", "test-key"))
                .isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("fileExists: 文件存在返回true")
    void fileExists_returnsTrue() throws Exception {
        StatObjectResponse response = mock(StatObjectResponse.class);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(response);

        boolean result = service.fileExists("test-bucket", "test-key");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("fileExists: 文件不存在返回false")
    void fileExists_returnsFalse() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("not found"));

        boolean result = service.fileExists("test-bucket", "test-key");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("fileExists: 异常返回false")
    void fileExists_exception_returnsFalse() throws Exception {
        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("connection error"));

        boolean result = service.fileExists("test-bucket", "test-key");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("createBucketIfNotExists: 桶不存在时创建")
    void createBucketIfNotExists_createsBucket() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);
        doNothing().when(minioClient).makeBucket(any(MakeBucketArgs.class));

        service.createBucketIfNotExists("new-bucket");

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("createBucketIfNotExists: 桶已存在不创建")
    void createBucketIfNotExists_bucketExists_doesNotCreate() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);

        service.createBucketIfNotExists("existing-bucket");

        verify(minioClient).bucketExists(any(BucketExistsArgs.class));
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    @DisplayName("listFiles: 返回文件列表")
    @SuppressWarnings("unchecked")
    void listFiles_returnsFileList() throws Exception {
        Item item1 = mock(Item.class);
        when(item1.objectName()).thenReturn("file1.txt");
        Item item2 = mock(Item.class);
        when(item2.objectName()).thenReturn("file2.txt");

        io.minio.Result<Item> result1 = mock(io.minio.Result.class);
        when(result1.get()).thenReturn(item1);
        io.minio.Result<Item> result2 = mock(io.minio.Result.class);
        when(result2.get()).thenReturn(item2);

        Iterable<io.minio.Result<Item>> iterable = List.of(result1, result2);
        when(minioClient.listObjects(any(ListObjectsArgs.class))).thenReturn(iterable);

        List<String> files = service.listFiles("test-bucket", "prefix/");

        assertThat(files).containsExactly("file1.txt", "file2.txt");
    }

    @Test
    @DisplayName("copyFile: 复制成功")
    void copyFile_copiesSuccessfully() throws Exception {
        ObjectWriteResponse response = mock(ObjectWriteResponse.class);
        when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(response);

        service.copyFile("src-bucket", "src-key", "dest-bucket", "dest-key");

        verify(minioClient).copyObject(any(CopyObjectArgs.class));
    }

    @Test
    @DisplayName("copyFile: 复制失败抛出AiException")
    void copyFile_failure_throwsAiException() throws Exception {
        when(minioClient.copyObject(any(CopyObjectArgs.class))).thenThrow(new RuntimeException("copy failed"));

        assertThatThrownBy(() -> service.copyFile("src-bucket", "src-key", "dest-bucket", "dest-key"))
                .isInstanceOf(AiException.class);
    }
}
