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

import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测运行存储
 * @author yangqiong
 */
public interface EvalRunRepository {

    /**
     * 保存评测运行(RUNNING初始态)
     * @param run
     * @return
     */
    EvalRunEntity insertRun(EvalRunEntity run);

    /**
     * 更新评测运行(完成态统计与报告快照)
     * @param run
     */
    void updateRun(EvalRunEntity run);

    /**
     * 按ID查询评测运行
     * @param id
     * @return
     */
    EvalRunEntity findRunById(Long id);

    /**
     * 统计数据集关联的运行条数(删除数据集防护)
     * @param datasetId
     * @return
     */
    long countRunsByDatasetId(Long datasetId);

    /**
     * 分页查询评测运行(按创建时间倒序)
     * @param agentCode Agent编码(可空)
     * @param datasetCode 数据集编码(可空)
     * @param status 状态(可空)
     * @param start 创建时间下界(可空)
     * @param end 创建时间上界(可空)
     * @param offset 偏移量
     * @param limit 条数
     * @return
     */
    List<EvalRunEntity> findRuns(String agentCode, String datasetCode, String status,
                                 LocalDateTime start, LocalDateTime end, int offset, int limit);

    /**
     * 统计评测运行条数
     * @param agentCode Agent编码(可空)
     * @param datasetCode 数据集编码(可空)
     * @param status 状态(可空)
     * @param start 创建时间下界(可空)
     * @param end 创建时间上界(可空)
     * @return
     */
    long countRuns(String agentCode, String datasetCode, String status,
                   LocalDateTime start, LocalDateTime end);

    /**
     * 批量保存运行用例明细
     * @param cases
     */
    void insertRunCases(List<EvalRunCaseEntity> cases);

    /**
     * 查询运行的用例明细(按用例编号排序)
     * @param runId
     * @return
     */
    List<EvalRunCaseEntity> findRunCases(Long runId);
}
