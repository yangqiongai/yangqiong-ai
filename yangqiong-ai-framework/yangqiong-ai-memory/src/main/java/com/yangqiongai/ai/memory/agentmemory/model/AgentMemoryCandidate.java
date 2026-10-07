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

/**
 * Agent运行记忆写入候选
 * @author yangqiong
 */
public class AgentMemoryCandidate {

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 用户锚点
     */
    private String userAnchor;

    /**
     * 来源会话ID
     */
    private String sessionId;

    /**
     * 作用域ID
     */
    private String scopeId;

    /**
     * 来源任务ID
     */
    private String sourceTaskId;

    /**
     * 来源用户
     */
    private String sourceUser;

    /**
     * 记忆类型(EPISODIC/SEMANTIC/PROCEDURAL)，null时默认EPISODIC
     */
    private String memoryType;

    /**
     * 记忆内容
     */
    private String content;

    /**
     * 置信度（0-1），null时使用默认值
     */
    private Double confidence;

    /**
     * TTL秒数（null表示永久有效）
     */
    private Long ttlSeconds;

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

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getScopeId() {
        return scopeId;
    }

    public void setScopeId(String scopeId) {
        this.scopeId = scopeId;
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

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    public Long getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Long ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
