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
package com.yangqiongai.ai.storage;

import java.io.InputStream;
import java.util.List;

/**
 * 文档对象存储
 * @author yangqiong
 */
public interface DocumentStorageService {

    /**
     * 上传文件
     * @param bucket
     * @param objectKey
     * @param inputStream
     * @param contentType
     * @return
     */
    String uploadFile(String bucket, String objectKey, InputStream inputStream, String contentType);

    /**
     * 下载文件
     * @param bucket
     * @param objectKey
     * @return
     */
    InputStream downloadFile(String bucket, String objectKey);

    /**
     * 删除文件
     * @param bucket
     * @param objectKey
     */
    void deleteFile(String bucket, String objectKey);

    /**
     * 获取文件访问URL
     * @param bucket
     * @param objectKey
     * @return
     */
    String getFileUrl(String bucket, String objectKey);

    /**
     * 判断文件是否存在
     * @param bucket
     * @param objectKey
     * @return
     */
    boolean fileExists(String bucket, String objectKey);

    /**
     * 列出指定前缀的文件
     * @param bucket
     * @param prefix
     * @return
     */
    List<String> listFiles(String bucket, String prefix);

    /**
     * 创建桶（如不存在）
     * @param bucket
     */
    void createBucketIfNotExists(String bucket);

    /**
     * 复制文件
     * @param sourceBucket
     * @param sourceKey
     * @param destBucket
     * @param destKey
     */
    void copyFile(String sourceBucket, String sourceKey, String destBucket, String destKey);
}
