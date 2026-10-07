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
package com.yangqiongai.ai.rag.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * RAG指标监控
 * @author yangqiong
 */
@Component
public class RagMetrics {

    private static final String METRIC_RETRIEVE_DURATION = "rag.retrieve.duration";
    private static final String METRIC_RETRIEVE_RESULTS = "rag.retrieve.results";
    private static final String METRIC_EMBED_DURATION = "rag.embed.duration";
    private static final String METRIC_EMBED_FAILED = "rag.embed.failed";
    private static final String METRIC_EMBED_QUEUE_SIZE = "rag.embed.queue.size";
    private static final String METRIC_CHUNK_DURATION = "rag.chunk.duration";
    private static final String METRIC_CHUNK_COUNT = "rag.chunk.count";
    private static final String METRIC_RERANK_DURATION = "rag.rerank.duration";
    private static final String METRIC_CACHE_HITS = "rag.cache.hits";
    private static final String METRIC_CACHE_MISSES = "rag.cache.misses";
    private static final String METRIC_CONTEXT_GRADE_DURATION = "rag.context.grade.duration";
    private static final String METRIC_CONTEXT_GRADE_SUFFICIENT = "rag.context.grade.sufficient";
    private static final String METRIC_RETRIEVAL_RETRY = "rag.retrieval.retry";

    @Autowired(required = false)
    private MeterRegistry meterRegistry;

    /**
     * 失败队列大小（由DefaultVectorEmbedder更新）
     */
    private final AtomicLong embedQueueSize = new AtomicLong(0);

    /**
     * 记录检索耗时
     * @param method
     * @param durationMillis
     */
    public void recordRetrieveDuration(String method, long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_RETRIEVE_DURATION)
                .tag("method", method)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * 记录检索结果数量
     * @param method
     * @param count
     */
    public void recordRetrieveResults(String method, int count) {
        if (meterRegistry == null) {
            return;
        }
        DistributionSummary.builder(METRIC_RETRIEVE_RESULTS)
                .tag("method", method)
                .register(meterRegistry)
                .record(count);
    }

    /**
     * 记录向量化耗时
     * @param durationMillis
     */
    public void recordEmbedDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_EMBED_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * 记录向量化失败
     */
    public void incrementEmbedFailed() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_EMBED_FAILED)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录切片耗时
     * @param durationMillis
     */
    public void recordChunkDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_CHUNK_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * 记录切片数量
     * @param count
     */
    public void recordChunkCount(int count) {
        if (meterRegistry == null) {
            return;
        }
        DistributionSummary.builder(METRIC_CHUNK_COUNT)
                .register(meterRegistry)
                .record(count);
    }

    /**
     * 记录Rerank耗时
     * @param durationMillis
     */
    public void recordRerankDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_RERANK_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * 更新失败队列大小
     * @param size
     */
    public void updateEmbedQueueSize(long size) {
        embedQueueSize.set(size);
        if (meterRegistry != null && meterRegistry.find(METRIC_EMBED_QUEUE_SIZE).gauge() == null) {
            Gauge.builder(METRIC_EMBED_QUEUE_SIZE, embedQueueSize, AtomicLong::doubleValue)
                    .description("失败向量化队列大小")
                    .register(meterRegistry);
        }
    }

    /**
     * 记录缓存命中
     * @param cacheName
     */
    public void incrementCacheHit(String cacheName) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_CACHE_HITS)
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录缓存未命中
     * @param cacheName
     */
    public void incrementCacheMiss(String cacheName) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_CACHE_MISSES)
                .tag("cache", cacheName)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录上下文评估耗时
     * @param durationMillis
     */
    public void recordContextGradeDuration(long durationMillis) {
        if (meterRegistry == null) {
            return;
        }
        Timer.builder(METRIC_CONTEXT_GRADE_DURATION)
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMillis));
    }

    /**
     * 记录上下文评估充分性
     * @param sufficient
     * @param confidence
     */
    public void recordContextGradeSufficient(boolean sufficient, double confidence) {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_CONTEXT_GRADE_SUFFICIENT)
                .tag("sufficient", String.valueOf(sufficient))
                .register(meterRegistry)
                .increment();
        DistributionSummary.builder(METRIC_CONTEXT_GRADE_SUFFICIENT + ".confidence")
                .tag("sufficient", String.valueOf(sufficient))
                .register(meterRegistry)
                .record(confidence);
    }

    /**
     * 记录重检索触发
     */
    public void incrementRetrievalRetry() {
        if (meterRegistry == null) {
            return;
        }
        Counter.builder(METRIC_RETRIEVAL_RETRY)
                .register(meterRegistry)
                .increment();
    }
}
