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
package com.yangqiongai.ai.agent.observability.spring;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import com.yangqiongai.ai.agent.observability.metrics.ApprovalPendingMonitor;
import com.yangqiongai.ai.agent.observability.metrics.MetricsEventListener;
import com.yangqiongai.ai.agent.observability.trace.OtlpBatchBuffer;
import com.yangqiongai.ai.agent.observability.trace.OtlpHttpTraceEmitter;
import com.yangqiongai.ai.agent.runtime.durable.ApprovalStore;
import com.yangqiongai.ai.agent.runtime.trace.TraceEmitter;

/**
 * 可观测性自动装配
 * <p>
 * 按 ai.agent.observability.enabled=true 激活，产出三组Bean：
 * OTLP Trace导出器、指标监听器、跨进程审批等待监控。产出后由 HarnessAutoConfiguration
 * 自动收集装配到运行时（TraceEmitter组合广播/事件监听器链）。
 * MeterRegistry 优先复用容器已有实例（如Prometheus），缺失时内部创建SimpleMeterRegistry。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(prefix = "ai.agent.observability", name = "enabled", havingValue = "true")
public class ObservabilityAutoConfiguration {

    /**
     * OTLP Trace导出器Bean，随容器销毁触发最后一批导出；otlp-endpoint未配置时不创建
     * @param properties
     * @return
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean(TraceEmitter.class)
    @ConditionalOnProperty(prefix = "ai.agent.observability", name = "otlp-endpoint")
    public OtlpHttpTraceEmitter otlpHttpTraceEmitter(ObservabilityProperties properties) {
        OtlpBatchBuffer buffer = new OtlpBatchBuffer(properties.getOtlpEndpoint(), properties.getServiceName(),
                properties.getTraceBatchSize(), properties.getTraceFlushIntervalMs(),
                properties.getTraceExportTimeoutMs(), properties.getTraceMaxBufferedSpans());
        return new OtlpHttpTraceEmitter(buffer);
    }

    /**
     * 指标与审批监控装配：Micrometer存在时生效
     * @author yangqiong
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    static class MetricsConfiguration {

        /**
         * 指标监听器Bean：复用容器MeterRegistry或内部创建，由 HarnessAutoConfiguration 装配进事件链
         * @param properties
         * @param registryProvider
         * @return
         */
        @Bean
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "ai.agent.observability.metrics", name = "enabled",
                havingValue = "true")
        public MetricsEventListener metricsEventListener(ObservabilityProperties properties,
                                                        ObjectProvider<MeterRegistry> registryProvider) {
            MeterRegistry registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
            applyCommonTags(registry, properties.getMetrics().getCommonTags());
            return new MetricsEventListener(registry, null);
        }

        /**
         * 跨进程审批等待监控Bean：容器存在ApprovalStore时生效，随容器销毁停表
         * @param properties
         * @param approvalStoreProvider
         * @param registryProvider
         * @return
         */
        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean
        @ConditionalOnProperty(prefix = "ai.agent.observability.approval-wait", name = "enabled",
                havingValue = "true")
        public ApprovalPendingMonitor approvalPendingMonitor(ObservabilityProperties properties,
                                                             ObjectProvider<ApprovalStore> approvalStoreProvider,
                                                             ObjectProvider<MeterRegistry> registryProvider) {
            ApprovalStore approvalStore = approvalStoreProvider.getIfAvailable();
            if (approvalStore == null) {
                throw new IllegalStateException(
                        "ai.agent.observability.approval-wait.enabled=true 需要容器中存在 ApprovalStore Bean");
            }
            MeterRegistry registry = registryProvider.getIfAvailable(SimpleMeterRegistry::new);
            return new ApprovalPendingMonitor(approvalStore, registry,
                    properties.getApprovalWait().getScopes(),
                    properties.getApprovalWait().getRefreshIntervalMs());
        }

        /**
         * 追加公共标签到注册表
         * @param registry
         * @param commonTags
         */
        private void applyCommonTags(MeterRegistry registry, Map<String, String> commonTags) {
            if (commonTags == null || commonTags.isEmpty()) {
                return;
            }
            io.micrometer.core.instrument.Tags tags = io.micrometer.core.instrument.Tags.empty();
            for (Map.Entry<String, String> entry : commonTags.entrySet()) {
                tags = tags.and(entry.getKey(), entry.getValue());
            }
            registry.config().commonTags(tags);
        }
    }
}
