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

/**
 * 切片候选
 * @author yangqiong
 */
public class ChunkCandidate {

    /**
     * 文本内容
     */
    private String text;

    /**
     * 文档ID
     */
    private String docId;

    /**
     * 来源知识库ID
     */
    private String kbId;

    /**
     * 来源文档名称
     */
    private String docName;

    /**
     * 切片ID
     */
    private String sliceId;

    /**
     * 相似度分数
     */
    private double score;

    /**
     * 是否子块命中
     */
    private boolean isChildHit;

    /**
     * 父块ID
     */
    private String parentId;

    /**
     * 前邻切片ID
     */
    private String prevSliceId;

    /**
     * 后邻切片ID
     */
    private String nextSliceId;

    public ChunkCandidate() {
    }

    public ChunkCandidate(String text, String docId, String sliceId,
                          double score, boolean isChildHit, String parentId) {
        this.text = text;
        this.docId = docId;
        this.sliceId = sliceId;
        this.score = score;
        this.isChildHit = isChildHit;
        this.parentId = parentId;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getDocId() {
        return docId;
    }

    public void setDocId(String docId) {
        this.docId = docId;
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

    public boolean isChildHit() {
        return isChildHit;
    }

    public void setChildHit(boolean childHit) {
        isChildHit = childHit;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getPrevSliceId() {
        return prevSliceId;
    }

    public void setPrevSliceId(String prevSliceId) {
        this.prevSliceId = prevSliceId;
    }

    public String getNextSliceId() {
        return nextSliceId;
    }

    public void setNextSliceId(String nextSliceId) {
        this.nextSliceId = nextSliceId;
    }
}
