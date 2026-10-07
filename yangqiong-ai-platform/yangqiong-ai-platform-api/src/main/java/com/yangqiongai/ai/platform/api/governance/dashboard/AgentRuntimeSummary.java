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

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Agent运行摘要（星系图节点映射数据源）
 * @author yangqiong
 */
public class AgentRuntimeSummary {

    /**
     * Agent编码
     */
    private String agentCode;

    /**
     * Agent名称
     */
    private String agentName;

    /**
     * 状态(0-禁用 1-启用)
     */
    private Integer status;

    /**
     * 最近一次发布时间(yyyy-MM-dd HH:mm:ss)
     */
    private String latestPublishTime;

    /**
     * 最近一次发布流水号
     */
    private String latestReleaseNo;

    /**
     * 近7天运行次数
     */
    private Long runs7d;

    /**
     * 近7天失败次数
     */
    private Long failures7d;

    /**
     * 近30天成本USD
     */
    private BigDecimal cost30d;

    /**
     * 月度预算USD(未配置为null，成本超限项不计)
     */
    private BigDecimal budgetAmount;

    /**
     * 信号徽标(键=信号类型，值=数量；可由前端从runs7d/budget推导的信号不重复下发)
     */
    private Map<String, Long> signalFlags = new LinkedHashMap<>();

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getAgentName() {
        return agentName;
    }

    public void setAgentName(String agentName) {
        this.agentName = agentName;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getLatestPublishTime() {
        return latestPublishTime;
    }

    public void setLatestPublishTime(String latestPublishTime) {
        this.latestPublishTime = latestPublishTime;
    }

    public String getLatestReleaseNo() {
        return latestReleaseNo;
    }

    public void setLatestReleaseNo(String latestReleaseNo) {
        this.latestReleaseNo = latestReleaseNo;
    }

    public Long getRuns7d() {
        return runs7d;
    }

    public void setRuns7d(Long runs7d) {
        this.runs7d = runs7d;
    }

    public Long getFailures7d() {
        return failures7d;
    }

    public void setFailures7d(Long failures7d) {
        this.failures7d = failures7d;
    }

    public BigDecimal getCost30d() {
        return cost30d;
    }

    public void setCost30d(BigDecimal cost30d) {
        this.cost30d = cost30d;
    }

    public BigDecimal getBudgetAmount() {
        return budgetAmount;
    }

    public void setBudgetAmount(BigDecimal budgetAmount) {
        this.budgetAmount = budgetAmount;
    }

    public Map<String, Long> getSignalFlags() {
        return signalFlags;
    }

    public void setSignalFlags(Map<String, Long> signalFlags) {
        this.signalFlags = signalFlags;
    }
}
