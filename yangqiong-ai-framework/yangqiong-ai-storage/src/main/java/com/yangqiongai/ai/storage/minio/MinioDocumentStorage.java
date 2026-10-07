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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import com.yangqiongai.ai.storage.DocumentStorageService;
import io.minio.CopyObjectArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteArgs;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.http.Method;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * MinIO存储
 * @author yangqiong
 */
public class MinioDocumentStorage implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(MinioDocumentStorage.class);

    private final MinioClient minioClient;

    /**
     * 对象Key解析器
     */
    @Autowired
    private ObjectKeyResolver objectKeyResolver;

    /**
     * 上传文件
     * @param bucket
     * @param objectKey
     * @param inputStream
     * @param contentType
     * @return
     */
    public String uploadFile(String bucket, String objectKey, InputStream inputStream, String contentType) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(resolvedKey)
                            .stream(inputStream, -1, ObjectWriteArgs.MAX_PART_SIZE)
                            .contentType(contentType)
                            .build()
            );
            return objectKey;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR, "上传文件失败: " + objectKey, e);
        }
    }

    /**
     * 下载文件
     * @param bucket
     * @param objectKey
     * @return
     */
    public InputStream downloadFile(String bucket, String objectKey) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        try {
            return minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(bucket)
                            .object(resolvedKey)
                            .build()
            );
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR, "下载文件失败: " + objectKey, e);
        }
    }

    /**
     * 删除文件
     * @param bucket
     * @param objectKey
     */
    public void deleteFile(String bucket, String objectKey) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(resolvedKey)
                            .build()
            );
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR, "删除文件失败: " + objectKey, e);
        }
    }

    /**
     * 获取文件访问URL
     * @param bucket
     * @param objectKey
     * @return
     */
    public String getFileUrl(String bucket, String objectKey) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(resolvedKey)
                            .expiry(7, TimeUnit.DAYS)
                            .build()
            );
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR, "获取文件URL失败: " + objectKey, e);
        }
    }

    /**
     * 判断文件是否存在
     * @param bucket
     * @param objectKey
     * @return
     */
    public boolean fileExists(String bucket, String objectKey) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        try {
            StatObjectResponse response = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucket)
                            .object(resolvedKey)
                            .build()
            );
            return response != null;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 列出指定前缀的文件
     * @param bucket
     * @param prefix
     * @return
     */
    public List<String> listFiles(String bucket, String prefix) {
        String resolvedPrefix = objectKeyResolver.resolve(prefix);
        try {
            List<String> fileKeys = new ArrayList<>();
            Iterable<io.minio.Result<io.minio.messages.Item>> results = minioClient.listObjects(
                    ListObjectsArgs.builder()
                            .bucket(bucket)
                            .prefix(resolvedPrefix)
                            .recursive(true)
                            .build()
            );
            for (io.minio.Result<io.minio.messages.Item> result : results) {
                fileKeys.add(result.get().objectName());
            }
            return fileKeys;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR, "列出文件失败: " + prefix, e);
        }
    }

    /**
     * 创建桶（如不存在）
     * @param bucket
     */
    public void createBucketIfNotExists(String bucket) {
        try {
            boolean exists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder()
                            .bucket(bucket)
                            .build()
            );
            if (!exists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucket)
                                .build()
                );
                log.info("创建MinIO桶: {}", bucket);
            }
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR, "创建桶失败: " + bucket, e);
        }
    }

    /**
     * 复制文件
     * @param sourceBucket
     * @param sourceKey
     * @param destBucket
     * @param destKey
     */
    public void copyFile(String sourceBucket, String sourceKey, String destBucket, String destKey) {
        String resolvedSourceKey = objectKeyResolver.resolve(sourceKey);
        String resolvedDestKey = objectKeyResolver.resolve(destKey);
        try {
            minioClient.copyObject(
                    CopyObjectArgs.builder()
                            .bucket(destBucket)
                            .object(resolvedDestKey)
                            .source(
                                    io.minio.CopySource.builder()
                                            .bucket(sourceBucket)
                                            .object(resolvedSourceKey)
                                            .build()
                            )
                            .build()
            );
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_MINIO_ERROR,
                    "复制文件失败: " + sourceBucket + "/" + sourceKey + " -> " + destBucket + "/" + destKey, e);
        }
    }

    public MinioDocumentStorage(MinioClient minioClient) {
        this.minioClient = minioClient;
    }
}
