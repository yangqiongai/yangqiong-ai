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
package com.yangqiongai.ai.platform.api.governance.dashboard;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 治理驾驶舱聚合响应
 * <p>
 * 全部区块由本端点单请求驱动（蜂巢/星系图/雷达流/趋势），禁止前端逐Agent查SLA/账单形成N+1。
 * 社区端仅含信号1-3的键，企业专属键由企业侧贡献者按开关拼装。
 * </p>
 * @author yangqiong
 */
public class GovernanceDashboardSummaryResponse {

    /**
     * 信号计数(键=信号类型: driftDetected/slaDegraded/costOverrun/triggerFailures/staleMemory/
     * quarantinedMemory/rotationDue/auditAnomalies/pendingProposals)
     */
    private Map<String, Long> signals = new LinkedHashMap<>();

    /**
     * 各信号近7天逐日计数(每信号源一次GROUP BY日期，供蜂巢hover sparkline)
     */
    private Map<String, List<Long>> signalTrend7d = new LinkedHashMap<>();

    /**
     * Agent运行摘要清单(星系图节点映射)
     */
    private List<AgentRuntimeSummary> agents;

    /**
     * 最近信号事件合并时间轴(各信号源各取最新N条服务端预合并，供雷达流首屏直出)
     */
    private List<GovernanceRecentItem> recent;

    /**
     * 趋势地平线(近7天运行/成本)
     */
    private GovernanceTrendSeries trend;

    /**
     * 数据生成时间(yyyy-MM-dd HH:mm:ss)
     */
    private String generatedAt;

    public Map<String, Long> getSignals() {
        return signals;
    }

    public void setSignals(Map<String, Long> signals) {
        this.signals = signals;
    }

    public Map<String, List<Long>> getSignalTrend7d() {
        return signalTrend7d;
    }

    public void setSignalTrend7d(Map<String, List<Long>> signalTrend7d) {
        this.signalTrend7d = signalTrend7d;
    }

    public List<AgentRuntimeSummary> getAgents() {
        return agents;
    }

    public void setAgents(List<AgentRuntimeSummary> agents) {
        this.agents = agents;
    }

    public List<GovernanceRecentItem> getRecent() {
        return recent;
    }

    public void setRecent(List<GovernanceRecentItem> recent) {
        this.recent = recent;
    }

    public GovernanceTrendSeries getTrend() {
        return trend;
    }

    public void setTrend(GovernanceTrendSeries trend) {
        this.trend = trend;
    }

    public String getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(String generatedAt) {
        this.generatedAt = generatedAt;
    }
}
