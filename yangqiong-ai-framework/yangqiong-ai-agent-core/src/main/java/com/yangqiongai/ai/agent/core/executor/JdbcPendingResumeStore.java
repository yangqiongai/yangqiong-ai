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
package com.yangqiongai.ai.agent.core.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * JDBC暂停恢复登记存储
 * <p>
 * 多节点部署实现，登记持久化到ai_agent_pending_resume表，
 * pop通过条件更新原子抢占防止重复恢复，内存句柄不落库（跨节点恢复凭登记数据重建现场）。
 * </p>
 * @author yangqiong
 */
public class JdbcPendingResumeStore implements PendingResumeStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcPendingResumeStore.class);

    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_RESOLVED = "RESOLVED";

    private static final String STATUS_EXPIRED = "EXPIRED";

    private static final String INSERT_SQL =
            "INSERT INTO ai_agent_pending_resume (request_id, session_id, tool_call_id, scope_id, run_id, "
                    + "agent_code, resume_type, user_id, node_id, pending_data, request_data, status, "
                    + "expire_time, create_user, create_time, update_user, update_time) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), ?, NOW()) "
                    + "ON DUPLICATE KEY UPDATE session_id = VALUES(session_id), tool_call_id = VALUES(tool_call_id), "
                    + "scope_id = VALUES(scope_id), run_id = VALUES(run_id), agent_code = VALUES(agent_code), "
                    + "resume_type = VALUES(resume_type), user_id = VALUES(user_id), node_id = VALUES(node_id), "
                    + "pending_data = VALUES(pending_data), request_data = VALUES(request_data), status = VALUES(status), "
                    + "expire_time = VALUES(expire_time), update_time = NOW()";

    private static final String POP_SQL =
            "UPDATE ai_agent_pending_resume SET status = ?, update_time = NOW() "
                    + "WHERE request_id = ? AND status = ? AND expire_time > NOW()";

    private static final String SELECT_SQL =
            "SELECT request_id, session_id, tool_call_id, scope_id, run_id, agent_code, resume_type, "
                    + "user_id, node_id, pending_data, request_data, status, expire_time, create_time "
                    + "FROM ai_agent_pending_resume WHERE request_id = ?";

    private static final String EXISTS_SQL =
            "SELECT COUNT(1) FROM ai_agent_pending_resume "
                    + "WHERE request_id = ? AND status = ? AND expire_time > NOW()";

    private static final String EXPIRE_SQL =
            "UPDATE ai_agent_pending_resume SET status = ?, update_time = NOW() "
                    + "WHERE status = ? AND expire_time <= NOW()";

    private final DataSource dataSource;

    /**
     * 以数据源装配登记存储
     * @param dataSource
     */
    public JdbcPendingResumeStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void register(PendingResumeEntry entry) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(INSERT_SQL)) {
            statement.setString(1, entry.getRequestId());
            statement.setString(2, entry.getSessionId());
            statement.setString(3, entry.getToolCallId());
            statement.setString(4, entry.getScopeId());
            statement.setString(5, entry.getRunId());
            statement.setString(6, entry.getAgentCode());
            statement.setString(7, entry.getResumeType());
            statement.setString(8, entry.getUserId());
            statement.setString(9, entry.getNodeId());
            statement.setString(10, entry.getPendingData());
            statement.setString(11, entry.getRequestData());
            statement.setString(12, STATUS_PENDING);
            statement.setTimestamp(13, Timestamp.from(entry.getExpireTime()));
            statement.setString(14, entry.getUserId());
            statement.setString(15, entry.getUserId());
            statement.executeUpdate();
        } catch (SQLException e) {
            log.error("暂停恢复登记写入失败: requestId={}", entry.getRequestId(), e);
            throw new IllegalStateException("暂停恢复登记写入失败", e);
        }
    }

    @Override
    public PendingResumeEntry pop(String requestId) {
        try (Connection connection = dataSource.getConnection()) {
            // 条件更新原子抢占，多节点并发pop仅一个成功
            try (PreparedStatement statement = connection.prepareStatement(POP_SQL)) {
                statement.setString(1, STATUS_RESOLVED);
                statement.setString(2, requestId);
                statement.setString(3, STATUS_PENDING);
                if (statement.executeUpdate() != 1) {
                    return null;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(SELECT_SQL)) {
                statement.setString(1, requestId);
                try (ResultSet rs = statement.executeQuery()) {
                    return rs.next() ? mapRow(rs) : null;
                }
            }
        } catch (SQLException e) {
            log.error("暂停恢复登记弹出失败: requestId={}", requestId, e);
            return null;
        }
    }

    @Override
    public boolean exists(String requestId) {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXISTS_SQL)) {
            statement.setString(1, requestId);
            statement.setString(2, STATUS_PENDING);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            log.warn("暂停恢复登记查询失败: requestId={}", requestId, e);
            return false;
        }
    }

    /**
     * 过期登记定时清理（每5分钟，未开启调度时静默）
     */
    @Scheduled(fixedDelay = 300_000)
    public void cleanExpired() {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(EXPIRE_SQL)) {
            statement.setString(1, STATUS_EXPIRED);
            statement.setString(2, STATUS_PENDING);
            int expired = statement.executeUpdate();
            if (expired > 0) {
                log.info("暂停恢复登记过期清理: count={}", expired);
            }
        } catch (SQLException e) {
            log.warn("暂停恢复登记过期清理失败", e);
        }
    }

    /**
     * 结果集行转登记对象
     * @param rs
     * @return
     * @throws SQLException
     */
    private PendingResumeEntry mapRow(ResultSet rs) throws SQLException {
        PendingResumeEntry entry = new PendingResumeEntry();
        entry.setRequestId(rs.getString("request_id"));
        entry.setSessionId(rs.getString("session_id"));
        entry.setToolCallId(rs.getString("tool_call_id"));
        entry.setScopeId(rs.getString("scope_id"));
        entry.setRunId(rs.getString("run_id"));
        entry.setAgentCode(rs.getString("agent_code"));
        entry.setResumeType(rs.getString("resume_type"));
        entry.setUserId(rs.getString("user_id"));
        entry.setNodeId(rs.getString("node_id"));
        entry.setPendingData(rs.getString("pending_data"));
        entry.setRequestData(rs.getString("request_data"));
        entry.setStatus(rs.getString("status"));
        Timestamp expireTime = rs.getTimestamp("expire_time");
        entry.setExpireTime(expireTime != null ? expireTime.toInstant() : null);
        Timestamp createTime = rs.getTimestamp("create_time");
        entry.setCreatedAt(createTime != null ? createTime.toInstant() : Instant.now());
        return entry;
    }
}
