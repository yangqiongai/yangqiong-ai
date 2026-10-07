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
package com.yangqiongai.ai.agent.observability.metrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.durable.ApprovalRecord;
import com.yangqiongai.ai.agent.runtime.durable.ApprovalStore;

/**
 * 跨进程审批等待监控测试
 * @author yangqiong
 */
class ApprovalPendingMonitorTest {

    /**
     * 构造固定 createdAt 的审批记录
     * @param createdAt
     * @return
     */
    private ApprovalRecord record(long createdAt) {
        return new ApprovalRecord("ap-1", "run-1", "call-1", "shell", "default",
                "admin", ApprovalRecord.ApprovalState.PENDING, null, createdAt);
    }

    /**
     * 构造仅实现 findPending 的审批存储桩
     * @param findPending
     * @return
     */
    private ApprovalStore storeOf(Function<String, List<ApprovalRecord>> findPending) {
        return new ApprovalStore() {

            @Override
            public ApprovalRecord create(ApprovalRecord record) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Optional<ApprovalRecord> findByApprovalId(String approvalId) {
                return Optional.empty();
            }

            @Override
            public Optional<ApprovalRecord> findByToolCallId(String toolCallId) {
                return Optional.empty();
            }

            @Override
            public ApprovalRecord resolve(String approvalId, boolean approved, String reason) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<ApprovalRecord> findPending(String scopeId) {
                return findPending.apply(scopeId);
            }

            @Override
            public List<ApprovalRecord> findByRunId(String runId) {
                return List.of();
            }
        };
    }

    @Test
    @DisplayName("refresh按scope输出pending计数与等待秒数gauge")
    void refreshUpdatesGauges() {
        long now = System.currentTimeMillis();
        ApprovalStore store = storeOf(scope -> List.of(
                record(now - 10_000L), record(now - 4_000L)));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApprovalPendingMonitor monitor = new ApprovalPendingMonitor(store, registry,
                List.of("default"), 0L);

        monitor.refresh();

        assertThat(registry.get("agent.approval.pending.count")
                .tag("scope", "default").gauge().value()).isEqualTo(2.0);
        assertThat(registry.get("agent.approval.pending.wait.seconds.max")
                .tag("scope", "default").gauge().value()).isEqualTo(10.0);
        assertThat(registry.get("agent.approval.pending.wait.seconds.avg")
                .tag("scope", "default").gauge().value()).isEqualTo(7.0);
        monitor.close();
    }

    @Test
    @DisplayName("待审批为空时各gauge归零")
    void refreshWithEmptyPending() {
        ApprovalStore store = storeOf(scope -> List.of());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApprovalPendingMonitor monitor = new ApprovalPendingMonitor(store, registry,
                List.of("default"), 0L);

        monitor.refresh();

        assertThat(registry.get("agent.approval.pending.count").gauge().value()).isZero();
        assertThat(registry.get("agent.approval.pending.wait.seconds.max").gauge().value()).isZero();
        assertThat(registry.get("agent.approval.pending.wait.seconds.avg").gauge().value()).isZero();
        monitor.close();
    }

    @Test
    @DisplayName("存储查询异常被消化且gauge置零")
    void storeFailureSwallowed() {
        ApprovalStore store = storeOf(scope -> {
            throw new IllegalStateException("db down");
        });
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApprovalPendingMonitor monitor = new ApprovalPendingMonitor(store, registry,
                List.of("default"), 0L);

        monitor.refresh();

        assertThat(registry.get("agent.approval.pending.count").gauge().value()).isZero();
        monitor.close();
    }

    @Test
    @DisplayName("空scope列表不采集")
    void emptyScopesSkipsCollection() {
        AtomicReference<String> queried = new AtomicReference<>();
        ApprovalStore store = storeOf(scope -> {
            queried.set(scope);
            return List.of();
        });
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApprovalPendingMonitor monitor = new ApprovalPendingMonitor(store, registry,
                List.of(), 0L);

        monitor.refresh();

        assertThat(queried.get()).isNull();
        assertThat(monitor.monitoredScopes()).isEmpty();
        monitor.close();
    }

    @Test
    @DisplayName("定时模式按scope轮询刷新")
    void scheduledModeRefreshesPeriodically() throws Exception {
        long now = System.currentTimeMillis();
        List<ApprovalRecord> pending = new ArrayList<>();
        pending.add(record(now - 1_000L));
        ApprovalStore store = storeOf(scope -> List.copyOf(pending));
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ApprovalPendingMonitor monitor = new ApprovalPendingMonitor(store, registry,
                List.of("default"), 50L);

        Thread.sleep(200);
        assertThat(registry.get("agent.approval.pending.count").gauge().value()).isEqualTo(1.0);

        pending.clear();
        Thread.sleep(200);
        assertThat(registry.get("agent.approval.pending.count").gauge().value()).isZero();
        monitor.close();
    }

    @Test
    @DisplayName("close幂等")
    void closeIsIdempotent() {
        ApprovalStore store = storeOf(scope -> List.of());
        ApprovalPendingMonitor monitor = new ApprovalPendingMonitor(store,
                new SimpleMeterRegistry(), List.of("default"), 50L);
        monitor.close();
        monitor.close();
        assertThat(monitor.monitoredScopes()).containsExactly("default");
    }
}
