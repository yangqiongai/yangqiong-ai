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

import com.yangqiongai.ai.agent.data.eval.loader.DbDatasetLoader;
import com.yangqiongai.ai.agent.data.eval.loader.PrefixDatasetLoader;
import com.yangqiongai.ai.agent.data.eval.repository.EvalDatasetRepository;
import com.yangqiongai.ai.evaluation.dataset.DatasetLoader;
import com.yangqiongai.ai.evaluation.dataset.DatasetLoaderImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Agent评测面装配
 * @author yangqiong
 */
@Configuration
@ConditionalOnProperty(prefix = "ai.agent.evaluation", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AgentEvalProperties.class)
public class AgentEvaluationConfiguration {

    /**
     * 注册前缀路由数据集加载器(db:数据库/classpath:与文件路径回退)
     * <p>
     * @Primary保证门禁与运行服务按类型注入时优先使用本加载器，
     * 评测框架默认加载器仍按@ConditionalOnMissingBean共存。
     * </p>
     * @param evalDatasetRepository 评测数据集存储
     * @return
     */
    @Bean
    @Primary
    public DatasetLoader prefixDatasetLoader(EvalDatasetRepository evalDatasetRepository) {
        return new PrefixDatasetLoader(new DbDatasetLoader(evalDatasetRepository), new DatasetLoaderImpl());
    }
}
