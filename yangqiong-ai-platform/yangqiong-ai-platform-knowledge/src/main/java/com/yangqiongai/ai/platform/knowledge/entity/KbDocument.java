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
package com.yangqiongai.ai.platform.knowledge.entity;

import com.yangqiongai.ai.common.entity.ScopeEntity;
import com.yangqiongai.ai.common.rag.DocumentStatus;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * 知识库文档
 * @author yangqiong
 */
@TableName("ai_kb_document")
public class KbDocument extends ScopeEntity {

    /**
     * 主键（内部使用，不暴露给前端）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文档业务ID（外部使用，由系统自动生成）
     */
    private String docId;

    /**
     * 所属用户ID
     */
    private String userId;

    /**
     * 知识库ID
     */
    private String kbId;

    /**
     * 文档名称
     */
    private String docName;

    /**
     * 文件类型
     */
    private String fileType;

    /**
     * 文件大小(字节)
     */
    private Long fileSize;

    /**
     * 文件内容MD5哈希（用于去重）
     */
    private String contentHash;

    /**
     * 文件存储的bucket名称
     */
    private String fileBucket;

    /**
     * MinIO文件路径
     */
    private String filePath;

    /**
     * 数据来源类型: FILE/DATABASE/API/WEBPAGE/TEXT/CRAWLER
     */
    private String sourceType;

    /**
     * 直接文本内容（TEXT/API/WEBPAGE等来源，FILE来源为空）
     */
    private String content;

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
     * 地区名列表
     */
    private String regions;

    @JsonIgnore
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getDocName() {
        return docName;
    }

    public void setDocName(String docName) {
        this.docName = docName;
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

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getFileBucket() {
        return fileBucket;
    }

    public void setFileBucket(String fileBucket) {
        this.fileBucket = fileBucket;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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

    public String getRegions() {
        return regions;
    }

    public void setRegions(String regions) {
        this.regions = regions;
    }
}
