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

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yangqiongai.ai.common.entity.ScopeEntity;

/**
 * 知识库
 * @author yangqiong
 */
@TableName("ai_knowledge_base")
public class KnowledgeBase extends ScopeEntity {

    /**
     * 主键（内部使用，不暴露给前端）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 知识库业务ID（外部使用，由系统自动生成）
     */
    private String kbId;

    /**
     * 所属用户ID
     */
    private String userId;

    /**
     * 知识库名称
     */
    private String kbName;

    /**
     * 知识库描述
     */
    private String kbDescription;

    /**
     * 嵌入模型
     */
    private String embeddingModel;

    /**
     * 切片策略
     */
    private String chunkStrategy;

    /**
     * 切片配置
     */
    private String chunkConfig;

    /**
     * 当前活跃版本
     */
    private String activeVersion;

    /**
     * 知识库状态
     */
    private Integer kbStatus;

    /**
     * 图标名称或base64图片
     */
    private String kbIcon;

    /**
     * 备注
     */
    private String remark;

    /**
     * 文档数（列表统计字段，非表字段）
     */
    @com.baomidou.mybatisplus.annotation.TableField(exist = false)
    private Long documentCount;

    @JsonIgnore
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKbName() {
        return kbName;
    }

    public void setKbName(String kbName) {
        this.kbName = kbName;
    }

    public String getKbDescription() {
        return kbDescription;
    }

    public void setKbDescription(String kbDescription) {
        this.kbDescription = kbDescription;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public String getChunkStrategy() {
        return chunkStrategy;
    }

    public void setChunkStrategy(String chunkStrategy) {
        this.chunkStrategy = chunkStrategy;
    }

    public String getChunkConfig() {
        return chunkConfig;
    }

    public void setChunkConfig(String chunkConfig) {
        this.chunkConfig = chunkConfig;
    }

    public String getActiveVersion() {
        return activeVersion;
    }

    public void setActiveVersion(String activeVersion) {
        this.activeVersion = activeVersion;
    }

    public Integer getKbStatus() {
        return kbStatus;
    }

    public void setKbStatus(Integer kbStatus) {
        this.kbStatus = kbStatus;
    }

    public String getKbIcon() {
        return kbIcon;
    }

    public void setKbIcon(String kbIcon) {
        this.kbIcon = kbIcon;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getDocumentCount() {
        return documentCount;
    }

    public void setDocumentCount(Long documentCount) {
        this.documentCount = documentCount;
    }
}
