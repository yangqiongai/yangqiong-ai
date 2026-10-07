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

import com.yangqiongai.ai.common.scope.DefaultObjectKeyResolver;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.messages.Item;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MinioDocumentStorage 对象Key解析器集成测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MinioDocumentStorage 对象Key解析器测试")
class MinioDocumentStorageResolverTest {

    @Mock
    private MinioClient minioClient;

    @Captor
    private ArgumentCaptor<PutObjectArgs> putArgsCaptor;

    @Captor
    private ArgumentCaptor<GetObjectArgs> getArgsCaptor;

    @Captor
    private ArgumentCaptor<RemoveObjectArgs> removeArgsCaptor;

    @Captor
    private ArgumentCaptor<GetPresignedObjectUrlArgs> presignedArgsCaptor;

    @Captor
    private ArgumentCaptor<StatObjectArgs> statArgsCaptor;

    @Captor
    private ArgumentCaptor<ListObjectsArgs> listArgsCaptor;

    @Captor
    private ArgumentCaptor<CopyObjectArgs> copyArgsCaptor;

    private MinioDocumentStorage service;

    @BeforeEach
    void setUp() {
        service = new MinioDocumentStorage(minioClient);
    }

    @Test
    @DisplayName("默认解析器: uploadFile 使用原始 objectKey")
    void uploadFile_usesOriginalObjectKey_withDefaultResolver() throws Exception {
        ReflectionTestUtils.setField(service, "objectKeyResolver", new DefaultObjectKeyResolver());
        ObjectWriteResponse response = new ObjectWriteResponse(null, null, null, null, null, null);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(response);

        InputStream is = new ByteArrayInputStream("content".getBytes());
        String result = service.uploadFile("bucket", "obj/001.txt", is, "text/plain");

        assertThat(result).isEqualTo("obj/001.txt");
        verify(minioClient).putObject(putArgsCaptor.capture());
        assertThat(putArgsCaptor.getValue().object()).isEqualTo("obj/001.txt");
    }

    @Test
    @DisplayName("作用域解析器: uploadFile 使用 scopeId/objectKey")
    void uploadFile_usesResolvedObjectKey_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        ObjectWriteResponse response = new ObjectWriteResponse(null, null, null, null, null, null);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenReturn(response);

        InputStream is = new ByteArrayInputStream("content".getBytes());
        String result = service.uploadFile("bucket", "obj/001.txt", is, "text/plain");

        assertThat(result).isEqualTo("obj/001.txt");
        verify(minioClient).putObject(putArgsCaptor.capture());
        assertThat(putArgsCaptor.getValue().object()).isEqualTo("tenant-001/obj/001.txt");
    }

    @Test
    @DisplayName("多租户解析器: downloadFile 使用解析后的 objectKey")
    void downloadFile_usesResolvedObjectKey_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        GetObjectResponse response = org.mockito.Mockito.mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        service.downloadFile("bucket", "obj/001.txt");

        verify(minioClient).getObject(getArgsCaptor.capture());
        assertThat(getArgsCaptor.getValue().object()).isEqualTo("tenant-001/obj/001.txt");
    }

    @Test
    @DisplayName("默认解析器: downloadFile 使用原始 objectKey")
    void downloadFile_usesOriginalObjectKey_withDefaultResolver() throws Exception {
        ReflectionTestUtils.setField(service, "objectKeyResolver", new DefaultObjectKeyResolver());
        GetObjectResponse response = org.mockito.Mockito.mock(GetObjectResponse.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(response);

        service.downloadFile("bucket", "obj/001.txt");

        verify(minioClient).getObject(getArgsCaptor.capture());
        assertThat(getArgsCaptor.getValue().object()).isEqualTo("obj/001.txt");
    }

    @Test
    @DisplayName("多租户解析器: deleteFile 使用解析后的 objectKey")
    void deleteFile_usesResolvedObjectKey_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        org.mockito.Mockito.doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        service.deleteFile("bucket", "obj/001.txt");

        verify(minioClient).removeObject(removeArgsCaptor.capture());
        assertThat(removeArgsCaptor.getValue().object()).isEqualTo("tenant-001/obj/001.txt");
    }

    @Test
    @DisplayName("多租户解析器: getFileUrl 使用解析后的 objectKey")
    void getFileUrl_usesResolvedObjectKey_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://example.com/tenant-001/obj/001.txt");

        service.getFileUrl("bucket", "obj/001.txt");

        verify(minioClient).getPresignedObjectUrl(presignedArgsCaptor.capture());
        assertThat(presignedArgsCaptor.getValue().object()).isEqualTo("tenant-001/obj/001.txt");
    }

    @Test
    @DisplayName("默认解析器: getFileUrl 使用原始 objectKey")
    void getFileUrl_usesOriginalObjectKey_withDefaultResolver() throws Exception {
        ReflectionTestUtils.setField(service, "objectKeyResolver", new DefaultObjectKeyResolver());
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn("http://example.com/obj/001.txt");

        service.getFileUrl("bucket", "obj/001.txt");

        verify(minioClient).getPresignedObjectUrl(presignedArgsCaptor.capture());
        assertThat(presignedArgsCaptor.getValue().object()).isEqualTo("obj/001.txt");
    }

    @Test
    @DisplayName("多租户解析器: fileExists 使用解析后的 objectKey")
    void fileExists_usesResolvedObjectKey_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        StatObjectResponse response = org.mockito.Mockito.mock(StatObjectResponse.class);
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(response);

        boolean exists = service.fileExists("bucket", "obj/001.txt");

        assertThat(exists).isTrue();
        verify(minioClient).statObject(statArgsCaptor.capture());
        assertThat(statArgsCaptor.getValue().object()).isEqualTo("tenant-001/obj/001.txt");
    }

    @Test
    @DisplayName("多租户解析器: listFiles 使用解析后的 prefix")
    void listFiles_usesResolvedPrefix_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        Item item = org.mockito.Mockito.mock(Item.class);
        when(item.objectName()).thenReturn("tenant-001/obj/001.txt");
        io.minio.Result<Item> result = org.mockito.Mockito.mock(io.minio.Result.class);
        when(result.get()).thenReturn(item);
        when(minioClient.listObjects(any(ListObjectsArgs.class)))
                .thenReturn(List.of(result));

        service.listFiles("bucket", "obj/");

        verify(minioClient).listObjects(listArgsCaptor.capture());
        assertThat(listArgsCaptor.getValue().prefix()).isEqualTo("tenant-001/obj/");
    }

    @Test
    @DisplayName("多租户解析器: copyFile 同时解析 sourceKey 和 destKey")
    void copyFile_resolvesBothKeys_withTenantResolver() throws Exception {
        ObjectKeyResolver tenantResolver = key -> "tenant-001/" + key;
        ReflectionTestUtils.setField(service, "objectKeyResolver", tenantResolver);
        ObjectWriteResponse response = new ObjectWriteResponse(null, null, null, null, null, null);
        when(minioClient.copyObject(any(CopyObjectArgs.class))).thenReturn(response);

        service.copyFile("src-bucket", "src.txt", "dest-bucket", "dest.txt");

        verify(minioClient).copyObject(copyArgsCaptor.capture());
        CopyObjectArgs args = copyArgsCaptor.getValue();
        assertThat(args.object()).isEqualTo("tenant-001/dest.txt");
    }
}
