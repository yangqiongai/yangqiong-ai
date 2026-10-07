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
package com.yangqiongai.ai.memory.graph;

import java.time.LocalDateTime;

/**
 * 知识三元组
 * @author yangqiong
 */
public class KnowledgeTriple {

    /**
     * 三元组ID（UUID，对应Qdrant点ID）
     */
    private String tripleId;

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 主体（如"用户"）
     */
    private String subject;

    /**
     * 谓词（如"喜欢"）
     */
    private String predicate;

    /**
     * 客体（如"咖啡"）
     */
    private String object;

    /**
     * 是否为因果边（true: A导致B; false: A喜欢B）
     */
    private boolean isCausal;

    /**
     * 生效时间
     */
    private LocalDateTime validFrom;

    /**
     * 失效时间（null表示永久有效）
     */
    private LocalDateTime validUntil;

    /**
     * 置信度 0-1
     */
    private Double confidence;

    public KnowledgeTriple() {
    }

    public KnowledgeTriple(String subject, String predicate, String object, boolean isCausal) {
        this.subject = subject;
        this.predicate = predicate;
        this.object = object;
        this.isCausal = isCausal;
    }

    public String getTripleId() {
        return tripleId;
    }

    public void setTripleId(String tripleId) {
        this.tripleId = tripleId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getPredicate() {
        return predicate;
    }

    public void setPredicate(String predicate) {
        this.predicate = predicate;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public boolean isCausal() {
        return isCausal;
    }

    public void setCausal(boolean causal) {
        isCausal = causal;
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

    public Double getConfidence() {
        return confidence;
    }

    public void setConfidence(Double confidence) {
        this.confidence = confidence;
    }

    /**
     * 拼接三元组文本用于向量化
     * @return
     */
    public String toText() {
        return subject + " " + predicate + " " + object;
    }

    /**
     * 三元组简短描述
     * @return
     */
    @Override
    public String toString() {
        return subject + " -[" + predicate + (isCausal ? "(因果)" : "") + "]-> " + object;
    }
}
