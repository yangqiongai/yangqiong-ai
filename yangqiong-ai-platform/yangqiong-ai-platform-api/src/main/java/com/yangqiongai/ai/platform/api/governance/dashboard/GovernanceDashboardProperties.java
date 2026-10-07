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

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 治理驾驶舱聚合配置
 * @author yangqiong
 */
@Component
@ConfigurationProperties(prefix = "ai.governance.dashboard")
public class GovernanceDashboardProperties {

    /**
     * 企业专属信号拼装开关(企业server开启，社区默认关闭实现六重隔离)
     */
    private boolean enterpriseSignals = false;

    /**
     * SLA退化判定阈值(近7天失败率>=该值视为退化)
     */
    private BigDecimal slaDegradedThreshold = new BigDecimal("0.10");

    /**
     * 每信号源下发最近事件条数
     */
    private int recentLimit = 5;

    /**
     * 触发器连续失败判定阈值(企业信号，最近N次全部失败视为连续失败)
     */
    private int consecutiveFailureThreshold = 3;

    /**
     * 审计异常统计窗口天数(企业信号，近N天拒绝判定计数)
     */
    private int auditAnomalyDays = 7;

    public boolean isEnterpriseSignals() {
        return enterpriseSignals;
    }

    public void setEnterpriseSignals(boolean enterpriseSignals) {
        this.enterpriseSignals = enterpriseSignals;
    }

    public BigDecimal getSlaDegradedThreshold() {
        return slaDegradedThreshold;
    }

    public void setSlaDegradedThreshold(BigDecimal slaDegradedThreshold) {
        this.slaDegradedThreshold = slaDegradedThreshold;
    }

    public int getRecentLimit() {
        return recentLimit;
    }

    public void setRecentLimit(int recentLimit) {
        this.recentLimit = recentLimit;
    }

    public int getConsecutiveFailureThreshold() {
        return consecutiveFailureThreshold;
    }

    public void setConsecutiveFailureThreshold(int consecutiveFailureThreshold) {
        this.consecutiveFailureThreshold = consecutiveFailureThreshold;
    }

    public int getAuditAnomalyDays() {
        return auditAnomalyDays;
    }

    public void setAuditAnomalyDays(int auditAnomalyDays) {
        this.auditAnomalyDays = auditAnomalyDays;
    }
}
