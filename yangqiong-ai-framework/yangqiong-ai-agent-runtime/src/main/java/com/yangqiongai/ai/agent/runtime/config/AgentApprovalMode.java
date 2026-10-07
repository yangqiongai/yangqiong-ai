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
package com.yangqiongai.ai.agent.runtime.config;

/**
 * Agent审批模式
 * <p>
 * 统一审批配置入口的四类模式：MANUAL为人工审批（破坏性工具暂停等待人工确认）、
 * AUTO为AI自动审批（由审批模型依据工具调用入参判定放行或拒绝，判定失败可回退人工）、
 * FULL_ACCESS为完全访问（跳过全部审批门控）、CUSTOM为自定义（沿用权限模式、规则、
 * 名单与策略门的既有细粒度配置）。
 * </p>
 * @author yangqiong
 */
public enum AgentApprovalMode {

    /**
     * 人工审批：破坏性工具调用暂停并发出待确认事件，由调用方通过resume注入审批结果
     */
    MANUAL,

    /**
     * AI自动审批：由审批模型依据工具名与调用入参判定放行或拒绝，无需人工介入
     */
    AUTO,

    /**
     * 完全访问：跳过全部权限与审批门控，所有工具直接放行
     */
    FULL_ACCESS,

    /**
     * 自定义：沿用permissionState规则、白/黑名单、requireApproval与toolPolicyGate既有细粒度配置
     */
    CUSTOM
}
