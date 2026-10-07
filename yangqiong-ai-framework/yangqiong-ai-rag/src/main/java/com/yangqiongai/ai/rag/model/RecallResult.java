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

/**
 * 召回测试结果
 * @author yangqiong
 */
public class RecallResult {

    /**
     * 查询语句
     */
    private String query;

    /**
     * 命中数量
     */
    private int hitCount;

    /**
     * 命中切片列表
     */
    private List<ChunkCandidate> hitSlices;

    /**
     * 召回率
     */
    private double recallRate;

    /**
     * 覆盖度
     */
    private double coverageRate;

    /**
     * MRR（平均倒数排名，第一个相关结果的排名倒数）
     */
    private double mrr;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public int getHitCount() {
        return hitCount;
    }

    public void setHitCount(int hitCount) {
        this.hitCount = hitCount;
    }

    public List<ChunkCandidate> getHitSlices() {
        return hitSlices;
    }

    public void setHitSlices(List<ChunkCandidate> hitSlices) {
        this.hitSlices = hitSlices;
    }

    public double getRecallRate() {
        return recallRate;
    }

    public void setRecallRate(double recallRate) {
        this.recallRate = recallRate;
    }

    public double getCoverageRate() {
        return coverageRate;
    }

    public void setCoverageRate(double coverageRate) {
        this.coverageRate = coverageRate;
    }

    public double getMrr() {
        return mrr;
    }

    public void setMrr(double mrr) {
        this.mrr = mrr;
    }
}
