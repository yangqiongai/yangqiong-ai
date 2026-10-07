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

import java.util.List;
import java.util.Map;

/**
 * 治理信号贡献者SPI
 * <p>
 * 企业侧实现本接口并注册为Bean，向社区驾驶舱聚合服务追加企业专属信号(4-9)的
 * 计数/趋势/最近事件/Agent徽标；社区端无实现Bean，响应自然裁剪，防绕过。
 * </p>
 * @author yangqiong
 */
public interface GovernanceSignalContributor {

    /**
     * 追加信号计数
     * @param signals 可变信号计数表(键=信号类型)
     */
    void contributeSignals(Map<String, Long> signals);

    /**
     * 追加信号近7天逐日计数(可选)
     * @param signalTrend7d 可变信号趋势表(键=信号类型，列表7位对齐：下标0=6天前)
     */
    default void contributeSignalTrends(Map<String, List<Long>> signalTrend7d) {
    }

    /**
     * 追加最近信号事件(可选)
     * @param recent 可变合并时间轴
     */
    default void contributeRecent(List<GovernanceRecentItem> recent) {
    }

    /**
     * 追加Agent级信号徽标(可选)
     * @param agentFlags 可变徽标表(键=agentCode，值=信号类型→数量)
     */
    default void contributeAgentFlags(Map<String, Map<String, Long>> agentFlags) {
    }
}
