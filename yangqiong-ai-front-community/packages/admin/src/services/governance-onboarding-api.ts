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
import type { OnboardingStatus } from '@yangqiong/shared';
import { request } from './api-client';

/**
 * 首次接入接口(社区裁剪版：仅状态判定，示例数据导入仅企业端开启)
 */
export const governanceOnboardingApi = {
  /**
   * 查询接入状态(四项完成度)
   * @return
   */
  status: () => request.get<OnboardingStatus>('/api/agent/governance/onboarding/status'),
};
