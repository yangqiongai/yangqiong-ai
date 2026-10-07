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
package com.yangqiongai.ai.agent.data.eval.loader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetCaseEntity;
import com.yangqiongai.ai.agent.data.eval.entity.EvalDatasetEntity;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.evaluation.dataset.DatasetLoader;
import com.yangqiongai.ai.evaluation.dataset.GoldenCase;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数据库评测数据集加载
 * @author yangqiong
 */
public class DbDatasetLoader implements DatasetLoader {

    private final EvalDatasetRepository evalDatasetRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 按数据集业务编码从数据库加载数据集
     * @param evalDatasetRepository 评测数据集存储
     */
    public DbDatasetLoader(EvalDatasetRepository evalDatasetRepository) {
        this.evalDatasetRepository = evalDatasetRepository;
    }

    /**
     * 从数据库加载数据集(location=数据集业务编码)
     * @param location
     * @return
     */
    @Override
    public GoldenDataset load(String location) {
        EvalDatasetEntity dataset = evalDatasetRepository.findDatasetByCode(location);
        if (dataset == null) {
            throw new RuntimeException("Dataset not found: " + location);
        }
        List<EvalDatasetCaseEntity> caseEntities = evalDatasetRepository.findCases(dataset.getId());
        List<GoldenCase> cases = new ArrayList<>();
        for (EvalDatasetCaseEntity caseEntity : caseEntities) {
            cases.add(toGoldenCase(caseEntity));
        }
        return new GoldenDataset(dataset.getDatasetCode(), dataset.getName(), dataset.getDescription(), cases);
    }

    /**
     * 从JSON字符串加载数据集(与文件加载一致)
     * @param json
     * @return
     */
    @Override
    public GoldenDataset loadFromJson(String json) {
        try {
            return objectMapper.readValue(json, GoldenDataset.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse dataset from JSON", e);
        }
    }

    /**
     * 数据集用例转Golden用例
     * @param caseEntity
     * @return
     */
    private GoldenCase toGoldenCase(EvalDatasetCaseEntity caseEntity) {
        Map<String, Object> body = null;
        if (caseEntity.getBodyJson() != null && !caseEntity.getBodyJson().isBlank()) {
            try {
                body = objectMapper.readValue(caseEntity.getBodyJson(),
                        objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            } catch (Exception e) {
                throw new RuntimeException("Failed to parse case body JSON, caseNo=" + caseEntity.getCaseNo(), e);
            }
        }
        return new GoldenCase(caseEntity.getCaseNo(), caseEntity.getQueryText(),
                caseEntity.getExpectedOutput(), caseEntity.getScoringCriteria(), body);
    }
}
