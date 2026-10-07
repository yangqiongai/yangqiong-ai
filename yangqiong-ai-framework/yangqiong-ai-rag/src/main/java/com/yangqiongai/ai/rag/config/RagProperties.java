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

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * RAG配置属性
 * @author yangqiong
 */
@Validated
@ConfigurationProperties(prefix = "ai.rag")
public class RagProperties {

    /**
     * 检索配置
     */
    @Valid
    private Retrieve retrieve = new Retrieve();

    /**
     * 线程池配置
     */
    @Valid
    private Executor executor = new Executor();

    /**
     * Rerank配置
     */
    @Valid
    private Rerank rerank = new Rerank();

    /**
     * 查询改写配置
     */
    private QueryRewrite queryRewrite = new QueryRewrite();

    /**
     * 查询分解配置
     */
    private QueryDecompose queryDecompose = new QueryDecompose();

    /**
     * 路由配置
     */
    private Route route = new Route();

    /**
     * 缓存配置
     */
    private Cache cache = new Cache();

    /**
     * 重试配置
     */
    @Valid
    private Retry retry = new Retry();

    /**
     * 嵌入配置
     */
    private Embed embed = new Embed();

    /**
     * 上下文评估配置
     */
    @Valid
    private ContextGrade contextGrade = new ContextGrade();

    /**
     * 上下文窗口扩展配置
     */
    @Valid
    private ContextExpand contextExpand = new ContextExpand();

    /**
     * 全文检索配置
     */
    @Valid
    private Fulltext fulltext = new Fulltext();

    /**
     * 文档解析配置
     */
    private Parser parser = new Parser();

    /**
     * 检索代理配置
     */
    private RetrievalAgentConfig retrievalAgent = new RetrievalAgentConfig();

    public Retrieve getRetrieve() {
        return retrieve;
    }

    public void setRetrieve(Retrieve retrieve) {
        this.retrieve = retrieve;
    }

    public Executor getExecutor() {
        return executor;
    }

    public void setExecutor(Executor executor) {
        this.executor = executor;
    }

    public Rerank getRerank() {
        return rerank;
    }

    public void setRerank(Rerank rerank) {
        this.rerank = rerank;
    }

    public QueryRewrite getQueryRewrite() {
        return queryRewrite;
    }

    public void setQueryRewrite(QueryRewrite queryRewrite) {
        this.queryRewrite = queryRewrite;
    }

    public QueryDecompose getQueryDecompose() {
        return queryDecompose;
    }

    public void setQueryDecompose(QueryDecompose queryDecompose) {
        this.queryDecompose = queryDecompose;
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public Retry getRetry() {
        return retry;
    }

    public void setRetry(Retry retry) {
        this.retry = retry;
    }

    public Embed getEmbed() {
        return embed;
    }

    public void setEmbed(Embed embed) {
        this.embed = embed;
    }

    public ContextGrade getContextGrade() {
        return contextGrade;
    }

    public void setContextGrade(ContextGrade contextGrade) {
        this.contextGrade = contextGrade;
    }

    public ContextExpand getContextExpand() {
        return contextExpand;
    }

    public void setContextExpand(ContextExpand contextExpand) {
        this.contextExpand = contextExpand;
    }

    public Fulltext getFulltext() {
        return fulltext;
    }

    public void setFulltext(Fulltext fulltext) {
        this.fulltext = fulltext;
    }

    public Parser getParser() {
        return parser;
    }

    public void setParser(Parser parser) {
        this.parser = parser;
    }

    public RetrievalAgentConfig getRetrievalAgent() {
        return retrievalAgent;
    }

    public void setRetrievalAgent(RetrievalAgentConfig retrievalAgent) {
        this.retrievalAgent = retrievalAgent;
    }

    /**
     * 检索配置
     */
    public static class Retrieve {

        /**
         * 默认最小相似度分数
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double defaultMinScore = 0.6;

        /**
         * 单文档最大返回条数
         */
        private int maxPerDoc = 2;

        /**
         * 向量检索最小TopK（资源耗尽降级下限）
         */
        @Min(1)
        private int minTopK = 2;

        /**
         * 自适应TopK倍数
         */
        private int adaptiveTopKMultiplier = 3;

        /**
         * 并行检索超时时间（秒）
         */
        @Positive
        private int futureTimeoutSeconds = 30;

        /**
         * 融合评分权重配置
         */
        @Valid
        private Fusion fusion = new Fusion();

        public double getDefaultMinScore() {
            return defaultMinScore;
        }

        public void setDefaultMinScore(double defaultMinScore) {
            this.defaultMinScore = defaultMinScore;
        }

        public int getMaxPerDoc() {
            return maxPerDoc;
        }

        public void setMaxPerDoc(int maxPerDoc) {
            this.maxPerDoc = maxPerDoc;
        }

        public int getMinTopK() {
            return minTopK;
        }

        public void setMinTopK(int minTopK) {
            this.minTopK = minTopK;
        }

        public int getAdaptiveTopKMultiplier() {
            return adaptiveTopKMultiplier;
        }

        public void setAdaptiveTopKMultiplier(int adaptiveTopKMultiplier) {
            this.adaptiveTopKMultiplier = adaptiveTopKMultiplier;
        }

        public int getFutureTimeoutSeconds() {
            return futureTimeoutSeconds;
        }

        public void setFutureTimeoutSeconds(int futureTimeoutSeconds) {
            this.futureTimeoutSeconds = futureTimeoutSeconds;
        }

        public Fusion getFusion() {
            return fusion;
        }

        public void setFusion(Fusion fusion) {
            this.fusion = fusion;
        }

        /**
         * 融合评分权重
         */
        public static class Fusion {

            /**
             * 向量通道命中权重
             */
            private double vectorWeight = 40;

            /**
             * 全文通道命中权重
             */
            private double fulltextWeight = 24;

            /**
             * 双通道命中加成
             */
            private double dualLaneBonus = 16;

            /**
             * 查询词整串命中加成
             */
            private double queryHitBonus = 12;

            /**
             * 查询词分词命中加成
             */
            private double termHitBonus = 3;

            /**
             * 原始分线性放大系数（rawScore <= 1 时）
             */
            private double rawScoreFactor = 10;

            /**
             * 原始分封顶值
             */
            private double rawScoreCap = 20;

            public double getVectorWeight() {
                return vectorWeight;
            }

            public void setVectorWeight(double vectorWeight) {
                this.vectorWeight = vectorWeight;
            }

            public double getFulltextWeight() {
                return fulltextWeight;
            }

            public void setFulltextWeight(double fulltextWeight) {
                this.fulltextWeight = fulltextWeight;
            }

            public double getDualLaneBonus() {
                return dualLaneBonus;
            }

            public void setDualLaneBonus(double dualLaneBonus) {
                this.dualLaneBonus = dualLaneBonus;
            }

            public double getQueryHitBonus() {
                return queryHitBonus;
            }

            public void setQueryHitBonus(double queryHitBonus) {
                this.queryHitBonus = queryHitBonus;
            }

            public double getTermHitBonus() {
                return termHitBonus;
            }

            public void setTermHitBonus(double termHitBonus) {
                this.termHitBonus = termHitBonus;
            }

            public double getRawScoreFactor() {
                return rawScoreFactor;
            }

            public void setRawScoreFactor(double rawScoreFactor) {
                this.rawScoreFactor = rawScoreFactor;
            }

            public double getRawScoreCap() {
                return rawScoreCap;
            }

            public void setRawScoreCap(double rawScoreCap) {
                this.rawScoreCap = rawScoreCap;
            }
        }
    }

    /**
     * 线程池配置
     */
    public static class Executor {

        /**
         * 核心线程数
         */
        @Min(1)
        private int corePoolSize = 4;

        /**
         * 最大线程数
         */
        @Min(1)
        private int maxPoolSize = 8;

        /**
         * 队列容量
         */
        @Min(0)
        private int queueCapacity = 100;

        public int getCorePoolSize() {
            return corePoolSize;
        }

        public void setCorePoolSize(int corePoolSize) {
            this.corePoolSize = corePoolSize;
        }

        public int getMaxPoolSize() {
            return maxPoolSize;
        }

        public void setMaxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
        }

        public int getQueueCapacity() {
            return queueCapacity;
        }

        public void setQueueCapacity(int queueCapacity) {
            this.queueCapacity = queueCapacity;
        }
    }

    /**
     * Rerank配置
     */
    public static class Rerank {

        /**
         * 是否启用Rerank
         */
        private boolean enabled = false;

        /**
         * Rerank策略
         */
        private String strategy = "llm";

        /**
         * Rerank最大候选数量
         */
        private int maxCandidates = 20;

        /**
         * LLM模型编码
         */
        private String modelCode = "default";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public int getMaxCandidates() {
            return maxCandidates;
        }

        public void setMaxCandidates(int maxCandidates) {
            this.maxCandidates = maxCandidates;
        }

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }
    }

    /**
     * 查询改写配置
     */
    public static class QueryRewrite {

        /**
         * 是否启用查询改写
         */
        private boolean enabled = false;

        /**
         * LLM模型编码
         */
        private String modelCode = "default";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }
    }

    /**
     * 查询分解配置
     */
    public static class QueryDecompose {

        /**
         * 是否启用查询分解
         */
        private boolean enabled = false;

        /**
         * LLM模型编码
         */
        private String modelCode = "default";

        /**
         * 查询分解最小长度
         */
        @Min(1)
        private int minLength = 10;

        /**
         * 最大子问题数量
         */
        private int maxSubQueries = 3;

        /**
         * 每个子问题的topK
         */
        private int subQueryTopK = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }

        public int getMinLength() {
            return minLength;
        }

        public void setMinLength(int minLength) {
            this.minLength = minLength;
        }

        public int getMaxSubQueries() {
            return maxSubQueries;
        }

        public void setMaxSubQueries(int maxSubQueries) {
            this.maxSubQueries = maxSubQueries;
        }

        public int getSubQueryTopK() {
            return subQueryTopK;
        }

        public void setSubQueryTopK(int subQueryTopK) {
            this.subQueryTopK = subQueryTopK;
        }
    }

    /**
     * 路由配置
     */
    public static class Route {

        /**
         * 路由策略：default | agentic
         */
        private String strategy = "default";

        /**
         * Agentic模式下的LLM模型编码
         */
        private String modelCode = "default";

        /**
         * 是否启用Web搜索兜底
         */
        private boolean webSearchFallbackEnabled = false;

        /**
         * fallback知识源列表
         */
        private List<String> fallbackSources = List.of("search");

        /**
         * Web搜索最大结果数
         */
        @Min(1)
        private int webSearchMaxResults = 5;

        public String getStrategy() {
            return strategy;
        }

        public void setStrategy(String strategy) {
            this.strategy = strategy;
        }

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }

        public boolean isWebSearchFallbackEnabled() {
            return webSearchFallbackEnabled;
        }

        public void setWebSearchFallbackEnabled(boolean webSearchFallbackEnabled) {
            this.webSearchFallbackEnabled = webSearchFallbackEnabled;
        }

        public List<String> getFallbackSources() {
            return fallbackSources;
        }

        public void setFallbackSources(List<String> fallbackSources) {
            this.fallbackSources = fallbackSources;
        }

        public int getWebSearchMaxResults() {
            return webSearchMaxResults;
        }

        public void setWebSearchMaxResults(int webSearchMaxResults) {
            this.webSearchMaxResults = webSearchMaxResults;
        }
    }

    /**
     * 缓存配置
     */
    public static class Cache {

        /**
         * 是否启用缓存
         */
        private boolean enabled = true;

        /**
         * 检索结果缓存TTL
         */
        private Duration retrieveTtl = Duration.ofMinutes(5);

        /**
         * 检索结果缓存最大容量
         */
        private int retrieveMaxSize = 5000;

        /**
         * 活跃版本缓存TTL
         */
        private Duration versionTtl = Duration.ofMinutes(10);

        /**
         * 查询改写/意图识别缓存TTL
         */
        private Duration queryTtl = Duration.ofMinutes(30);

        /**
         * 查询改写/意图识别缓存最大容量
         */
        private int queryMaxSize = 1000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public Duration getRetrieveTtl() {
            return retrieveTtl;
        }

        public void setRetrieveTtl(Duration retrieveTtl) {
            this.retrieveTtl = retrieveTtl;
        }

        public int getRetrieveMaxSize() {
            return retrieveMaxSize;
        }

        public void setRetrieveMaxSize(int retrieveMaxSize) {
            this.retrieveMaxSize = retrieveMaxSize;
        }

        public Duration getVersionTtl() {
            return versionTtl;
        }

        public void setVersionTtl(Duration versionTtl) {
            this.versionTtl = versionTtl;
        }

        public Duration getQueryTtl() {
            return queryTtl;
        }

        public void setQueryTtl(Duration queryTtl) {
            this.queryTtl = queryTtl;
        }

        public int getQueryMaxSize() {
            return queryMaxSize;
        }

        public void setQueryMaxSize(int queryMaxSize) {
            this.queryMaxSize = queryMaxSize;
        }
    }

    /**
     * 重试配置
     */
    public static class Retry {

        /**
         * 最大重试次数
         */
        @Min(1)
        @Max(10)
        private int maxAttempts = 3;

        /**
         * 退避初始延迟
         */
        private Duration backoffDelay = Duration.ofMillis(1000);

        /**
         * 退避乘数
         */
        private double backoffMultiplier = 2.0;

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public Duration getBackoffDelay() {
            return backoffDelay;
        }

        public void setBackoffDelay(Duration backoffDelay) {
            this.backoffDelay = backoffDelay;
        }

        public double getBackoffMultiplier() {
            return backoffMultiplier;
        }

        public void setBackoffMultiplier(double backoffMultiplier) {
            this.backoffMultiplier = backoffMultiplier;
        }
    }

    /**
     * 嵌入配置
     */
    public static class Embed {

        /**
         * 默认向量维度
         */
        @Min(1)
        private int defaultVectorSize = 1024;

        /**
         * 最大重试次数
         */
        @Min(1)
        private int maxRetryAttempts = 3;

        /**
         * 重试基础延迟（毫秒）
         */
        @Min(100)
        private long retryBaseDelayMs = 1000L;

        /**
         * 批量嵌入大小
         */
        @Min(1)
        private int batchSize = 20;

        /**
         * 失败队列容量
         */
        private int failedQueueCapacity = 1000;

        /**
         * 重试间隔
         */
        private Duration retryInterval = Duration.ofMinutes(5);

        public int getDefaultVectorSize() {
            return defaultVectorSize;
        }

        public void setDefaultVectorSize(int defaultVectorSize) {
            this.defaultVectorSize = defaultVectorSize;
        }

        public int getMaxRetryAttempts() {
            return maxRetryAttempts;
        }

        public void setMaxRetryAttempts(int maxRetryAttempts) {
            this.maxRetryAttempts = maxRetryAttempts;
        }

        public long getRetryBaseDelayMs() {
            return retryBaseDelayMs;
        }

        public void setRetryBaseDelayMs(long retryBaseDelayMs) {
            this.retryBaseDelayMs = retryBaseDelayMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public int getFailedQueueCapacity() {
            return failedQueueCapacity;
        }

        public void setFailedQueueCapacity(int failedQueueCapacity) {
            this.failedQueueCapacity = failedQueueCapacity;
        }

        public Duration getRetryInterval() {
            return retryInterval;
        }

        public void setRetryInterval(Duration retryInterval) {
            this.retryInterval = retryInterval;
        }
    }

    /**
     * 上下文评估配置
     */
    public static class ContextGrade {

        /**
         * 是否启用上下文评估
         */
        private boolean enabled = false;

        /**
         * LLM模型编码
         */
        private String modelCode = "default";

        /**
         * 最大重检索次数
         */
        @Min(0)
        @Max(5)
        private int maxRetries = 2;

        /**
         * 置信度阈值
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double confidenceThreshold = 0.6;

        /**
         * 单次评估最大证据条数
         */
        @Min(1)
        private int maxEvidences = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getModelCode() {
            return modelCode;
        }

        public void setModelCode(String modelCode) {
            this.modelCode = modelCode;
        }

        public int getMaxRetries() {
            return maxRetries;
        }

        public void setMaxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
        }

        public double getConfidenceThreshold() {
            return confidenceThreshold;
        }

        public void setConfidenceThreshold(double confidenceThreshold) {
            this.confidenceThreshold = confidenceThreshold;
        }

        public int getMaxEvidences() {
            return maxEvidences;
        }

        public void setMaxEvidences(int maxEvidences) {
            this.maxEvidences = maxEvidences;
        }
    }

    /**
     * 检索代理配置
     */
    public static class RetrievalAgentConfig {

        /**
         * 是否启用反思-迭代代理
         */
        private boolean enabled = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * 上下文窗口扩展配置
     */
    public static class ContextExpand {

        /**
         * 关键词最小长度
         */
        @Min(1)
        private int minKeywordLen = 2;

        /**
         * 最大锚点聚类数量
         */
        @Min(1)
        private int maxClusterLimit = 5;

        /**
         * 锚点扩展半径占窗口比例
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double anchorRadiusRatio = 0.15;

        /**
         * 最小扩展半径
         */
        @Min(1)
        private int minRadius = 50;

        /**
         * 小文档切片数阈值
         */
        @Min(1)
        private int smallDocChunkCeiling = 30;

        /**
         * 默认邻居扩展距离
         */
        @Min(0)
        private int neighborSpan = 1;

        /**
         * 聚焦锚点最大数量
         */
        @Min(1)
        private int focusAnchorCeiling = 3;

        /**
         * 聚焦锚点邻居半径
         */
        @Min(0)
        private int focusNeighborSpan = 1;

        public int getMinKeywordLen() {
            return minKeywordLen;
        }

        public void setMinKeywordLen(int minKeywordLen) {
            this.minKeywordLen = minKeywordLen;
        }

        public int getMaxClusterLimit() {
            return maxClusterLimit;
        }

        public void setMaxClusterLimit(int maxClusterLimit) {
            this.maxClusterLimit = maxClusterLimit;
        }

        public double getAnchorRadiusRatio() {
            return anchorRadiusRatio;
        }

        public void setAnchorRadiusRatio(double anchorRadiusRatio) {
            this.anchorRadiusRatio = anchorRadiusRatio;
        }

        public int getMinRadius() {
            return minRadius;
        }

        public void setMinRadius(int minRadius) {
            this.minRadius = minRadius;
        }

        public int getSmallDocChunkCeiling() {
            return smallDocChunkCeiling;
        }

        public void setSmallDocChunkCeiling(int smallDocChunkCeiling) {
            this.smallDocChunkCeiling = smallDocChunkCeiling;
        }

        public int getNeighborSpan() {
            return neighborSpan;
        }

        public void setNeighborSpan(int neighborSpan) {
            this.neighborSpan = neighborSpan;
        }

        public int getFocusAnchorCeiling() {
            return focusAnchorCeiling;
        }

        public void setFocusAnchorCeiling(int focusAnchorCeiling) {
            this.focusAnchorCeiling = focusAnchorCeiling;
        }

        public int getFocusNeighborSpan() {
            return focusNeighborSpan;
        }

        public void setFocusNeighborSpan(int focusNeighborSpan) {
            this.focusNeighborSpan = focusNeighborSpan;
        }
    }

    /**
     * 全文检索配置
     */
    public static class Fulltext {

        /**
         * LIKE兜底结果数
         */
        @Min(1)
        private int fallbackResultSize = 5;

        /**
         * 超取因子
         */
        @Min(1)
        private int overfetchFactor = 6;

        /**
         * 最小超取数
         */
        @Min(1)
        private int minOverfetch = 30;

        /**
         * LIKE兜底限制
         */
        @Min(1)
        private int likeFallbackLimit = 50;

        /**
         * 基础相关性分数
         */
        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double baseRelevance = 0.3;

        /**
         * 精确匹配加成
         */
        @DecimalMin("0.0")
        private double exactMatchBonus = 0.35;

        /**
         * 分词命中加成
         */
        @DecimalMin("0.0")
        private double termHitBonus = 0.1;

        /**
         * 父文档标题加成
         */
        @DecimalMin("0.0")
        private double parentTitleBonus = 0.2;

        public int getFallbackResultSize() {
            return fallbackResultSize;
        }

        public void setFallbackResultSize(int fallbackResultSize) {
            this.fallbackResultSize = fallbackResultSize;
        }

        public int getOverfetchFactor() {
            return overfetchFactor;
        }

        public void setOverfetchFactor(int overfetchFactor) {
            this.overfetchFactor = overfetchFactor;
        }

        public int getMinOverfetch() {
            return minOverfetch;
        }

        public void setMinOverfetch(int minOverfetch) {
            this.minOverfetch = minOverfetch;
        }

        public int getLikeFallbackLimit() {
            return likeFallbackLimit;
        }

        public void setLikeFallbackLimit(int likeFallbackLimit) {
            this.likeFallbackLimit = likeFallbackLimit;
        }

        public double getBaseRelevance() {
            return baseRelevance;
        }

        public void setBaseRelevance(double baseRelevance) {
            this.baseRelevance = baseRelevance;
        }

        public double getExactMatchBonus() {
            return exactMatchBonus;
        }

        public void setExactMatchBonus(double exactMatchBonus) {
            this.exactMatchBonus = exactMatchBonus;
        }

        public double getTermHitBonus() {
            return termHitBonus;
        }

        public void setTermHitBonus(double termHitBonus) {
            this.termHitBonus = termHitBonus;
        }

        public double getParentTitleBonus() {
            return parentTitleBonus;
        }

        public void setParentTitleBonus(double parentTitleBonus) {
            this.parentTitleBonus = parentTitleBonus;
        }
    }

    /**
     * 文档解析配置
     */
    public static class Parser {

        /**
         * Tika最大内容长度（字节）
         */
        @Min(1)
        private int tikaMaxContentLength = 10 * 1024 * 1024;

        /**
         * Docling最大文件大小（字节）
         */
        @Min(1)
        private int doclingMaxFileSize = 100 * 1024 * 1024;

        /**
         * Docling分页解析每批页数（仅对PDF生效）
         */
        @Min(1)
        private int doclingPageSize = 10;

        /**
         * Docling分页解析最大页数（仅对PDF生效, 0表示不限制）
         */
        @Min(0)
        private int doclingMaxPages = 0;

        public int getTikaMaxContentLength() {
            return tikaMaxContentLength;
        }

        public void setTikaMaxContentLength(int tikaMaxContentLength) {
            this.tikaMaxContentLength = tikaMaxContentLength;
        }

        public int getDoclingMaxFileSize() {
            return doclingMaxFileSize;
        }

        public void setDoclingMaxFileSize(int doclingMaxFileSize) {
            this.doclingMaxFileSize = doclingMaxFileSize;
        }

        public int getDoclingPageSize() {
            return doclingPageSize;
        }

        public void setDoclingPageSize(int doclingPageSize) {
            this.doclingPageSize = doclingPageSize;
        }

        public int getDoclingMaxPages() {
            return doclingMaxPages;
        }

        public void setDoclingMaxPages(int doclingMaxPages) {
            this.doclingMaxPages = doclingMaxPages;
        }
    }
}
