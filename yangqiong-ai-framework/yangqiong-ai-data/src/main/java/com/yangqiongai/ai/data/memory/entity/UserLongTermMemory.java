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
package com.yangqiongai.ai.data.memory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * 用户长期记忆
 * @author yangqiong
 */
@TableName("ai_user_long_term_memory")
public class UserLongTermMemory extends ScopeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_UUID)
    private Long id;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 记忆类型(SUMMARY/FACT/PREFERENCE/CONSTRAINT/PROFILE/EVENT/DEPRECATED)
     */
    private String memoryType;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 关联会话ID列表(JSON)
     */
    private String relatedSessionIds;

    /**
     * 重要性评分
     */
    private Integer importanceScore;

    /**
     * 基础分（创建时初始分，衰减计算用，不被衰减修改）
     */
    private Integer baseScore;

    /**
     * 访问次数
     */
    private Integer accessCount;

    /**
     * 标签（逗号分隔，用于过滤）
     */
    private String tags;

    /**
     * 记忆来源（LLM_EXTRACTED/USER_DECLARED/SYSTEM_DEFAULT），默认LLM_EXTRACTED
     */
    private String source;

    /**
     * 序列化向量（BLOB 兜底，Qdrant 不可用时使用）
     */
    private byte[] embedding;

    /**
     * 生效时间
     */
    private LocalDateTime validFrom;

    /**
     * 失效时间（null表示永久有效）
     */
    private LocalDateTime validUntil;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMemoryType() {
        return memoryType;
    }

    public void setMemoryType(String memoryType) {
        this.memoryType = memoryType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRelatedSessionIds() {
        return relatedSessionIds;
    }

    public void setRelatedSessionIds(String relatedSessionIds) {
        this.relatedSessionIds = relatedSessionIds;
    }

    public Integer getImportanceScore() {
        return importanceScore;
    }

    public void setImportanceScore(Integer importanceScore) {
        this.importanceScore = importanceScore;
    }

    public Integer getBaseScore() {
        return baseScore;
    }

    public void setBaseScore(Integer baseScore) {
        this.baseScore = baseScore;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public byte[] getEmbedding() {
        return embedding;
    }

    public void setEmbedding(byte[] embedding) {
        this.embedding = embedding;
    }

    public LocalDateTime getValidFrom() {
        return validFrom;
    }

    public void setValidFrom(LocalDateTime validFrom) {
        this.validFrom = validFrom;
    }

    public LocalDateTime getValidUntil() {
        return validUntil;
    }

    public void setValidUntil(LocalDateTime validUntil) {
        this.validUntil = validUntil;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }
}
