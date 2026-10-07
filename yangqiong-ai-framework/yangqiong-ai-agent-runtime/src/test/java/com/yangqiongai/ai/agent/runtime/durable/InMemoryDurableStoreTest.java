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
package com.yangqiongai.ai.agent.runtime.durable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;

/**
 * 内存分布式存储单元测试
 * @author yangqiong
 */
class InMemoryDurableStoreTest {

    private MemoryDistributedStores stores;

    @BeforeEach
    void setUp() {
        stores = new MemoryDistributedStores();
    }

    private AgentRunRecord newRun(String runId, String scopeId, String sessionId, long createdAt) {
        return new AgentRunRecord(runId, scopeId, sessionId, "u1", "agent", AgentRunState.CREATED, createdAt);
    }

    @Nested
    @DisplayName("运行存储")
    class RunStoreTest {

        @Test
        @DisplayName("创建后可按runId与会话查询")
        void create_thenQueryByRunIdAndSession() {
            AgentRunRecord record = stores.runStore().create(newRun("r1", "s1", "c1", 1000L));
            assertThat(record.getRunId()).isEqualTo("r1");
            assertThat(stores.runStore().findByRunId("r1")).isPresent();
            assertThat(stores.runStore().findLatestBySession("s1", "c1")).isPresent();
            assertThat(stores.runStore().findBySession("s1", "c1")).hasSize(1);
        }

        @Test
        @DisplayName("重复runId创建被拒绝")
        void create_duplicateRunId_shouldThrow() {
            stores.runStore().create(newRun("r1", "s1", "c1", 1000L));
            assertThatThrownBy(() -> stores.runStore().create(newRun("r1", "s1", "c1", 2000L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已存在");
        }

        @Test
        @DisplayName("runId为空创建被拒绝")
        void create_nullRunId_shouldThrow() {
            assertThatThrownBy(() -> stores.runStore().create(newRun(null, "s1", "c1", 1000L)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("容量超限淘汰创建时间最旧记录")
        void create_overflow_shouldEvictOldest() {
            for (int i = 0; i <= InMemoryAgentRunStore.MAX_RUNS; i++) {
                stores.runStore().create(newRun("r" + i, "s1", "c1", i));
            }
            assertThat(stores.runStore().findByRunId("r0")).isEmpty();
            assertThat(stores.runStore().findByRunId("r" + InMemoryAgentRunStore.MAX_RUNS)).isPresent();
            assertThat(stores.runStore().findBySession("s1", "c1"))
                    .hasSize(InMemoryAgentRunStore.MAX_RUNS);
        }

        @Test
        @DisplayName("saveTransition要求记录已存在")
        void saveTransition_missingRun_shouldThrow() {
            assertThatThrownBy(() -> stores.runStore().saveTransition(newRun("missing", "s1", "c1", 1L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不存在");
        }

        @Test
        @DisplayName("按作用域查询等待审批的运行")
        void findWaitingApproval_filterByScope() {
            AgentRunRecord waiting = stores.runStore().create(newRun("r1", "s1", "c1", 1000L));
            waiting.transitionTo(AgentRunState.RUNNING, "启动");
            waiting.transitionTo(AgentRunState.WAITING_APPROVAL, "需审批");
            stores.runStore().create(newRun("r2", "other", "c1", 1000L));
            List<AgentRunRecord> hits = stores.runStore().findWaitingApproval("s1");
            assertThat(hits).hasSize(1);
            assertThat(hits.get(0).getRunId()).isEqualTo("r1");
        }
    }

    @Nested
    @DisplayName("审批存储")
    class ApprovalStoreTest {

        @Test
        @DisplayName("审批落定后状态与意见生效")
        void resolve_approved_shouldUpdateState() {
            stores.approvalStore().create(ApprovalRecord.pending("a1", "r1", "t1", "http_call", "s1", "boss"));
            ApprovalRecord resolved = stores.approvalStore().resolve("a1", true, "同意");
            assertThat(resolved.getState()).isEqualTo(ApprovalRecord.ApprovalState.APPROVED);
            assertThat(resolved.getReason()).isEqualTo("同意");
            assertThat(stores.approvalStore().findByApprovalId("a1")).isPresent();
            assertThat(stores.approvalStore().findByToolCallId("t1")).isPresent();
        }

        @Test
        @DisplayName("已落定审批重复resolve保持原状")
        void resolve_alreadyResolved_shouldReturnOriginal() {
            stores.approvalStore().create(ApprovalRecord.pending("a1", "r1", "t1", "http_call", "s1", "boss"));
            stores.approvalStore().resolve("a1", true, "同意");
            ApprovalRecord again = stores.approvalStore().resolve("a1", false, "改口无效");
            assertThat(again.getState()).isEqualTo(ApprovalRecord.ApprovalState.APPROVED);
        }

        @Test
        @DisplayName("审批单不存在时resolve抛异常")
        void resolve_missing_shouldThrow() {
            assertThatThrownBy(() -> stores.approvalStore().resolve("missing", true, null))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不存在");
        }

        @Test
        @DisplayName("待审批按作用域过滤且落定后不再返回")
        void findPending_scopeFiltered() {
            stores.approvalStore().create(ApprovalRecord.pending("a1", "r1", "t1", "http_call", "s1", "boss"));
            stores.approvalStore().create(ApprovalRecord.pending("a2", "r2", "t2", "http_call", "other", "boss"));
            assertThat(stores.approvalStore().findPending("s1")).hasSize(1);
            stores.approvalStore().resolve("a1", false, "拒绝");
            assertThat(stores.approvalStore().findPending("s1")).isEmpty();
        }

        @Test
        @DisplayName("按runId查询审批记录且null入参返回空")
        void findByRunId_nullSafe() {
            stores.approvalStore().create(ApprovalRecord.pending("a1", "r1", "t1", "http_call", "s1", "boss"));
            assertThat(stores.approvalStore().findByRunId("r1")).hasSize(1);
            assertThat(stores.approvalStore().findByRunId(null)).isEmpty();
        }

        @Test
        @DisplayName("容量超限淘汰最旧记录且本次保存不被淘汰")
        void create_overflow_shouldEvictOldestExceptCurrent() {
            for (int i = 0; i <= InMemoryApprovalStore.MAX_RECORDS; i++) {
                stores.approvalStore().create(
                        new ApprovalRecord("a" + i, "r" + i, null, "tool", "s1", "boss",
                                ApprovalRecord.ApprovalState.PENDING, null, i));
            }
            assertThat(stores.approvalStore().findByApprovalId("a0")).isEmpty();
            assertThat(stores.approvalStore().findByApprovalId("a" + InMemoryApprovalStore.MAX_RECORDS)).isPresent();
        }
    }

    @Nested
    @DisplayName("检查点存储")
    class CheckpointStoreTest {

        private AgentCheckpoint checkpoint(String runId, long version, long timestamp) {
            return new AgentCheckpoint(runId, "s1", "c1", 1,
                    List.of(), List.of(), List.of(), timestamp, version);
        }

        @Test
        @DisplayName("保存后可按会话查最新且按runId查历史")
        void save_thenQueryLatestAndHistory() {
            stores.checkpointStore().save(checkpoint("r1", 1, 1000L));
            stores.checkpointStore().save(checkpoint("r1", 2, 2000L));
            Optional<AgentCheckpoint> latest = stores.checkpointStore().latest("s1", "c1");
            assertThat(latest).isPresent();
            assertThat(latest.get().getVersion()).isEqualTo(2);
            assertThat(stores.checkpointStore().findByRunId("r1")).hasSize(2);
        }

        @Test
        @DisplayName("版本回退保存被拒绝")
        void save_versionRegression_shouldThrow() {
            stores.checkpointStore().save(checkpoint("r1", 2, 1000L));
            assertThatThrownBy(() -> stores.checkpointStore().save(checkpoint("r1", 1, 2000L)))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("版本冲突");
        }

        @Test
        @DisplayName("runId为空保存被拒绝")
        void save_nullRunId_shouldThrow() {
            assertThatThrownBy(() -> stores.checkpointStore().save(checkpoint(null, 1, 1000L)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("清除指定runId后最新引用同步移除")
        void clear_shouldRemoveLatestReference() {
            stores.checkpointStore().save(checkpoint("r1", 1, 1000L));
            stores.checkpointStore().clear("r1");
            assertThat(stores.checkpointStore().latest("s1", "c1")).isEmpty();
            assertThat(stores.checkpointStore().findByRunId("r1")).isEmpty();
            stores.checkpointStore().clear(null);
        }

        @Test
        @DisplayName("会话键容量超限淘汰版本最旧检查点")
        void save_overflowSessions_shouldEvictLowestVersion() {
            for (int i = 0; i <= InMemoryCheckpointStore.MAX_SESSION_KEYS; i++) {
                stores.checkpointStore().save(new AgentCheckpoint("r" + i, "s" + i, "c1", 1,
                        List.of(), List.of(), List.of(), i, i));
            }
            assertThat(stores.checkpointStore().latest("s0", "c1")).isEmpty();
            assertThat(stores.checkpointStore().latest("s" + InMemoryCheckpointStore.MAX_SESSION_KEYS, "c1"))
                    .isPresent();
        }
    }

    @Nested
    @DisplayName("运行锁存储")
    class RunLockStoreTest {

        @Test
        @DisplayName("首次加锁成功且持有者正确")
        void tryLock_firstTime_shouldSucceed() {
            assertThat(stores.runLockStore().tryLock("r1", "node-1", Duration.ofSeconds(60))).isTrue();
            assertThat(stores.runLockStore().owner("r1")).contains("node-1");
        }

        @Test
        @DisplayName("他节点持锁未过期时加锁失败")
        void tryLock_heldByOther_shouldFail() {
            stores.runLockStore().tryLock("r1", "node-1", Duration.ofSeconds(60));
            assertThat(stores.runLockStore().tryLock("r1", "node-2", Duration.ofSeconds(60))).isFalse();
            assertThat(stores.runLockStore().owner("r1")).contains("node-1");
        }

        @Test
        @DisplayName("同节点重复加锁视为续期成功")
        void tryLock_sameOwner_shouldRenewAndSucceed() {
            stores.runLockStore().tryLock("r1", "node-1", Duration.ofSeconds(60));
            assertThat(stores.runLockStore().tryLock("r1", "node-1", Duration.ofSeconds(60))).isTrue();
        }

        @Test
        @DisplayName("锁过期后他节点可抢占")
        void tryLock_expired_shouldBeStolen() throws InterruptedException {
            stores.runLockStore().tryLock("r1", "node-1", Duration.ofMillis(50));
            Thread.sleep(80);
            assertThat(stores.runLockStore().owner("r1")).isEmpty();
            assertThat(stores.runLockStore().tryLock("r1", "node-2", Duration.ofSeconds(60))).isTrue();
        }

        @Test
        @DisplayName("仅持有者可解锁与续期")
        void unlockAndRenew_ownerOnly() {
            stores.runLockStore().tryLock("r1", "node-1", Duration.ofSeconds(60));
            stores.runLockStore().renew("r1", "node-2", Duration.ofSeconds(60));
            assertThat(stores.runLockStore().owner("r1")).contains("node-1");
            stores.runLockStore().unlock("r1", "node-2");
            assertThat(stores.runLockStore().owner("r1")).contains("node-1");
            stores.runLockStore().unlock("r1", "node-1");
            assertThat(stores.runLockStore().owner("r1")).isEmpty();
        }

        @Test
        @DisplayName("空入参加锁失败且owner空入参返回空")
        void nullArguments_shouldBeSafe() {
            assertThat(stores.runLockStore().tryLock(null, "node-1", Duration.ofSeconds(60))).isFalse();
            assertThat(stores.runLockStore().tryLock("r1", null, Duration.ofSeconds(60))).isFalse();
            assertThat(stores.runLockStore().tryLock("r1", "node-1", null)).isFalse();
            assertThat(stores.runLockStore().owner(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("工具执行存储")
    class ToolExecutionStoreTest {

        @Test
        @DisplayName("记录后判定完成并返回原结果")
        void record_thenCompletedWithResult() {
            AgentToolResultBlock result = AgentToolResultBlock.of("t1",
                    List.of(AgentTextBlock.builder().text("ok").build()));
            stores.toolExecutionStore().record("idem-1", result);
            assertThat(stores.toolExecutionStore().isCompleted("idem-1")).isTrue();
            assertThat(stores.toolExecutionStore().getResult("idem-1")).isSameAs(result);
        }

        @Test
        @DisplayName("重复记录保留首次结果")
        void record_duplicateKey_shouldKeepFirst() {
            AgentToolResultBlock first = AgentToolResultBlock.of("t1", List.of());
            AgentToolResultBlock second = AgentToolResultBlock.of("t1", List.of());
            stores.toolExecutionStore().record("idem-1", first);
            stores.toolExecutionStore().record("idem-1", second);
            assertThat(stores.toolExecutionStore().getResult("idem-1")).isSameAs(first);
        }

        @Test
        @DisplayName("null入参安全")
        void nullArguments_shouldBeSafe() {
            stores.toolExecutionStore().record(null, AgentToolResultBlock.of("t1", List.of()));
            stores.toolExecutionStore().record("idem-1", null);
            assertThat(stores.toolExecutionStore().isCompleted("idem-1")).isFalse();
            assertThat(stores.toolExecutionStore().isCompleted(null)).isFalse();
            assertThat(stores.toolExecutionStore().getResult(null)).isNull();
        }
    }

    @Nested
    @DisplayName("记忆存储")
    class MemoryStoreTest {

        @Test
        @DisplayName("会话摘要与情节保存加载清除")
        void sessionMemory_roundTrip() {
            stores.sessionMemory().saveSummary("c1", "摘要", 3);
            com.yangqiongai.ai.agent.runtime.memory.SessionSummary summary = stores.sessionMemory().loadSummary("c1");
            assertThat(summary.summary()).isEqualTo("摘要");
            assertThat(summary.summarizedMessageCount()).isEqualTo(3);
            stores.sessionMemory().saveEpisode("c1", com.yangqiongai.ai.agent.runtime.message.AgentMessage.builder().build());
            assertThat(stores.sessionMemory().listEpisodes("c1", 10)).hasSize(1);
            stores.sessionMemory().clear("c1");
            assertThat(stores.sessionMemory().loadSummary("c1")).isNull();
            assertThat(stores.sessionMemory().listEpisodes("c1", 10)).isEmpty();
        }

        @Test
        @DisplayName("长期记忆跨用户隔离与删除")
        void longTermMemory_isolatedByUser() {
            stores.longTermMemory().store("u1", "c1", "用户A喜欢简洁回复", null);
            stores.longTermMemory().store("u2", "c1", "用户B偏好表格输出", null);
            assertThat(stores.longTermMemory().search("u1", "简洁", 10)).containsExactly("用户A喜欢简洁回复");
            assertThat(stores.longTermMemory().search("u2", "表格", 10)).containsExactly("用户B偏好表格输出");
            assertThat(stores.longTermMemory().search("u1", "表格", 10)).isEmpty();
        }

        @Test
        @DisplayName("长期记忆空入参安全")
        void longTermMemory_nullSafe() {
            stores.longTermMemory().store("u1", "c1", null, null);
            assertThat(stores.longTermMemory().search("u1", null, 10)).isEmpty();
            assertThat(stores.longTermMemory().search("u1", "  ", 10)).isEmpty();
            stores.longTermMemory().delete(null);
        }
    }
}
