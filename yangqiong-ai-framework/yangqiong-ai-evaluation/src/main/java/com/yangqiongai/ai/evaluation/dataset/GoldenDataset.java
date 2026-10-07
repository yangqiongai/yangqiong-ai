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
package com.yangqiongai.ai.evaluation.dataset;

import java.util.List;

/**
 * 评测数据集
 * @author yangqiong
 */
public class GoldenDataset {

    /**
     * 数据集ID
     */
    private String datasetId;

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 数据集描述
     */
    private String description;

    /**
     * 评测用例列表
     */
    private List<GoldenCase> cases;

    public GoldenDataset() {
    }

    public GoldenDataset(String datasetId, String name, String description, List<GoldenCase> cases) {
        this.datasetId = datasetId;
        this.name = name;
        this.description = description;
        this.cases = cases;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(String datasetId) {
        this.datasetId = datasetId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<GoldenCase> getCases() {
        return cases;
    }

    public void setCases(List<GoldenCase> cases) {
        this.cases = cases;
    }
}
