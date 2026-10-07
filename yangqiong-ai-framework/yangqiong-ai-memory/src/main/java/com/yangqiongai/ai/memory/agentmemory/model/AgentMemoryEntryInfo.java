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
package com.yangqiongai.ai.memory.agentmemory.model;

import java.time.LocalDateTime;

/**
 * Agent运行记忆条目
 * @author yangqiong
 */
public class AgentMemoryEntryInfo {

    /**
     * 状态：生效
     */
    public static final String STATUS_ACTIVE = "ACTIVE";

    /**
     * 状态：归档（TTL过期/低置信/负反馈淘汰，不参与检索）
     */
    public static final String STATUS_STALE = "STALE";

    /**
     * 状态：隔离（可疑来源，不参与检索，待治理处置）
     */
    public static final String STATUS_QUARANTINED = "QUARANTINED";

    /**
     * 状态：已擦除
     */
    public static final String STATUS_ERASED = "ERASED";

    /**
     * 类型：情景记忆（任务级事件）
     */
    public static final String TYPE_EPISODIC = "EPISODIC";

    /**
     * 类型：语义记忆（事实）
     */
    public static final String TYPE_SEMANTIC = "SEMANTIC";

    /**
     * 类型：程序记忆（经验规则，可版本化）
     */
    public static final String TYPE_PROCEDURAL = "PROCEDURAL";

    /**
     * 主键
     */
    private Long id;

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 用户锚点（记忆归属用户，任务级溯源维度）
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
     * 向量索引引用（pointId，向量库侧关联标识）
     */
    private String embeddingRef;

    /**
     * 来源任务ID（任务级溯源）
     */
    private String sourceTaskId;

    /**
     * 来源用户
     */
    private String sourceUser;

    /**
     * 置信度（0-1，检索排序与淘汰依据）
     */
    private Double confidence;

    /**
     * TTL过期时间（null表示永久有效）
     */
    private LocalDateTime ttlExpireTime;

    /**
     * 状态(ACTIVE/STALE/QUARANTINED/ERASED)
     */
    private String status;

    /**
     * 内容摘要哈希（去重键，agent+user+type+content规范化后SHA-256）
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
     * 版本号（PROCEDURAL程序记忆版本化，普通类型恒为1）
     */
    private Integer versionNo;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

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

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
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

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
