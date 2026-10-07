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
import { GovernanceDashboardPage } from '@/pages/governance/GovernanceDashboardPage';
import { GovernanceInboxPage } from '@/pages/governance/GovernanceInboxPage';
import { Agent360Page } from '@/pages/governance/Agent360Page';
import type { RouteItem } from '@/routes/app-plugin';

/**
 * 治理三件套路由(社区裁剪版：驾驶舱/待处置入菜单，360°详情作为子页面由处置跳转进入)
 */
export const governanceRoutes: RouteItem[] = [
  {
    path: '/governance-dashboard',
    key: 'governance-dashboard',
    label: '治理驾驶舱',
    element: <GovernanceDashboardPage />,
  },
  {
    path: '/governance-inbox',
    key: 'governance-inbox',
    label: '治理收件箱',
    element: <GovernanceInboxPage />,
  },
  {
    path: '/governance-agents/:agentCode',
    key: 'governance-agent-360',
    label: 'Agent 360° 详情',
    element: <Agent360Page />,
  },
];
