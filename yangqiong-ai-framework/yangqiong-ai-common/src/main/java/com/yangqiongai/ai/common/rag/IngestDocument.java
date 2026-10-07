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
package com.yangqiongai.ai.common.rag;

import java.util.HashMap;
import java.util.Map;

/**
 * 通用入库文档模型
 * @author yangqiong
 */
public class IngestDocument {

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 数据来源类型：FILE / DATABASE / API / WEBPAGE / TEXT / CRAWLER
     */
    private String sourceType;

    /**
     * 文件存储的bucket名称
     */
    private String fileBucket;

    /**
     * 文件路径（FILE模式，从对象存储下载解析）
     */
    private String filePath;

    /**
     * 直接文本内容（TEXT/API/WEBPAGE模式，无需文件下载）
     */
    private String content;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 版本标签
     */
    private String versionTag;

    /**
     * 文档状态
     */
    private DocumentStatus docStatus;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 切片数量
     */
    private Integer chunkCount;

    /**
     * 文档摘要
     */
    private String summary;

    /**
     * 元参数（不参与处理）：kbId, userId, sourceUrl 等
     */
    private Map<String, String> metadata = new HashMap<>();

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getFileBucket() {
        return fileBucket;
    }

    public void setFileBucket(String fileBucket) {
        this.fileBucket = fileBucket;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public DocumentStatus getDocStatus() {
        return docStatus;
    }

    public void setDocStatus(DocumentStatus docStatus) {
        this.docStatus = docStatus;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, String> metadata) {
        this.metadata = metadata;
    }

    /**
     * 从元参数中获取知识库ID
     * @return
     */
    public String getKbId() {
        return metadata != null ? metadata.get("kbId") : null;
    }

    /**
     * 从元参数中获取用户ID
     * @return
     */
    public String getUserId() {
        return metadata != null ? metadata.get("userId") : null;
    }
}
