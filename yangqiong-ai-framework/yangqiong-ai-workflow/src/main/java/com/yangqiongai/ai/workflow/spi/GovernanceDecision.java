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

import lombok.Data;

/**
 * 工作流执行治理决策
 * @author yangqiong
 */
@Data
public class GovernanceDecision {

    /**
     * 是否允许执行
     */
    private boolean allow;

    /**
     * 拒绝原因
     */
    private String rejectReason;

    /**
     * 钉住的执行版本，null时按最新版本执行（灰度路由使用）
     */
    private Integer pinnedVersion;

    /**
     * 构建放行决策
     * @return
     */
    public static GovernanceDecision allow() {
        GovernanceDecision decision = new GovernanceDecision();
        decision.setAllow(true);
        return decision;
    }

    /**
     * 构建带钉版放行决策（灰度路由到指定版本）
     * @param pinnedVersion
     * @return
     */
    public static GovernanceDecision allowWithVersion(Integer pinnedVersion) {
        GovernanceDecision decision = allow();
        decision.setPinnedVersion(pinnedVersion);
        return decision;
    }

    /**
     * 构建拒绝决策
     * @param rejectReason
     * @return
     */
    public static GovernanceDecision reject(String rejectReason) {
        GovernanceDecision decision = new GovernanceDecision();
        decision.setAllow(false);
        decision.setRejectReason(rejectReason);
        return decision;
    }
}
