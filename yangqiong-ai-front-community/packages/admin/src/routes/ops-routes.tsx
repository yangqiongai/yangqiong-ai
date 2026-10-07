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
import { EvaluationPage } from '@/pages/evaluation/EvaluationPage';
import { ApprovalListPage } from '@/pages/approval/ApprovalListPage';
import { TraceRunPage } from '@/pages/trace/TraceRunPage';
import { WebhookPage } from '@/pages/webhook/WebhookPage';
import { OpenCapabilityPage } from '@/pages/open-capability/OpenCapabilityPage';
import type { RouteItem } from '@/routes/app-plugin';

/**
 * 运营类管理页面路由
 */
export const opsRoutes: RouteItem[] = [
  {
    path: '/evaluations',
    key: 'evaluations',
    label: '评测管理',
    element: <EvaluationPage />,
  },
  {
    path: '/trace-runs',
    key: 'trace-runs',
    label: '运行回放',
    element: <TraceRunPage />,
  },
  {
    path: '/webhooks',
    key: 'webhooks',
    label: '事件订阅',
    element: <WebhookPage />,
  },
  {
    path: '/approvals',
    key: 'approvals',
    label: '变更审批',
    element: <ApprovalListPage />,
  },
  {
    path: '/open-capabilities',
    key: 'open-capabilities',
    label: '开放能力管理',
    element: <OpenCapabilityPage />,
  },
];
