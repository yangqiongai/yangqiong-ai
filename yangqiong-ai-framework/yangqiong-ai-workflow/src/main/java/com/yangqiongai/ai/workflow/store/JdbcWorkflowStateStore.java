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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.workflow.model.WorkflowState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/**
 * 基于数据库的工作流状态存储
 * @author yangqiong
 */
public class JdbcWorkflowStateStore implements WorkflowStateStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcWorkflowStateStore.class);

    private static final String UPSERT_SQL = """
            INSERT INTO ai_workflow_state (instance_id, definition_name, status, paused_node_id, pending_request_id,
                paused_reason, paused_by, paused_time, state_json, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                definition_name = VALUES(definition_name), status = VALUES(status),
                paused_node_id = VALUES(paused_node_id), pending_request_id = VALUES(pending_request_id),
                paused_reason = VALUES(paused_reason), paused_by = VALUES(paused_by), paused_time = VALUES(paused_time),
                state_json = VALUES(state_json), update_time = VALUES(update_time)
            """;

    private static final String SELECT_BY_ID = """
            SELECT instance_id, definition_name, status, paused_node_id, pending_request_id,
                paused_reason, paused_by, paused_time, state_json, create_time, update_time
            FROM ai_workflow_state WHERE instance_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper;

    private final WorkflowStateRowMapper rowMapper = new WorkflowStateRowMapper();

    public JdbcWorkflowStateStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.rowMapper.objectMapper = objectMapper;
    }

    /**
     * 保存工作流状态
     * @param state
     */
    @Override
    public void save(WorkflowState state) {
        if (state == null || state.getInstanceId() == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Long createTime = state.getCreateTime() != null ? state.getCreateTime() : now;
        jdbcTemplate.update(UPSERT_SQL,
                state.getInstanceId(),
                state.getDefinitionName(),
                state.getStatus() != null ? state.getStatus().name() : null,
                state.getPausedNodeId(),
                state.getPendingRequestId(),
                state.getPausedReason(),
                state.getPausedBy(),
                state.getPausedTime(),
                toJson(state),
                createTime,
                state.getUpdateTime() != null ? state.getUpdateTime() : now);
    }

    /**
     * 加载工作流状态
     * @param instanceId
     * @return
     */
    @Override
    public WorkflowState load(String instanceId) {
        if (instanceId == null) {
            return null;
        }
        List<WorkflowState> list = jdbcTemplate.query(SELECT_BY_ID, rowMapper, instanceId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 删除工作流状态
     * @param instanceId
     */
    @Override
    public void delete(String instanceId) {
        if (instanceId == null) {
            return;
        }
        jdbcTemplate.update("DELETE FROM ai_workflow_state WHERE instance_id = ?", instanceId);
    }

    /**
     * 根据定义名称查询工作流状态列表
     * @param definitionName
     * @return
     */
    @Override
    public List<WorkflowState> queryByDefinitionName(String definitionName) {
        if (definitionName == null) {
            return List.of();
        }
        return jdbcTemplate.query(SELECT_BY_ID.replace("WHERE instance_id = ?",
                "WHERE definition_name = ? ORDER BY create_time DESC"), rowMapper, definitionName);
    }

    /**
     * 根据审批请求ID查找暂停的工作流状态
     * @param pendingRequestId
     * @return
     */
    @Override
    public WorkflowState findByPendingRequestId(String pendingRequestId) {
        if (pendingRequestId == null) {
            return null;
        }
        List<WorkflowState> list = jdbcTemplate.query(SELECT_BY_ID.replace("WHERE instance_id = ?",
                "WHERE pending_request_id = ?"), rowMapper, pendingRequestId);
        return list.isEmpty() ? null : list.get(0);
    }

    /**
     * 查询全部运行中的工作流状态
     * @return
     */
    @Override
    public List<WorkflowState> listRunning() {
        return jdbcTemplate.query(SELECT_BY_ID.replace("WHERE instance_id = ?",
                "WHERE status = 'RUNNING'"), rowMapper);
    }

    /**
     * 查询全部暂停中的工作流状态
     * @return
     */
    @Override
    public List<WorkflowState> listPaused() {
        return jdbcTemplate.query(SELECT_BY_ID.replace("WHERE instance_id = ?",
                "WHERE status = 'PAUSED'"), rowMapper);
    }

    /**
     * 序列化状态快照
     * @param state
     * @return
     */
    private String toJson(WorkflowState state) {
        try {
            return objectMapper.writeValueAsString(state);
        } catch (JsonProcessingException e) {
            log.error("工作流状态序列化失败: instanceId={}", state.getInstanceId(), e);
            return "{}";
        }
    }

    /**
     * 工作流状态行映射（与写入共用同一ObjectMapper，避免序列化配置不对称）
     */
    private class WorkflowStateRowMapper implements RowMapper<WorkflowState> {

        private ObjectMapper objectMapper;

        @Override
        public WorkflowState mapRow(ResultSet rs, int rowNum) throws SQLException {
            String json = rs.getString("state_json");
            try {
                if (json != null && !json.isBlank()) {
                    return objectMapper.readValue(json, WorkflowState.class);
                }
            } catch (JsonProcessingException e) {
                log.error("工作流状态反序列化失败: instanceId={}", rs.getString("instance_id"), e);
            }
            // 快照缺失时退化为按列组装的骨架状态
            WorkflowState state = new WorkflowState();
            state.setInstanceId(rs.getString("instance_id"));
            state.setDefinitionName(rs.getString("definition_name"));
            String status = rs.getString("status");
            if (status != null) {
                state.setStatus(com.yangqiongai.ai.workflow.model.ExecutionStatus.valueOf(status));
            }
            state.setPausedNodeId(rs.getString("paused_node_id"));
            state.setPendingRequestId(rs.getString("pending_request_id"));
            state.setPausedReason(rs.getString("paused_reason"));
            state.setPausedBy(rs.getString("paused_by"));
            state.setPausedTime(getLong(rs, "paused_time"));
            state.setCreateTime(getLong(rs, "create_time"));
            state.setUpdateTime(getLong(rs, "update_time"));
            return state;
        }

        private Long getLong(ResultSet rs, String column) throws SQLException {
            long value = rs.getLong(column);
            return rs.wasNull() ? null : value;
        }
    }
}
