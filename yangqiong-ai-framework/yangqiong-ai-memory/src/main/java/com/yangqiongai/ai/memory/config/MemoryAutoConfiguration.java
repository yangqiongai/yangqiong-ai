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
package com.yangqiongai.ai.memory.config;

import com.yangqiongai.ai.memory.spi.MemoryEnhancer;
import com.yangqiongai.ai.memory.spi.NoopMemoryEnhancer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryPolicy;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.Collections;

/**
 * 记忆自动配置
 * @author yangqiong
 */
@Configuration
public class MemoryAutoConfiguration {

    /**
     * 社区缺省记忆增强实现，商业模块提供 MemoryEnhancer 时自动让位
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(MemoryEnhancer.class)
    public MemoryEnhancer noopMemoryEnhancer() {
        return new NoopMemoryEnhancer();
    }

    /**
     * 对话摘要生成专用重试模板，采用编程式RetryTemplate（与ai-rag约定一致，不使用@Retryable注解）
     * @param maxAttempts
     * @param initialInterval
     * @param multiplier
     * @return
     */
    @Bean("conversationRetryTemplate")
    public RetryTemplate conversationRetryTemplate(
            @Value("${ai.memory.summary.retry.max-attempts:${ai.conversation.summary.retry.max-attempts:3}}") int maxAttempts,
            @Value("${ai.memory.summary.retry.initial-interval:${ai.conversation.summary.retry.initial-interval:1000}}") long initialInterval,
            @Value("${ai.memory.summary.retry.multiplier:${ai.conversation.summary.retry.multiplier:2.0}}") double multiplier) {
        RetryTemplate template = new RetryTemplate();

        RetryPolicy retryPolicy = new SimpleRetryPolicy(maxAttempts,
                Collections.singletonMap(Exception.class, true));
        template.setRetryPolicy(retryPolicy);

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(initialInterval);
        backOffPolicy.setMultiplier(multiplier);
        template.setBackOffPolicy(backOffPolicy);

        return template;
    }
}
