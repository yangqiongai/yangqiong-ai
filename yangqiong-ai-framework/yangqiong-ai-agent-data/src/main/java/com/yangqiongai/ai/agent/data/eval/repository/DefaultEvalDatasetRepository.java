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
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalDatasetCaseMapper;
import com.yangqiongai.ai.agent.data.eval.mapper.EvalDatasetMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 评测数据集存储
 * @author yangqiong
 */
public class DefaultEvalDatasetRepository implements EvalDatasetRepository {

    @Autowired
    private EvalDatasetMapper evalDatasetMapper;

    @Autowired
    private EvalDatasetCaseMapper evalDatasetCaseMapper;

    @Override
    public EvalDatasetEntity saveDataset(EvalDatasetEntity dataset) {
        evalDatasetMapper.insert(dataset);
        return dataset;
    }

    @Override
    public EvalDatasetEntity updateDataset(EvalDatasetEntity dataset) {
        evalDatasetMapper.updateById(dataset);
        return dataset;
    }

    @Override
    public EvalDatasetEntity findDatasetById(Long id) {
        return evalDatasetMapper.selectById(id);
    }

    @Override
    public EvalDatasetEntity findDatasetByCode(String datasetCode) {
        LambdaQueryWrapper<EvalDatasetEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDatasetEntity::getDatasetCode, datasetCode);
        return evalDatasetMapper.selectOne(wrapper);
    }

    @Override
    public List<EvalDatasetEntity> findDatasets(String keyword, String status, int offset, int limit) {
        LambdaQueryWrapper<EvalDatasetEntity> wrapper = datasetWrapper(keyword, status);
        wrapper.orderByDesc(EvalDatasetEntity::getCreateTime)
                .last("LIMIT " + Math.max(1, limit) + " OFFSET " + Math.max(0, offset));
        return evalDatasetMapper.selectList(wrapper);
    }

    @Override
    public long countDatasets(String keyword, String status) {
        LambdaQueryWrapper<EvalDatasetEntity> wrapper = datasetWrapper(keyword, status);
        return evalDatasetMapper.selectCount(wrapper);
    }

    @Override
    @Transactional
    public void deleteDataset(Long id) {
        LambdaQueryWrapper<EvalDatasetCaseEntity> caseWrapper = new LambdaQueryWrapper<>();
        caseWrapper.eq(EvalDatasetCaseEntity::getDatasetId, id);
        evalDatasetCaseMapper.delete(caseWrapper);
        evalDatasetMapper.deleteById(id);
    }

    @Override
    public List<EvalDatasetCaseEntity> findCases(Long datasetId) {
        LambdaQueryWrapper<EvalDatasetCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDatasetCaseEntity::getDatasetId, datasetId)
                .orderByAsc(EvalDatasetCaseEntity::getCaseNo);
        return evalDatasetCaseMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public int replaceCases(Long datasetId, List<EvalDatasetCaseEntity> cases) {
        LambdaQueryWrapper<EvalDatasetCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvalDatasetCaseEntity::getDatasetId, datasetId);
        evalDatasetCaseMapper.delete(wrapper);
        int count = 0;
        if (cases != null) {
            for (EvalDatasetCaseEntity caseEntity : cases) {
                // 回填归属数据集，避免dataset_id为空导致插入失败
                caseEntity.setDatasetId(datasetId);
                evalDatasetCaseMapper.insert(caseEntity);
                count++;
            }
        }
        EvalDatasetEntity dataset = evalDatasetMapper.selectById(datasetId);
        if (dataset != null) {
            dataset.setCaseCount(count);
            evalDatasetMapper.updateById(dataset);
        }
        return count;
    }

    /**
     * 构建数据集查询条件
     * @param keyword
     * @param status
     * @return
     */
    private LambdaQueryWrapper<EvalDatasetEntity> datasetWrapper(String keyword, String status) {
        LambdaQueryWrapper<EvalDatasetEntity> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like(EvalDatasetEntity::getName, keyword)
                    .or().like(EvalDatasetEntity::getDatasetCode, keyword));
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq(EvalDatasetEntity::getStatus, status);
        }
        return wrapper;
    }
}
