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
package com.yangqiongai.ai.rag.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG缓存配置
 * @author yangqiong
 */
@Configuration
@ConditionalOnProperty(name = "ai.rag.cache.enabled", havingValue = "true", matchIfMissing = true)
public class RagCacheConfiguration {

    /**
     * 缓存名称：检索结果
     */
    public static final String CACHE_RETRIEVE = "rag-retrieve";

    /**
     * 缓存名称：活跃版本
     */
    public static final String CACHE_ACTIVE_VERSION = "rag-active-version";

    /**
     * 缓存名称：查询改写
     */
    public static final String CACHE_QUERY_REWRITE = "rag-query-rewrite";

    /**
     * 缓存名称：意图识别
     */
    public static final String CACHE_INTENTION = "rag-intention";

    /**
     * 缓存名称：上下文评估
     */
    public static final String CACHE_CONTEXT_GRADE = "rag-context-grade";

    /**
     * 缓存管理器（按缓存名配置不同TTL和容量，开启统计以支持缓存指标）
     * <p>缓存指标由Spring Boot的CacheMetricsRegistrar自动绑定，此处不再手动注册以避免meter标签冲突</p>
     * @param ragProperties
     * @return
     */
    @Bean
    public CacheManager ragCacheManager(RagProperties ragProperties) {
        RagProperties.Cache cfg = ragProperties.getCache();
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setAllowNullValues(false);

        // 检索结果缓存：TTL较短，容量大
        registerCache(cacheManager, CACHE_RETRIEVE, Caffeine.newBuilder()
                .expireAfterWrite(cfg.getRetrieveTtl())
                .maximumSize(cfg.getRetrieveMaxSize())
                .recordStats()
                .build());

        // 活跃版本缓存：TTL较长，变更不频繁
        registerCache(cacheManager, CACHE_ACTIVE_VERSION, Caffeine.newBuilder()
                .expireAfterWrite(cfg.getVersionTtl())
                .maximumSize(500)
                .recordStats()
                .build());

        // 查询改写缓存：TTL较长，相同查询改写结果稳定
        registerCache(cacheManager, CACHE_QUERY_REWRITE, Caffeine.newBuilder()
                .expireAfterWrite(cfg.getQueryTtl())
                .maximumSize(cfg.getQueryMaxSize())
                .recordStats()
                .build());

        // 意图识别缓存：TTL较长，相同查询意图稳定
        registerCache(cacheManager, CACHE_INTENTION, Caffeine.newBuilder()
                .expireAfterWrite(cfg.getQueryTtl())
                .maximumSize(cfg.getQueryMaxSize())
                .recordStats()
                .build());

        // 上下文评估缓存：TTL较长，相同query+evidences评估结果稳定
        registerCache(cacheManager, CACHE_CONTEXT_GRADE, Caffeine.newBuilder()
                .expireAfterWrite(cfg.getQueryTtl())
                .maximumSize(cfg.getQueryMaxSize())
                .recordStats()
                .build());

        return cacheManager;
    }

    /**
     * 注册Caffeine缓存到管理器
     * @param cacheManager
     * @param cacheName
     * @param cache
     */
    private void registerCache(CaffeineCacheManager cacheManager, String cacheName,
                                Cache<Object, Object> cache) {
        cacheManager.registerCustomCache(cacheName, cache);
    }
}
