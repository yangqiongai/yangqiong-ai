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
package com.yangqiongai.ai.rag;

import com.yangqiongai.ai.rag.config.RagProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Collections;
import java.util.concurrent.Executor;

/**
 * RAG自动配置
 * @author yangqiong
 */
@Configuration
@EnableConfigurationProperties(RagProperties.class)
public class RagAutoConfiguration {

    /**
     * RAG专用线程池
     * @param ragProperties
     * @return
     */
    @Bean("ragExecutor")
    public Executor ragExecutor(RagProperties ragProperties) {
        RagProperties.Executor cfg = ragProperties.getExecutor();
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(cfg.getCorePoolSize());
        executor.setMaxPoolSize(cfg.getMaxPoolSize());
        executor.setQueueCapacity(cfg.getQueueCapacity());
        executor.setThreadNamePrefix("rag-worker-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    /**
     * RAG通用重试模板
     * @param ragProperties
     * @return
     */
    @Bean("ragRetryTemplate")
    public RetryTemplate ragRetryTemplate(RagProperties ragProperties) {
        RagProperties.Retry cfg = ragProperties.getRetry();
        RetryTemplate template = new RetryTemplate();

        RetryPolicy retryPolicy = new SimpleRetryPolicy(cfg.getMaxAttempts(),
                Collections.singletonMap(Exception.class, true));
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(cfg.getBackoffDelay().toMillis());
        backOffPolicy.setMultiplier(cfg.getBackoffMultiplier());
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }
}

