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
package com.yangqiongai.ai.workflow.spi;

import com.yangqiongai.ai.workflow.model.WorkflowDefinition;

/**
 * 工作流执行治理门禁
 * <p>
 * 工作流执行入口同步段回调，用于执行配额管控与灰度版本路由。
 * 未注入实现时社区引擎直接放行，默认降级不报错。
 * </p>
 * @author yangqiong
 */
public interface WorkflowGovernanceGate {

    /**
     * 执行前治理检查
     * @param definition 工作流定义
     * @param scopeId 当前作用域ID（同步入口线程捕获）
     * @return 治理决策，放行且pinnedVersion非空时按该版本执行
     */
    GovernanceDecision checkExecute(WorkflowDefinition definition, String scopeId);
}
