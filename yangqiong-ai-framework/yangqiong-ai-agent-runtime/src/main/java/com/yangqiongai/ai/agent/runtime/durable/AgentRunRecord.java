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

import java.util.ArrayList;
import java.util.List;

/**
 * Agent运行记录
 * <p>
 * 描述一次Agent执行的完整生命周期信息，供{@link AgentRunStore}持久化与续跑查询。
 * </p>
 * @author yangqiong
 */
public class AgentRunRecord {

    /**
     * 运行ID
     */
    private final String runId;

    /**
     * 隔离域ID
     */
    private final String scopeId;

    /**
     * 会话ID
     */
    private final String sessionId;

    /**
     * 用户ID
     */
    private final String userId;

    /**
     * Agent名称
     */
    private final String agentName;

    /**
     * 创建时间戳（毫秒）
     */
    private final long createdAt;

    /**
     * 更新时间戳（毫秒）
     */
    private long updatedAt;

    /**
     * 乐观锁版本号
     */
    private long version;

    /**
     * 当前运行状态
     */
    private AgentRunState state;

    /**
     * 失败原因，成功时为null
     */
    private String error;

    /**
     * 状态迁移轨迹
     */
    private final List<StateTransition> transitions;

    public AgentRunRecord(String runId, String scopeId, String sessionId, String userId,
                          String agentName, AgentRunState state, long createdAt) {
        this.runId = runId;
        this.scopeId = scopeId;
        this.sessionId = sessionId;
        this.userId = userId;
        this.agentName = agentName;
        this.state = state;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
        this.transitions = new ArrayList<>();
    }

    /**
     * 迁移到目标状态并追加轨迹，非法迁移拒绝
     * @param target 目标状态
     * @param reason 迁移原因
     * @return
     */
    public synchronized AgentRunRecord transitionTo(AgentRunState target, String reason) {
        if (!state.canTransitionTo(target)) {
            throw new IllegalStateException("非法状态迁移: " + state + " -> " + target + ", runId=" + runId);
        }
        this.state = target;
        this.updatedAt = System.currentTimeMillis();
        this.version++;
        if (target == AgentRunState.FAILED && reason != null) {
            this.error = reason;
        }
        this.transitions.add(new StateTransition(target, this.updatedAt, reason));
        return this;
    }

    /**
     * 按持久化快照恢复运行记录(转换场景直接装配,不走状态机校验)
     * @param state 当前状态
     * @param version 乐观锁版本号
     * @param updatedAt 更新时间戳(毫秒)
     * @param error 失败原因
     * @param transitions 状态迁移轨迹
     */
    public synchronized void restore(AgentRunState state, long version, long updatedAt,
                                     String error, List<StateTransition> transitions) {
        this.state = state;
        this.version = version;
        this.updatedAt = updatedAt;
        if (error != null) {
            this.error = error;
        }
        if (transitions != null) {
            this.transitions.clear();
            this.transitions.addAll(transitions);
        }
    }

    /**
     * 按期望版本做乐观锁替换
     * @param expectedVersion 期望版本号
     * @param updater 版本匹配时执行的更新动作
     * @return true时替换成功
     */
    public synchronized boolean casUpdate(long expectedVersion, Runnable updater) {
        if (this.version != expectedVersion) {
            return false;
        }
        updater.run();
        this.version++;
        this.updatedAt = System.currentTimeMillis();
        return true;
    }

    /**
     * 获取运行ID
     * @return
     */
    public String getRunId() {
        return runId;
    }

    /**
     * 获取隔离域ID
     * @return
     */
    public String getScopeId() {
        return scopeId;
    }

    /**
     * 获取会话ID
     * @return
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * 获取用户ID
     * @return
     */
    public String getUserId() {
        return userId;
    }

    /**
     * 获取Agent名称
     * @return
     */
    public String getAgentName() {
        return agentName;
    }

    /**
     * 获取创建时间戳（毫秒）
     * @return
     */
    public long getCreatedAt() {
        return createdAt;
    }

    /**
     * 获取更新时间戳（毫秒）
     * @return
     */
    public long getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间戳（毫秒）
     * @param updatedAt
     */
    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    /**
     * 获取版本号
     * @return
     */
    public long getVersion() {
        return version;
    }

    /**
     * 设置版本号
     * @param version
     */
    public void setVersion(long version) {
        this.version = version;
    }

    /**
     * 获取当前运行状态
     * @return
     */
    public AgentRunState getState() {
        return state;
    }

    /**
     * 设置当前运行状态
     * @param state
     */
    public void setState(AgentRunState state) {
        this.state = state;
    }

    /**
     * 获取失败原因
     * @return
     */
    public String getError() {
        return error;
    }

    /**
     * 设置失败原因
     * @param error
     */
    public void setError(String error) {
        this.error = error;
    }

    /**
     * 获取状态迁移轨迹
     * @return
     */
    public List<StateTransition> getTransitions() {
        return transitions;
    }

    @Override
    public String toString() {
        return "AgentRunRecord{runId=" + runId + ", sessionId=" + sessionId
                + ", agentName=" + agentName + ", state=" + state + ", version=" + version + "}";
    }

    /**
     * 状态迁移轨迹项
     * @param state 目标状态
     * @param timestamp 迁移时间戳（毫秒）
     * @param reason 迁移原因
     */
    public record StateTransition(AgentRunState state, long timestamp, String reason) {
    }
}
