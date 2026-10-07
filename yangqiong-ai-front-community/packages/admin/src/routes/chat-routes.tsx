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
import { AgentChatPage } from '@/pages/chat/AgentChatPage';
import { ConversationListPage } from '@/pages/conversation/ConversationListPage';
import { WorkbenchPage } from '@/pages/chat/workbench/WorkbenchPage';
import type { RouteItem } from '@/routes/app-plugin';

/**
 * 对话模块路由集合
 */
export const chatRoutes: RouteItem[] = [
  {
    path: '/chat',
    key: 'agent-chat',
    label: 'Agent 对话',
    element: <AgentChatPage />,
  },
  {
    path: '/workbench',
    key: 'workbench',
    label: '本地工作台',
    element: <WorkbenchPage />,
  },
  {
    path: '/conversations',
    key: 'conversations',
    label: '会话历史',
    element: <ConversationListPage />,
  },
];
