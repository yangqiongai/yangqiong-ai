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

import java.util.List;
import java.util.Set;

/**
 * 召回测试请求
 * @author yangqiong
 */
public class RecallRequest {

    /**
     * 查询语句列表
     */
    private List<String> queries;

    /**
     * 知识库ID列表
     */
    private List<String> kbIds;

    /**
     * TopK
     */
    private int topK;

    /**
     * 最低相似度阈值
     */
    private double minScore;

    /**
     * 预设的相关文档ID集合（用于计算真实召回率，可选）
     */
    private Set<String> expectedRelevantDocIds;

    public List<String> getQueries() {
        return queries;
    }

    public void setQueries(List<String> queries) {
        this.queries = queries;
    }

    public List<String> getKbIds() {
        return kbIds;
    }

    public void setKbIds(List<String> kbIds) {
        this.kbIds = kbIds;
    }

    public int getTopK() {
        return topK;
    }

    public void setTopK(int topK) {
        this.topK = topK;
    }

    public double getMinScore() {
        return minScore;
    }

    public void setMinScore(double minScore) {
        this.minScore = minScore;
    }

    public Set<String> getExpectedRelevantDocIds() {
        return expectedRelevantDocIds;
    }

    public void setExpectedRelevantDocIds(Set<String> expectedRelevantDocIds) {
        this.expectedRelevantDocIds = expectedRelevantDocIds;
    }
}
