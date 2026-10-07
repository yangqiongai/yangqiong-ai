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
package com.yangqiongai.ai.evaluation;

import com.yangqiongai.ai.evaluation.dataset.DatasetLoader;
import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.engine.EvaluationEngine;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 评测服务管理
 * @author yangqiong
 */
public class DefaultEvaluationRunner implements EvaluationRunner {

    @Autowired
    private DatasetLoader datasetLoader;

    @Autowired
    private EvaluationEngine evaluationEngine;

    /**
     * 从文件加载并评测
     * @param filePath
     * @return
     */
    @Override
    public EvaluationReport evaluateFromFile(String filePath) {
        GoldenDataset dataset = datasetLoader.load(filePath);
        return evaluationEngine.evaluate(dataset);
    }

    /**
     * 从JSON加载并评测
     * @param json
     * @return
     */
    @Override
    public EvaluationReport evaluateFromJson(String json) {
        GoldenDataset dataset = datasetLoader.loadFromJson(json);
        return evaluationEngine.evaluate(dataset);
    }

    /**
     * 直接评测
     * @param dataset
     * @return
     */
    @Override
    public EvaluationReport evaluate(GoldenDataset dataset) {
        return evaluationEngine.evaluate(dataset);
    }
}
