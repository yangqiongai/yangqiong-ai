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
import { AgentPlaygroundPage } from '@/pages/agent/AgentPlaygroundPage';
import { UserListPage } from '@/pages/system/UserListPage';
import { MenuListPage } from '@/pages/system/MenuListPage';
import { MemoryPage } from '@/pages/memory/MemoryPage';
import { AgentMemoryBasePage } from '@/pages/memory/AgentMemoryBasePage';
import { McpExposePage } from '@/pages/ecosystem/McpExposePage';
import { A2aCardPage } from '@/pages/ecosystem/A2aCardPage';
import { AgentTriggerBasePage } from '@/pages/trigger/AgentTriggerBasePage';
import { IntegrationRecordPage } from '@/pages/integration/IntegrationRecordPage';
import { ChannelConfigPage } from '@/pages/integration/ChannelConfigPage';
import { AlertRulePage } from '@/pages/integration/AlertRulePage';
import { ConnectorPage } from '@/pages/connector/ConnectorPage';
import type { RouteItem } from '@/routes/app-plugin';

/**
 * 管理后台「管理 + Playground」模块路由集合
 */
export const mgmtRoutes: RouteItem[] = [
  {
    path: '/agent-test',
    key: 'agent-test',
    label: 'Agent 测试',
    element: <AgentPlaygroundPage />,
  },
  {
    path: '/users',
    key: 'users',
    label: '用户管理',
    element: <UserListPage />,
  },
  {
    path: '/menus',
    key: 'menus',
    label: '菜单管理',
    element: <MenuListPage />,
  },
  {
    path: '/memory',
    key: 'memory',
    label: '记忆检索',
    element: <MemoryPage />,
  },
  {
    path: '/mcp-expose',
    key: 'mcp-expose',
    label: 'MCP 出口',
    element: <McpExposePage />,
  },
  {
    path: '/a2a-card',
    key: 'a2a-card',
    label: 'A2A 卡片',
    element: <A2aCardPage />,
  },
  {
    path: '/agent-memory',
    key: 'agent-memory',
    label: '记忆条目',
    element: <AgentMemoryBasePage />,
  },
  {
    path: '/triggers',
    key: 'triggers',
    label: '触发规则',
    element: <AgentTriggerBasePage />,
  },
  {
    path: '/integrations',
    key: 'integrations',
    label: '集成记录',
    element: <IntegrationRecordPage />,
  },
  {
    path: '/integration-channels',
    key: 'integration-channels',
    label: '渠道配置',
    element: <ChannelConfigPage />,
  },
  {
    path: '/integration-alert-rules',
    key: 'integration-alert-rules',
    label: '告警规则',
    element: <AlertRulePage />,
  },
  {
    path: '/connectors',
    key: 'connectors',
    label: '连接器',
    element: <ConnectorPage />,
  },
];
