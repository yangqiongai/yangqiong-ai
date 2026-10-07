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
import type { GovernanceDashboardSummary } from '@yangqiong/shared';
import { request } from './api-client';

/**
 * 治理驾驶舱接口
 */
export const governanceDashboardApi = {
  /**
   * 聚合查询驾驶舱summary数据(信号蜂巢/星系图/雷达流单请求直出，社区端后端按键裁剪)
   * @return
   */
  summary: () => request.get<GovernanceDashboardSummary>('/api/governance/dashboard/summary'),
};
