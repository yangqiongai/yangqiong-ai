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
package com.yangqiongai.ai.rag.model;

import java.util.Map;
import java.util.Objects;

/**
 * 检索证据
 * @author yangqiong
 */
public class RetrievalEvidence {

    /**
     * 内容
     */
    private String content;

    /**
     * 来源知识库ID
     */
    private String kbId;

    /**
     * 来源知识库名称
     */
    private String kbName;

    /**
     * 来源文档ID
     */
    private String sourceDocId;

    /**
     * 来源文档名称
     */
    private String sourceDocName;

    /**
     * 切片ID
     */
    private String sliceId;

    /**
     * 相似度分数
     */
    private double score;

    /**
     * 请求数据体
     */
    private Map<String, Object> body;

    public RetrievalEvidence() {
    }

    public RetrievalEvidence(String content, String sourceDocId, String sourceDocName,
                             String sliceId, double score, Map<String, Object> body) {
        this.content = content;
        this.sourceDocId = sourceDocId;
        this.sourceDocName = sourceDocName;
        this.sliceId = sliceId;
        this.score = score;
        this.body = body;
    }

    public String getKbId() {
        return kbId;
    }

    public void setKbId(String kbId) {
        this.kbId = kbId;
    }

    public String getKbName() {
        return kbName;
    }

    public void setKbName(String kbName) {
        this.kbName = kbName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSourceDocId() {
        return sourceDocId;
    }

    public void setSourceDocId(String sourceDocId) {
        this.sourceDocId = sourceDocId;
    }

    public String getSourceDocName() {
        return sourceDocName;
    }

    public void setSourceDocName(String sourceDocName) {
        this.sourceDocName = sourceDocName;
    }

    public String getSliceId() {
        return sliceId;
    }

    public void setSliceId(String sliceId) {
        this.sliceId = sliceId;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setBody(Map<String, Object> body) {
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RetrievalEvidence that = (RetrievalEvidence) o;
        return Objects.equals(content, that.content) && Objects.equals(sourceDocId, that.sourceDocId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, sourceDocId);
    }
}
