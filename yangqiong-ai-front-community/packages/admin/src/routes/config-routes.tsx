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
import { Navigate } from 'react-router-dom';
import { ModelListPage } from '@/pages/model/ModelListPage';
import { AgentDefinitionPage } from '@/pages/agent/AgentDefinitionPage';
import { ToolConfigListPage } from '@/pages/tool/ToolConfigListPage';
import { McpServerListPage } from '@/pages/tool/McpServerListPage';
import { AiToolConfigPage } from '@/pages/tool/AiToolConfigPage';
import { ToolUsagePage } from '@/pages/tool/ToolUsagePage';
import { SkillListPage } from '@/pages/skill/SkillListPage';
import { SkillUsagePage } from '@/pages/skill/SkillUsagePage';
import type { RouteItem } from '@/routes/app-plugin';

/**
 * 配置类管理页面路由
 */
export const configRoutes: RouteItem[] = [
  { path: '/models', key: 'models', label: '模型管理', element: <ModelListPage /> },
  { path: '/agents', key: 'agents', label: 'Agent 管理', element: <AgentDefinitionPage /> },
  { path: '/agent-types', key: 'agent-types', label: 'Agent 类型', element: <Navigate to="/agents" replace /> },
  { path: '/tools', key: 'tools', label: '工具管理', element: <ToolConfigListPage /> },
  { path: '/mcp', key: 'mcp', label: 'MCP 接入', element: <McpServerListPage /> },
  { path: '/tools/ai-tool', key: 'tools-ai-tool', label: 'AI 工具配置', element: <AiToolConfigPage /> },
  { path: '/skills', key: 'skills', label: '技能管理', element: <SkillListPage /> },
  { path: '/skill-usage', key: 'skill-usage', label: '技能用量', element: <SkillUsagePage /> },
  { path: '/tool-usage', key: 'tool-usage', label: '工具用量', element: <ToolUsagePage /> },
];
