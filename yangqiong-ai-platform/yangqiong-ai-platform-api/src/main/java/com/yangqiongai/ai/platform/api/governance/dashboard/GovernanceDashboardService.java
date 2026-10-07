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

import com.yangqiongai.ai.agent.data.core.mapper.AgentMapper;
import com.yangqiongai.ai.agent.data.core.mapper.AgentTaskMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 治理驾驶舱聚合
 * <p>
 * 单请求返回驾驶舱全部区块数据：信号计数(蜂巢)、信号趋势(蜂巢sparkline)、
 * Agent运行摘要(星系图节点)、最近信号事件(雷达流)、运行趋势(趋势地平线)。
 * 运行/失败聚合GROUP BY一次取回，消除逐Agent查询N+1；
 * 仅依赖core数据源，企业专属信号(漂移/成本/触发器/记忆等)经{@link GovernanceSignalContributor}追加，
 * 社区端响应自然裁剪。
 * </p>
 * @author yangqiong
 */
@Service
public class GovernanceDashboardService {

    /**
     * 信号键：配置漂移
     */
    public static final String SIGNAL_DRIFT = "driftDetected";

    /**
     * 信号键：SLA退化
     */
    public static final String SIGNAL_SLA = "slaDegraded";

    /**
     * 信号键：成本超限
     */
    public static final String SIGNAL_COST = "costOverrun";

    @Autowired
    private AgentMapper agentMapper;

    @Autowired
    private AgentTaskMapper agentTaskMapper;

    @Autowired
    private GovernanceDashboardProperties properties;

    @Autowired(required = false)
    private List<GovernanceSignalContributor> contributors = List.of();

    /**
     * 聚合治理驾驶舱summary数据
     * @return
     */
    public GovernanceDashboardSummaryResponse summary() {
        LocalDate today = LocalDate.now();
        LocalDateTime from7dTime = today.minusDays(6).atStartOfDay();
        LocalDate from7dDate = today.minusDays(6);

        Map<String, long[]> runAgg = loadRunAgg(from7dTime);

        List<AgentRuntimeSummary> agents = buildAgents(runAgg);

        GovernanceDashboardSummaryResponse response = new GovernanceDashboardSummaryResponse();
        response.setAgents(agents);
        response.setSignals(buildSignals(agents));
        response.setSignalTrend7d(new LinkedHashMap<>());
        response.setTrend(buildTrend(from7dDate));
        response.setRecent(buildRecent(runAgg));
        response.setGeneratedAt(GovernanceRecentItem.TIME_FORMATTER.format(LocalDateTime.now()));
        applyContributors(response);
        return response;
    }

    /**
     * 加载近7天按Agent运行聚合
     * @param fromTime
     * @return agentCode → [totalRuns, failedRuns]
     */
    private Map<String, long[]> loadRunAgg(LocalDateTime fromTime) {
        Map<String, long[]> result = new HashMap<>();
        for (Map<String, Object> row : agentTaskMapper.selectAgentRunAggSince(from7dTimeOf(fromTime))) {
            String agentCode = str(row.get("agent_code"));
            if (agentCode == null) {
                continue;
            }
            result.put(agentCode, new long[]{longValue(row.get("totalRuns")), longValue(row.get("failedRuns"))});
        }
        return result;
    }

    /**
     * 参数透传(便于单测覆写时间基准)
     * @param fromTime
     * @return
     */
    protected LocalDateTime from7dTimeOf(LocalDateTime fromTime) {
        return fromTime;
    }

    /**
     * 构建Agent运行摘要清单
     * @param runAgg
     * @return
     */
    private List<AgentRuntimeSummary> buildAgents(Map<String, long[]> runAgg) {
        List<AgentRuntimeSummary> agents = new ArrayList<>();
        for (Map<String, Object> row : agentMapper.selectDashboardAgents()) {
            String agentCode = str(row.get("agent_code"));
            if (agentCode == null) {
                continue;
            }
            AgentRuntimeSummary summary = new AgentRuntimeSummary();
            summary.setAgentCode(agentCode);
            summary.setAgentName(str(row.get("agent_name")));
            Object status = row.get("status");
            summary.setStatus(status instanceof Number number ? number.intValue() : null);

            long[] runs = runAgg.getOrDefault(agentCode, new long[]{0, 0});
            summary.setRuns7d(runs[0]);
            summary.setFailures7d(runs[1]);
            agents.add(summary);
        }
        return agents;
    }

    /**
     * 构建基础信号计数(SLA退化由core运行数据推导)
     * @param agents
     * @return
     */
    private Map<String, Long> buildSignals(List<AgentRuntimeSummary> agents) {
        Map<String, Long> signals = new LinkedHashMap<>();
        long slaDegraded = 0;
        for (AgentRuntimeSummary agent : agents) {
            if (agent.getRuns7d() != null && agent.getRuns7d() > 0 && agent.getFailures7d() != null
                    && BigDecimal.valueOf(agent.getFailures7d())
                    .divide(BigDecimal.valueOf(agent.getRuns7d()), 4, RoundingMode.HALF_UP)
                    .compareTo(properties.getSlaDegradedThreshold()) >= 0) {
                slaDegraded++;
            }
        }
        signals.put(SIGNAL_SLA, slaDegraded);
        return signals;
    }

    /**
     * 构建运行趋势(7位对齐补零)
     * @param from7dDate
     * @return
     */
    private GovernanceTrendSeries buildTrend(LocalDate from7dDate) {
        GovernanceTrendSeries trend = new GovernanceTrendSeries();
        trend.setRuns7d(alignDaily(agentTaskMapper.selectRunTrendSince(from7dDate.atStartOfDay()),
                from7dDate, "cnt"));
        return trend;
    }

    /**
     * 构建最近信号事件(SLA退化各取最近N条)
     * @param runAgg
     * @return
     */
    private List<GovernanceRecentItem> buildRecent(Map<String, long[]> runAgg) {
        List<GovernanceRecentItem> recent = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<String, long[]> entry : runAgg.entrySet()) {
            long[] runs = entry.getValue();
            if (runs[0] > 0 && BigDecimal.valueOf(runs[1])
                    .divide(BigDecimal.valueOf(runs[0]), 4, RoundingMode.HALF_UP)
                    .compareTo(properties.getSlaDegradedThreshold()) >= 0) {
                long ratePercent = BigDecimal.valueOf(runs[1] * 100)
                        .divide(BigDecimal.valueOf(runs[0]), 0, RoundingMode.HALF_UP).longValue();
                recent.add(new GovernanceRecentItem(SIGNAL_SLA, entry.getKey(), null,
                        "近7天失败率 " + ratePercent + "%（" + runs[1] + "/" + runs[0] + "）", now));
            }
        }
        recent.sort(Comparator.comparing(GovernanceRecentItem::getOccurredAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return recent;
    }

    /**
     * 应用企业侧信号贡献者(社区端无实现Bean，响应自然裁剪)
     * @param response
     */
    private void applyContributors(GovernanceDashboardSummaryResponse response) {
        if (contributors.isEmpty()) {
            return;
        }
        Map<String, Map<String, Long>> agentFlags = new HashMap<>();
        for (GovernanceSignalContributor contributor : contributors) {
            contributor.contributeSignals(response.getSignals());
            contributor.contributeSignalTrends(response.getSignalTrend7d());
            contributor.contributeRecent(response.getRecent());
            contributor.contributeAgentFlags(agentFlags);
        }
        for (AgentRuntimeSummary agent : response.getAgents()) {
            Map<String, Long> flags = agentFlags.get(agent.getAgentCode());
            if (flags != null && !flags.isEmpty()) {
                agent.getSignalFlags().putAll(flags);
            }
        }
    }

    /**
     * 按日对齐趋势序列(7位，缺失补零)
     * @param rows
     * @param from7dDate
     * @param valueKey
     * @return
     */
    private List<Long> alignDaily(List<Map<String, Object>> rows, LocalDate from7dDate, String valueKey) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (Map<String, Object> row : rows) {
            byDay.put(toDate(row.get("statDay")), longValue(row.get(valueKey)));
        }
        List<Long> aligned = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            aligned.add(byDay.getOrDefault(from7dDate.plusDays(6 - i), 0L));
        }
        return aligned;
    }

    private LocalDate toDate(Object value) {
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        if (value instanceof String text && !text.isBlank()) {
            return LocalDate.parse(text.substring(0, 10));
        }
        return null;
    }

    private String str(Object value) {
        return value != null ? value.toString() : null;
    }

    private long longValue(Object value) {
        return value instanceof Number number ? number.longValue() : 0L;
    }
}
