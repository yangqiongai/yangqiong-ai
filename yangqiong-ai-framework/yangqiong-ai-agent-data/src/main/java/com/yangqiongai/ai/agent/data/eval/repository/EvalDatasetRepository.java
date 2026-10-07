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
package com.yangqiongai.ai.agent.data.eval.repository;

import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;

import java.util.List;

/**
 * 评测数据集存储
 * @author yangqiong
 */
public interface EvalDatasetRepository {

    /**
     * 保存数据集
     * @param dataset
     * @return
     */
    EvalDatasetEntity saveDataset(EvalDatasetEntity dataset);

    /**
     * 更新数据集
     * @param dataset
     * @return
     */
    EvalDatasetEntity updateDataset(EvalDatasetEntity dataset);

    /**
     * 按ID查询数据集
     * @param id
     * @return
     */
    EvalDatasetEntity findDatasetById(Long id);

    /**
     * 按业务编码查询数据集
     * @param datasetCode
     * @return
     */
    EvalDatasetEntity findDatasetByCode(String datasetCode);

    /**
     * 分页查询数据集(按创建时间倒序)
     * @param keyword 名称/编码模糊搜索(可空)
     * @param status 状态(可空)
     * @param offset 偏移量
     * @param limit 条数
     * @return
     */
    List<EvalDatasetEntity> findDatasets(String keyword, String status, int offset, int limit);

    /**
     * 统计数据集条数
     * @param keyword 名称/编码模糊搜索(可空)
     * @param status 状态(可空)
     * @return
     */
    long countDatasets(String keyword, String status);

    /**
     * 删除数据集并级联删除用例
     * @param id
     */
    void deleteDataset(Long id);

    /**
     * 查询数据集全部用例(按用例编号排序)
     * @param datasetId
     * @return
     */
    List<EvalDatasetCaseEntity> findCases(Long datasetId);

    /**
     * 全量替换数据集用例并刷新冗余用例数
     * @param datasetId
     * @param cases
     * @return 替换后的用例数
     */
    int replaceCases(Long datasetId, List<EvalDatasetCaseEntity> cases);
}
