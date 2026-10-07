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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalRunEntity;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalRunCaseMapper;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalRunMapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评测运行存储
 * @author yangqiong
 */
public class DefaultEvalRunRepository implements EvalRunRepository {

    @Autowired
    private EvalRunMapper evalRunMapper;

    @Autowired
    private EvalRunCaseMapper evalRunCaseMapper;

    @Override
    public EvalRunEntity insertRun(EvalRunEntity run) {
        evalRunMapper.insert(run);
        return run;
    }

    @Override
    public void updateRun(EvalRunEntity run) {
        evalRunMapper.updateById(run);
    }

    @Override
    public EvalRunEntity findRunById(Long id) {
        return evalRunMapper.selectById(id);
    }

    @Override
    public long countRunsByDatasetId(Long datasetId) {
        LambdaQueryWrapper<EvalRunEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalRunEntity::getDatasetId, datasetId);
        return evalRunMapper.selectCount(wrapper);
    }

    @Override
    public List<EvalRunEntity> findRuns(String agentCode, String datasetCode, String status,
                                        LocalDateTime start, LocalDateTime end, int offset, int limit) {
        LambdaQueryWrapper<EvalRunEntity> wrapper = runWrapper(agentCode, datasetCode, status, start, end);
        wrapper.orderByDesc(EvalRunEntity::getCreateTime)
                .last("LIMIT " + Math.max(1, limit) + " OFFSET " + Math.max(0, offset));
        return evalRunMapper.selectList(wrapper);
    }

    @Override
    public long countRuns(String agentCode, String datasetCode, String status,
                          LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<EvalRunEntity> wrapper = runWrapper(agentCode, datasetCode, status, start, end);
        return evalRunMapper.selectCount(wrapper);
    }

    @Override
    public void insertRunCases(List<EvalRunCaseEntity> cases) {
        if (cases == null || cases.isEmpty()) {
            return;
        }
        for (EvalRunCaseEntity caseEntity : cases) {
            evalRunCaseMapper.insert(caseEntity);
        }
    }

    @Override
    public List<EvalRunCaseEntity> findRunCases(Long runId) {
        LambdaQueryWrapper<EvalRunCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalRunCaseEntity::getRunId, runId)
                .orderByAsc(EvalRunCaseEntity::getCaseNo);
        return evalRunCaseMapper.selectList(wrapper);
    }

    /**
     * 构建运行查询条件
     * @param agentCode
     * @param datasetCode
     * @param status
     * @param start
     * @param end
     * @return
     */
    private LambdaQueryWrapper<EvalRunEntity> runWrapper(String agentCode, String datasetCode, String status,
                                                         LocalDateTime start, LocalDateTime end) {
        LambdaQueryWrapper<EvalRunEntity> wrapper = new LambdaQueryWrapper<>();
        if (agentCode != null && !agentCode.isBlank()) {
            wrapper.eq(EvalRunEntity::getAgentCode, agentCode);
        }
        if (datasetCode != null && !datasetCode.isBlank()) {
            wrapper.eq(EvalRunEntity::getDatasetCode, datasetCode);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EvalRunEntity::getStatus, status);
        }
        if (start != null) {
            wrapper.ge(EvalRunEntity::getCreateTime, start);
        }
        if (end != null) {
            wrapper.le(EvalRunEntity::getCreateTime, end);
        }
        return wrapper;
    }
}
