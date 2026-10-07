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
package com.yangqiongai.ai.workflow.store;

import com.yangqiongai.ai.workflow.model.WorkflowPauseHistory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JdbcWorkflowPauseHistoryStore 单元测试（H2内存库，MySQL兼容模式）
 *
 * @author yangqiong
 */
class JdbcWorkflowPauseHistoryStoreTest {

    private JdbcWorkflowPauseHistoryStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:h2:mem:history_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE ai_workflow_pause_history (
                    id VARCHAR(64) NOT NULL,
                    scope_id VARCHAR(64),
                    instance_id VARCHAR(64) NOT NULL,
                    definition_name VARCHAR(128),
                    paused_node_id VARCHAR(64),
                    action VARCHAR(16),
                    reason VARCHAR(500),
                    operator VARCHAR(64),
                    operator_time BIGINT,
                    pending_request_id VARCHAR(64),
                    wait_duration_ms BIGINT,
                    create_time BIGINT,
                    PRIMARY KEY (id),
                    KEY idx_instance_id (instance_id),
                    KEY idx_operator_time (operator_time)
                )
                """);
        store = new JdbcWorkflowPauseHistoryStore(jdbcTemplate);
    }

    /**
     * 构建流水记录
     * @param instanceId
     * @param action
     * @param operatorTime
     * @return
     */
    private WorkflowPauseHistory buildHistory(String instanceId, String action, long operatorTime) {
        WorkflowPauseHistory history = new WorkflowPauseHistory();
        history.setInstanceId(instanceId);
        history.setScopeId("scope-1");
        history.setDefinitionName("test-workflow");
        history.setPausedNodeId("node-1");
        history.setAction(action);
        history.setReason("测试原因");
        history.setOperator("zhangsan");
        history.setOperatorTime(operatorTime);
        history.setPendingRequestId("pr-1");
        history.setWaitDurationMs(123L);
        history.setCreateTime(operatorTime);
        return history;
    }

    // ==================== 记录与查询 ====================

    @Nested
    @DisplayName("记录与查询测试")
    class RecordQueryTests {

        @Test
        @DisplayName("记录后按实例查询返回完整字段")
        void recordThenList() {
            store.record(buildHistory("inst-1", WorkflowPauseHistory.ACTION_PAUSE, 1000L));

            List<WorkflowPauseHistory> list = store.listByInstanceId("inst-1");
            assertThat(list).hasSize(1);
            WorkflowPauseHistory history = list.get(0);
            assertThat(history.getId()).isNotBlank();
            assertThat(history.getScopeId()).isEqualTo("scope-1");
            assertThat(history.getDefinitionName()).isEqualTo("test-workflow");
            assertThat(history.getPausedNodeId()).isEqualTo("node-1");
            assertThat(history.getAction()).isEqualTo(WorkflowPauseHistory.ACTION_PAUSE);
            assertThat(history.getReason()).isEqualTo("测试原因");
            assertThat(history.getOperator()).isEqualTo("zhangsan");
            assertThat(history.getOperatorTime()).isEqualTo(1000L);
            assertThat(history.getPendingRequestId()).isEqualTo("pr-1");
            assertThat(history.getWaitDurationMs()).isEqualTo(123L);
        }

        @Test
        @DisplayName("多次记录按操作时间正序返回")
        void multipleRecordsSortedByTime() {
            store.record(buildHistory("inst-1", WorkflowPauseHistory.ACTION_PAUSE, 3000L));
            store.record(buildHistory("inst-1", WorkflowPauseHistory.ACTION_RESUME, 4000L));
            store.record(buildHistory("inst-1", WorkflowPauseHistory.ACTION_PAUSE, 5000L));

            List<WorkflowPauseHistory> list = store.listByInstanceId("inst-1");
            assertThat(list).hasSize(3);
            assertThat(list.get(0).getAction()).isEqualTo(WorkflowPauseHistory.ACTION_PAUSE);
            assertThat(list.get(1).getAction()).isEqualTo(WorkflowPauseHistory.ACTION_RESUME);
            assertThat(list.get(2).getAction()).isEqualTo(WorkflowPauseHistory.ACTION_PAUSE);
            assertThat(list.get(2).getOperatorTime()).isEqualTo(5000L);
        }

        @Test
        @DisplayName("不同实例的流水互不干扰")
        void instancesAreIsolated() {
            store.record(buildHistory("inst-1", WorkflowPauseHistory.ACTION_PAUSE, 1000L));
            store.record(buildHistory("inst-2", WorkflowPauseHistory.ACTION_PAUSE, 2000L));

            assertThat(store.listByInstanceId("inst-1")).hasSize(1);
            assertThat(store.listByInstanceId("inst-2")).hasSize(1);
            assertThat(store.listByInstanceId("inst-3")).isEmpty();
        }

        @Test
        @DisplayName("记录null或无实例ID的流水被忽略")
        void recordInvalidIgnored() {
            store.record(null);
            WorkflowPauseHistory history = new WorkflowPauseHistory();
            store.record(history);
            assertThat(store.listByInstanceId("inst-1")).isEmpty();
        }

        @Test
        @DisplayName("查询入参为null返回空列表")
        void listNullReturnsEmpty() {
            assertThat(store.listByInstanceId(null)).isEmpty();
        }
    }
}
