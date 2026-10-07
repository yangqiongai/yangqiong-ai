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

import com.yangqiongai.ai.evaluation.dataset.GoldenDataset;
import com.yangqiongai.ai.evaluation.report.EvaluationReport;

/**
 * 评测服务
 * @author yangqiong
 */
public interface EvaluationRunner {

    /**
     * 从文件加载并评测
     * @param filePath
     * @return
     */
    EvaluationReport evaluateFromFile(String filePath);

    /**
     * 从JSON加载并评测
     * @param json
     * @return
     */
    EvaluationReport evaluateFromJson(String json);

    /**
     * 直接评测
     * @param dataset
     * @return
     */
    EvaluationReport evaluate(GoldenDataset dataset);
}
