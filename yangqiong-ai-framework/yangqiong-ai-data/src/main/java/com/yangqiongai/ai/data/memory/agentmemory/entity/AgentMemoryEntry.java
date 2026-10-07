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
package com.yangqiongai.ai.data.memory.agentmemory.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.yangqiongai.ai.common.entity.ScopeEntity;

import java.time.LocalDateTime;

/**
 * Agent运行记忆条目
 * @author yangqiong
 */
@TableName("ai_agent_memory_entry")
public class AgentMemoryEntry extends ScopeEntity {

    /**
     * 主键
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 用户锚点
     */
    private String userAnchor;

    /**
     * 记忆类型(EPISODIC/SEMANTIC/PROCEDURAL)
     */
    private String memoryType;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 向量索引引用
     */
    private String embeddingRef;

    /**
     * 来源任务ID
     */
    private String sourceTaskId;

    /**
     * 来源用户
     */
    private String sourceUser;

    /**
     * 置信度
     */
    private Double confidence;

    /**
     * TTL过期时间
     */
    private LocalDateTime ttlExpireTime;

    /**
     * 状态(ACTIVE/STALE/QUARANTINED/ERASED)
     */
    private String status;

    /**
     * 内容摘要哈希（去重键）
     */
    private String inputHash;

    /**
     * 访问次数
     */
    private Integer accessCount;

    /**
     * 最后访问时间
     */
    private LocalDateTime lastAccessedAt;

    /**
     * 版本号
     */
    private Integer versionNo;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getUserAnchor() {
        return userAnchor;
    }

    public void setUserAnchor(String userAnchor) {
        this.userAnchor = userAnchor;
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

    public String getEmbeddingRef() {
        return embeddingRef;
    }

    public void setEmbeddingRef(String embeddingRef) {
        this.embeddingRef = embeddingRef;
    }

    public String getSourceTaskId() {
        return sourceTaskId;
    }

    public void setSourceTaskId(String sourceTaskId) {
        this.sourceTaskId = sourceTaskId;
    }

    public String getSourceUser() {
        return sourceUser;
    }

    public void setSourceUser(String sourceUser) {
        this.sourceUser = sourceUser;
    }

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public LocalDateTime getTtlExpireTime() {
        return ttlExpireTime;
    }

    public void setTtlExpireTime(LocalDateTime ttlExpireTime) {
        this.ttlExpireTime = ttlExpireTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getInputHash() {
        return inputHash;
    }

    public void setInputHash(String inputHash) {
        this.inputHash = inputHash;
    }

    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public void setLastAccessedAt(LocalDateTime lastAccessedAt) {
        this.lastAccessedAt = lastAccessedAt;
    }

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }
}
