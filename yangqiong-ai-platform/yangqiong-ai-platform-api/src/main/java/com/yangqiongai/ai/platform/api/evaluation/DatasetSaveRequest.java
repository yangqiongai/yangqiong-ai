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
package com.yangqiongai.ai.platform.api.evaluation;

import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;

import java.util.List;

/**
 * 数据集保存请求
 * @author yangqiong
 */
public class DatasetSaveRequest {

    /**
     * 业务编码(创建后不可变)
     */
    private String datasetCode;

    /**
     * 数据集名称
     */
    private String name;

    /**
     * 数据集描述
     */
    private String description;

    /**
     * 状态(DRAFT/ENABLED/DISABLED)
     */
    private String status;

    /**
     * 用例列表(创建时批量导入，更新时非空=全量替换)
     */
    private List<EvalDatasetCaseEntity> cases;

    public String getDatasetCode() {
        return datasetCode;
    }

    public void setDatasetCode(String datasetCode) {
        this.datasetCode = datasetCode;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<EvalDatasetCaseEntity> getCases() {
        return cases;
    }

    public void setCases(List<EvalDatasetCaseEntity> cases) {
        this.cases = cases;
    }
}
