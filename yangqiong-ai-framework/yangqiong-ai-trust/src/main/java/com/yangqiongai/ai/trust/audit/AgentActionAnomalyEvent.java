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
package com.yangqiongai.ai.trust.audit;

/**
 * Agent动作异常事件
 * <p>
 * 异常检测发现拒绝突增/异常时段调用等风险时发布，
 * 平台侧(如企业版告警通道)可监听处理。
 * </p>
 * @author yangqiong
 */
public class AgentActionAnomalyEvent {

    /**
     * 异常类型(DENY_BURST拒绝突增/NIGHT_CALL异常时段)
     */
    private final String anomalyType;

    /**
     * 作用域ID
     */
    private final String scopeId;

    /**
     * Agent编码
     */
    private final String agentCode;

    /**
     * 异常描述
     */
    private final String message;

    public AgentActionAnomalyEvent(String anomalyType, String scopeId, String agentCode, String message) {
        this.anomalyType = anomalyType;
        this.scopeId = scopeId;
        this.agentCode = agentCode;
        this.message = message;
    }

    public String getAnomalyType() {
        return anomalyType;
    }

    public String getScopeId() {
        return scopeId;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getMessage() {
        return message;
    }
}
