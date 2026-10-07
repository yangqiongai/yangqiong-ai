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
import com.yangqiongai.ai.evaluation.dataset.DatasetLoaderImpl;
import com.yangqiongai.ai.evaluation.engine.DefaultEvaluationEngine;
import com.yangqiongai.ai.evaluation.engine.EvaluationEngine;
import com.yangqiongai.ai.evaluation.scoring.ScoringStrategy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * 评测框架自动配置
 * @author yangqiong
 */
@AutoConfiguration
public class EvaluationAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(DatasetLoader.class)
    public DatasetLoader datasetLoader() {
        return new DatasetLoaderImpl();
    }

    @Bean
    @ConditionalOnMissingBean(EvaluationRunner.class)
    public EvaluationRunner evaluationRunner() {
        return new DefaultEvaluationRunner();
    }

    @Bean
    @ConditionalOnMissingBean(EvaluationEngine.class)
    public EvaluationEngine evaluationEngine(List<ScoringStrategy> scoringStrategies) {
        return new DefaultEvaluationEngine(scoringStrategies);
    }

}
