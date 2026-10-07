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
package com.yangqiongai.ai.memory.agentmemory.model;

/**
 * Agent运行记忆检索入参
 * @author yangqiong
 */
public class MemoryRunQuery {

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * 用户锚点
     */
    private String userAnchor;

    /**
     * 会话ID
     */
    private String sessionId;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 检索查询文本（通常为任务输入）
     */
    private String query;

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getUserAnchor() {
        return userAnchor;
    }

    public void setUserAnchor(String userAnchor) {
        this.userAnchor = userAnchor;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }
}
