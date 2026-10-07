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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * 基于数据库的工作流暂停恢复流水存储
 * @author yangqiong
 */
public class JdbcWorkflowPauseHistoryStore implements WorkflowPauseHistoryStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowPauseHistoryStore.class);

    private static final String INSERT_SQL = """
            INSERT INTO ai_workflow_pause_history (id, scope_id, instance_id, definition_name, paused_node_id,
                action, reason, operator, operator_time, pending_request_id, wait_duration_ms, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_SQL = """
            SELECT id, scope_id, instance_id, definition_name, paused_node_id,
                action, reason, operator, operator_time, pending_request_id, wait_duration_ms, create_time
            FROM ai_workflow_pause_history WHERE instance_id = ? ORDER BY operator_time ASC, create_time ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<WorkflowPauseHistory> rowMapper = new HistoryRowMapper();

    public JdbcWorkflowPauseHistoryStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 记录暂停/恢复流水
     * @param history
     */
    @Override
    public void record(WorkflowPauseHistory history) {
        if (history == null || history.getInstanceId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        jdbcTemplate.update(INSERT_SQL,
                history.getId() != null ? history.getId() : UUID.randomUUID().toString().replace("-", ""),
                history.getScopeId(),
                history.getInstanceId(),
                history.getDefinitionName(),
                history.getPausedNodeId(),
                history.getAction(),
                history.getReason(),
                history.getOperator(),
                history.getOperatorTime() != null ? history.getOperatorTime() : now,
                history.getPendingRequestId(),
                history.getWaitDurationMs(),
                history.getCreateTime() != null ? history.getCreateTime() : now);
    }

    /**
     * 按实例ID查询暂停恢复流水
     * @param instanceId
     * @return
     */
    @Override
    public List<WorkflowPauseHistory> listByInstanceId(String instanceId) {
        if (instanceId == null) {
            return List.of();
        }
        return jdbcTemplate.query(SELECT_SQL, rowMapper, instanceId);
    }

    /**
     * 暂停恢复流行映射
     */
    private static class HistoryRowMapper implements RowMapper<WorkflowPauseHistory> {

        @Override
        public WorkflowPauseHistory mapRow(ResultSet rs, int rowNum) throws SQLException {
            WorkflowPauseHistory history = new WorkflowPauseHistory();
            history.setId(rs.getString("id"));
            history.setScopeId(rs.getString("scope_id"));
            history.setInstanceId(rs.getString("instance_id"));
            history.setDefinitionName(rs.getString("definition_name"));
            history.setPausedNodeId(rs.getString("paused_node_id"));
            history.setAction(rs.getString("action"));
            history.setReason(rs.getString("reason"));
            history.setOperator(rs.getString("operator"));
            history.setOperatorTime(getLong(rs, "operator_time"));
            history.setPendingRequestId(rs.getString("pending_request_id"));
            history.setWaitDurationMs(getLong(rs, "wait_duration_ms"));
            history.setCreateTime(getLong(rs, "create_time"));
            return history;
        }

        private Long getLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }
}
