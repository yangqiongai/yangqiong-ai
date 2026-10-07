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
import type { ComponentType } from 'react';
import {
  ApartmentOutlined,
  ApiOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  BookOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  ExperimentOutlined,
  FolderOpenOutlined,
  GlobalOutlined,
  HistoryOutlined,
  MessageOutlined,
  ProfileOutlined,
  RobotOutlined,
  SafetyOutlined,
  SearchOutlined,
  StarOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  TrophyOutlined,
  UserOutlined,
} from '@ant-design/icons';

/**
 * 菜单项
 */
export interface MenuConfigItem {
  key: string;
  label: string;
  path: string;
  icon?: ComponentType;
}

/**
 * 菜单分组
 */
export interface MenuConfigGroup {
  key: string;
  label: string;
  icon?: ComponentType;
  children: MenuConfigItem[];
}

/**
 * 管理后台菜单分组配置（商业版菜单由插件经 registerAppRoutes 注入）
 */
export const MENU_GROUPS: MenuConfigGroup[] = [
  {
    key: 'overview',
    label: '总览',
    icon: DashboardOutlined,
    children: [
      { key: 'dashboard', label: '工作台', path: '/dashboard', icon: DashboardOutlined },
    ],
  },
  {
    key: 'chat',
    label: '运行中心',
    icon: MessageOutlined,
    children: [
      { key: 'agent-chat', label: 'Agent 对话', path: '/chat', icon: MessageOutlined },
      { key: 'conversations', label: '会话管理', path: '/conversations', icon: HistoryOutlined },
      { key: 'workbench', label: '本地工作台', path: '/workbench', icon: FolderOpenOutlined },
    ],
  },
  {
    key: 'model-agent',
    label: '模型与 Agent',
    icon: RobotOutlined,
    children: [
      { key: 'models', label: '模型管理', path: '/models', icon: ThunderboltOutlined },
      { key: 'agents', label: 'Agent 管理', path: '/agents', icon: AppstoreOutlined },
      { key: 'agent-test', label: 'Agent 测试', path: '/agent-test', icon: ExperimentOutlined },
    ],
  },
  {
    key: 'tool-skill',
    label: '工具与技能',
    icon: ToolOutlined,
    children: [
      { key: 'tools', label: '工具管理', path: '/tools', icon: ToolOutlined },
      { key: 'mcp', label: 'MCP 接入', path: '/mcp', icon: DeploymentUnitOutlined },
      { key: 'skills', label: '技能管理', path: '/skills', icon: StarOutlined },
      { key: 'skill-usage', label: '技能用量', path: '/skill-usage', icon: BarChartOutlined },
      { key: 'tool-usage', label: '工具用量', path: '/tool-usage', icon: BarChartOutlined },
    ],
  },
  {
    key: 'knowledge',
    label: '知识中心',
    icon: BookOutlined,
    children: [
      { key: 'knowledge-bases', label: '知识库', path: '/knowledge-bases', icon: BookOutlined },
      { key: 'data-sources', label: '数据源', path: '/data-sources', icon: DatabaseOutlined },
      { key: 'vector-stores', label: '向量存储', path: '/vector-stores', icon: ClusterOutlined },
      { key: 'knowledge-bases-rag', label: 'RAG 检索', path: '/knowledge-bases/rag', icon: SearchOutlined },
    ],
  },
  {
    key: 'orchestration',
    label: '编排引擎',
    icon: ApartmentOutlined,
    children: [
      { key: 'workflows', label: '工作流', path: '/workflows', icon: ApartmentOutlined },
    ],
  },
  {
    key: 'evaluation-evolution',
    label: '评测与进化',
    icon: TrophyOutlined,
    children: [
      { key: 'evaluations', label: 'Agent 评测', path: '/evaluations', icon: TrophyOutlined },
    ],
  },
  {
    key: 'governance',
    label: '治理中心',
    icon: SafetyOutlined,
    children: [
      { key: 'trace-runs', label: '运行回放', path: '/trace-runs', icon: HistoryOutlined },
      { key: 'memory', label: '记忆检索', path: '/memory', icon: CloudServerOutlined },
      { key: 'agent-memory', label: '记忆条目', path: '/agent-memory', icon: DatabaseOutlined },
    ],
  },
  {
    key: 'open-capability',
    label: '开放能力',
    icon: GlobalOutlined,
    children: [
      { key: 'open-capabilities', label: '开放能力', path: '/open-capabilities', icon: GlobalOutlined },
      { key: 'connectors', label: '连接器', path: '/connectors', icon: ApiOutlined },
      { key: 'mcp-expose', label: 'MCP 出口', path: '/mcp-expose', icon: ApiOutlined },
      { key: 'a2a-card', label: 'A2A 卡片', path: '/a2a-card', icon: DeploymentUnitOutlined },
    ],
  },
  {
    key: 'system',
    label: '系统管理',
    icon: AuditOutlined,
    children: [
      { key: 'users', label: '用户管理', path: '/users', icon: UserOutlined },
      { key: 'menus', label: '菜单管理', path: '/menus', icon: ProfileOutlined },
    ],
  },
];
