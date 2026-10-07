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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.workflow.model.ExecutionStatus;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JdbcWorkflowStateStore 单元测试（H2内存库，MySQL兼容模式）
 *
 * @author yangqiong
 */
class JdbcWorkflowStateStoreTest {

    private JdbcTemplate jdbcTemplate;

    private JdbcWorkflowStateStore store;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setUrl("jdbc:h2:mem:state_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        dataSource.setDriverClassName("org.h2.Driver");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE ai_workflow_state (
                    instance_id VARCHAR(64) NOT NULL,
                    definition_name VARCHAR(128),
                    status VARCHAR(32),
                    paused_node_id VARCHAR(64),
                    pending_request_id VARCHAR(64),
                    paused_reason VARCHAR(500),
                    paused_by VARCHAR(64),
                    paused_time BIGINT,
                    state_json MEDIUMTEXT,
                    create_time BIGINT,
                    update_time BIGINT,
                    PRIMARY KEY (instance_id),
                    KEY idx_status (status),
                    KEY idx_definition_name (definition_name),
                    KEY idx_pending_request_id (pending_request_id)
                )
                """);
        store = new JdbcWorkflowStateStore(jdbcTemplate, new ObjectMapper());
    }

    /**
     * 构建测试状态
     * @param instanceId
     * @param status
     * @return
     */
    private WorkflowState buildState(String instanceId, ExecutionStatus status) {
        WorkflowState state = new WorkflowState();
        state.setInstanceId(instanceId);
        state.setDefinitionName("test-workflow");
        state.setStatus(status);
        state.setCreateTime(1000L);
        state.setUpdateTime(2000L);
        state.setVariable("key1", "value1");
        return state;
    }

    // ==================== 保存与加载 ====================

    @Nested
    @DisplayName("保存与加载测试")
    class SaveLoadTests {

        @Test
        @DisplayName("保存后加载返回相同状态")
        void saveThenLoad() {
            WorkflowState state = buildState("inst-1", ExecutionStatus.RUNNING);
            store.save(state);

            WorkflowState loaded = store.load("inst-1");
            assertThat(loaded).isNotNull();
            assertThat(loaded.getInstanceId()).isEqualTo("inst-1");
            assertThat(loaded.getDefinitionName()).isEqualTo("test-workflow");
            assertThat(loaded.getStatus()).isEqualTo(ExecutionStatus.RUNNING);
            assertThat(loaded.getVariable("key1")).isEqualTo("value1");
        }

        @Test
        @DisplayName("重复保存覆盖更新（upsert语义）")
        void saveTwiceUpdates() {
            WorkflowState state = buildState("inst-1", ExecutionStatus.RUNNING);
            store.save(state);
            state.setStatus(ExecutionStatus.COMPLETED);
            state.setVariable("key1", "changed");
            store.save(state);

            WorkflowState loaded = store.load("inst-1");
            assertThat(loaded.getStatus()).isEqualTo(ExecutionStatus.COMPLETED);
            assertThat(loaded.getVariable("key1")).isEqualTo("changed");
        }

        @Test
        @DisplayName("加载不存在的实例返回null")
        void loadMissingReturnsNull() {
            assertThat(store.load("no-such")).isNull();
        }

        @Test
        @DisplayName("保存null或无实例ID的状态不抛异常")
        void saveInvalidIgnored() {
            store.save(null);
            store.save(new WorkflowState());
            assertThat(store.queryByDefinitionName("test-workflow")).isEmpty();
        }
    }

    // ==================== 暂停元数据 ====================

    @Nested
    @DisplayName("暂停元数据测试")
    class PauseMetadataTests {

        @Test
        @DisplayName("暂停元数据随快照持久化并可恢复")
        void pauseMetadataRoundTrip() {
            WorkflowState state = buildState("inst-paused", ExecutionStatus.PAUSED);
            state.setPausedNodeId("node-2");
            state.setPendingRequestId("pr-1");
            state.setPausedReason("等待审批");
            state.setPausedBy("zhangsan");
            state.setPausedTime(5555L);
            store.save(state);

            WorkflowState loaded = store.load("inst-paused");
            assertThat(loaded.getPausedNodeId()).isEqualTo("node-2");
            assertThat(loaded.getPendingRequestId()).isEqualTo("pr-1");
            assertThat(loaded.getPausedReason()).isEqualTo("等待审批");
            assertThat(loaded.getPausedBy()).isEqualTo("zhangsan");
            assertThat(loaded.getPausedTime()).isEqualTo(5555L);
        }

        @Test
        @DisplayName("按审批请求ID查找暂停实例")
        void findByPendingRequestId() {
            WorkflowState state = buildState("inst-paused", ExecutionStatus.PAUSED);
            state.setPendingRequestId("pr-9");
            store.save(state);

            assertThat(store.findByPendingRequestId("pr-9").getInstanceId()).isEqualTo("inst-paused");
            assertThat(store.findByPendingRequestId("no-such")).isNull();
            assertThat(store.findByPendingRequestId(null)).isNull();
        }

        @Test
        @DisplayName("listPaused只返回暂停状态实例")
        void listPausedFiltersByStatus() {
            WorkflowState paused = buildState("inst-paused", ExecutionStatus.PAUSED);
            store.save(paused);
            WorkflowState running = buildState("inst-running", ExecutionStatus.RUNNING);
            store.save(running);

            List<WorkflowState> pausedList = store.listPaused();
            assertThat(pausedList).hasSize(1);
            assertThat(pausedList.get(0).getInstanceId()).isEqualTo("inst-paused");

            List<WorkflowState> runningList = store.listRunning();
            assertThat(runningList).hasSize(1);
            assertThat(runningList.get(0).getInstanceId()).isEqualTo("inst-running");
        }
    }

    // ==================== 删除与查询 ====================

    @Nested
    @DisplayName("删除与查询测试")
    class DeleteQueryTests {

        @Test
        @DisplayName("删除后不再可加载")
        void deleteRemoves() {
            store.save(buildState("inst-1", ExecutionStatus.RUNNING));
            store.delete("inst-1");
            assertThat(store.load("inst-1")).isNull();
        }

        @Test
        @DisplayName("按定义名称查询实例列表")
        void queryByDefinitionName() {
            store.save(buildState("inst-1", ExecutionStatus.RUNNING));
            store.save(buildState("inst-2", ExecutionStatus.PAUSED));

            List<WorkflowState> list = store.queryByDefinitionName("test-workflow");
            assertThat(list).hasSize(2);
            assertThat(store.queryByDefinitionName("other")).isEmpty();
            assertThat(store.queryByDefinitionName(null)).isEmpty();
        }
    }
}
