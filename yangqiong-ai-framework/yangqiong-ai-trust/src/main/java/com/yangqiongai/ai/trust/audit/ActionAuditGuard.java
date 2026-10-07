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

import com.yangqiongai.ai.agent.runtime.spi.ToolInvocation;
import com.yangqiongai.ai.agent.runtime.spi.ToolInvocationGuard;
import com.yangqiongai.ai.agent.runtime.spi.ToolInvocationOutcome;
import com.yangqiongai.ai.trust.audit.entity.AgentActionLog;

/**
 * 工具调用审计守卫
 * <p>
 * 挂接引擎工具调用链：社区基础版前置一律放行，事后按开关落审计日志；
 * 仅守卫拒绝记DENY，执行失败仍为ALLOW并留失败摘要。守卫异常不阻断主流程。
 * </p>
 * @author yangqiong
 */
public class ActionAuditGuard implements ToolInvocationGuard {

    /**
     * 工具调用动作类型
     */
    private static final String ACTION_TYPE_TOOL_CALL = "TOOL_CALL";

    /**
     * 放行判定
     */
    private static final String DECISION_ALLOW = "ALLOW";

    /**
     * 拒绝判定
     */
    private static final String DECISION_DENY = "DENY";

    /**
     * 动作审计
     */
    private final ActionAuditService actionAuditService;

    public ActionAuditGuard(ActionAuditService actionAuditService) {
        this.actionAuditService = actionAuditService;
    }

    @Override
    public Decision before(ToolInvocation invocation) {
        // 社区基础版不做前置拦截，仅事后留痕；企业版权限画像执行由独立守卫承担
        return Decision.allow();
    }

    @Override
    public void after(ToolInvocationOutcome outcome) {
        if (!actionAuditService.isEnabled() || outcome == null) {
            return;
        }
        AgentActionLog actionLog = new AgentActionLog();
        actionLog.setAgentCode(outcome.getAgentCode());
        actionLog.setScopeId(outcome.getScopeId());
        actionLog.setRunId(outcome.getRunId());
        actionLog.setActionType(ACTION_TYPE_TOOL_CALL);
        actionLog.setResource(outcome.getToolName());
        actionLog.setDecision(outcome.isDenied() ? DECISION_DENY : DECISION_ALLOW);
        actionLog.setSummary(outcome.getSummary());
        actionLog.setDurationMillis(outcome.getDurationMillis());
        actionAuditService.record(actionLog);
    }
}
