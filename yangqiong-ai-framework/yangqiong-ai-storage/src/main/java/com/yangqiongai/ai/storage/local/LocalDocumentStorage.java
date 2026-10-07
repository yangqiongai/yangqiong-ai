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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import com.yangqiongai.ai.storage.DocumentStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地文件目录存储
 * @author yangqiong
 */
public class LocalDocumentStorage implements DocumentStorageService {

    private static final Logger log = LoggerFactory.getLogger(LocalDocumentStorage.class);

    private final LocalProperties properties;

    /**
     * 对象Key解析器
     */
    @Autowired
    private ObjectKeyResolver objectKeyResolver;

    public LocalDocumentStorage(LocalProperties properties) {
        this.properties = properties;
    }

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
        File file = toFile(bucket, resolvedKey);
        try {
            createParentDirs(file);
            Files.copy(inputStream, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return objectKey;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "上传文件失败: " + objectKey, e);
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
        File file = toFile(bucket, resolvedKey);
        try {
            return Files.newInputStream(file.toPath());
        } catch (NoSuchFileException e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "文件不存在: " + objectKey, e);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "下载文件失败: " + objectKey, e);
        }
    }

    /**
     * 删除文件
     * @param bucket
     * @param objectKey
     */
    public void deleteFile(String bucket, String objectKey) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        File file = toFile(bucket, resolvedKey);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "删除文件失败: " + objectKey, e);
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
        return "local://" + bucket + "/" + resolvedKey;
    }

    /**
     * 判断文件是否存在
     * @param bucket
     * @param objectKey
     * @return
     */
    public boolean fileExists(String bucket, String objectKey) {
        String resolvedKey = objectKeyResolver.resolve(objectKey);
        File file = toFile(bucket, resolvedKey);
        return file.isFile();
    }

    /**
     * 列出指定前缀的文件
     * @param bucket
     * @param prefix
     * @return
     */
    public List<String> listFiles(String bucket, String prefix) {
        String resolvedPrefix = objectKeyResolver.resolve(prefix);
        Path root = Paths.get(properties.getBaseDir(), bucket).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            return new ArrayList<>();
        }
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> fileKeys = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .map(p -> root.relativize(p).toString().replace('\\', '/'))
                    .filter(k -> k.startsWith(resolvedPrefix))
                    .sorted()
                    .forEach(fileKeys::add);
            return fileKeys;
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "列出文件失败: " + prefix, e);
        }
    }

    /**
     * 创建桶（如不存在）
     * @param bucket
     */
    public void createBucketIfNotExists(String bucket) {
        try {
            Path dir = Paths.get(properties.getBaseDir(), bucket).toAbsolutePath().normalize();
            if (!Files.isDirectory(dir)) {
                Files.createDirectories(dir);
                log.info("创建本地存储目录: {}", dir);
            }
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "创建存储目录失败: " + bucket, e);
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
        File source = toFile(sourceBucket, resolvedSourceKey);
        File dest = toFile(destBucket, resolvedDestKey);
        try {
            if (!source.isFile()) {
                throw new NoSuchFileException(source.getPath());
            }
            createParentDirs(dest);
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR,
                    "复制文件失败: " + sourceBucket + "/" + sourceKey + " -> " + destBucket + "/" + destKey, e);
        }
    }

    /**
     * 解析对象Key对应的物理文件，并校验不越出存储根目录
     * @param bucket
     * @param resolvedKey
     * @return
     */
    private File toFile(String bucket, String resolvedKey) {
        Path root = Paths.get(properties.getBaseDir(), bucket).toAbsolutePath().normalize();
        Path file = root.resolve(resolvedKey).normalize();
        if (!file.startsWith(root)) {
            throw new AiException(AiErrorCode.STORAGE_LOCAL_ERROR, "非法的文件路径: " + resolvedKey);
        }
        return file.toFile();
    }

    /**
     * 创建文件父目录
     * @param file
     */
    private void createParentDirs(File file) throws IOException {
        Path parent = file.toPath().getParent();
        if (parent != null && !Files.isDirectory(parent)) {
            Files.createDirectories(parent);
        }
    }
}
