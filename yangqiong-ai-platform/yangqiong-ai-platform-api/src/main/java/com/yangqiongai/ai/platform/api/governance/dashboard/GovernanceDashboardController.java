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

import com.yangqiongai.ai.common.bean.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 治理驾驶舱接口
 * @author yangqiong
 */
@Tag(name = "治理驾驶舱接口")
@RestController
@RequestMapping("/api/governance/dashboard")
@ConditionalOnProperty(name = "ai.governance.dashboard.enabled", havingValue = "true", matchIfMissing = true)
public class GovernanceDashboardController {

    @Autowired
    private GovernanceDashboardService governanceDashboardService;

    /**
     * 聚合查询驾驶舱summary数据(信号蜂巢/星系图/雷达流/趋势地平线单请求直出)
     * @return
     */
    @Operation(summary = "聚合查询治理驾驶舱summary数据")
    @GetMapping("/summary")
    public ApiResult<GovernanceDashboardSummaryResponse> summary() {
        return ApiResult.ok(governanceDashboardService.summary());
    }
}
