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
import type { ComponentType, ReactNode } from 'react';
import type { MenuConfigGroup } from '@/components/layout/menu-config';
import { MENU_GROUPS } from '@/components/layout/menu-config';
import type { AgentResourceOptions } from '@yangqiong/shared/agent';

/**
 * 路由项
 */
export interface RouteItem {
  path: string;
  key: string;
  label: string;
  element: ReactNode;
}

/**
 * Agent 详情页扩展 Tab 项（企业版插件注入，按 agentCode 渲染内容）
 */
export interface AgentDetailTabItem {
  key: string;
  label: string;
  render: (agentCode: string) => ReactNode;
}

/**
 * Agent 详情页扩展操作项（企业版插件注入，覆盖社区版同名锁定操作）
 */
export interface AgentDetailActionItem {
  /**
   * 操作标识（governance=纳入治理）
   */
  key: string;

  /**
   * 操作渲染组件（按 agentCode 渲染，覆盖社区版锁定按钮）
   */
  render: ComponentType<{ agentCode: string }>;
}

/**
 * Agent 能力挂载表单扩展字段属性（字段名需携带 prefix 前缀对齐表单结构）
 */
export interface AgentMountFieldProps {
  /**
   * 字段名前缀（编辑抽屉内为 ['config']，能力挂载 Tab 直挂为空）
   */
  prefix?: string[];

  /**
   * 挂载资源选项（模型/知识库等下拉单源）
   */
  options: AgentResourceOptions;

  /**
   * 是否禁用
   */
  disabled?: boolean;
}

/**
 * 应用路由插件（商业版通过该扩展点注入路由与菜单，社区构建物物理不含商业页面）
 */
export interface AppRoutePlugin {
  name: string;

  /**
   * 插件路由（与社区路由合并渲染，不在菜单中的路由作为子页面兜底渲染）
   */
  routes: RouteItem[];

  /**
   * 菜单分组（追加在社区菜单之后）
   */
  menuGroups?: MenuConfigGroup[];

  /**
   * 侧边栏库菜单端标识（企业基座声明 enterprise-admin，未声明默认 community-admin）
   */
  menuAppCode?: string;

  /**
   * 租户切换器（平台管理员顶部展示，社区版无实现）
   */
  tenantSwitcher?: ComponentType;

  /**
   * Agent 详情页扩展 Tab（追加在基座 Tab 之后，同 key 覆盖社区版锁定 Tab；社区版无注入则不展示）
   */
  agentDetailTabs?: AgentDetailTabItem[];

  /**
   * Agent 详情页扩展操作（覆盖社区版锁定按钮，社区版无注入则保持锁定态）
   */
  agentDetailActions?: AgentDetailActionItem[];

  /**
   * Agent 能力挂载表单扩展字段组件（渲染在重排模型之后，社区版无注入则不展示）
   */
  agentMountFields?: ComponentType<AgentMountFieldProps>[];

  /**
   * 运行回放页用量下钻抽屉（企业版注入，社区版无注入则隐藏下钻入口）
   */
  traceUsageDrill?: ComponentType<{ taskId: string; onClose: () => void }>;

  /**
   * 登录后全局挂件（企业版注入，如授权宽限提示；社区版无注入则不渲染）
   */
  globalWidgets?: ComponentType[];
}

const plugins: AppRoutePlugin[] = [];

/**
 * 注册应用路由插件（须在应用首次渲染前调用）
 * @param plugin
 */
export function registerAppRoutes(plugin: AppRoutePlugin): void {
  plugins.push(plugin);
}

/**
 * 获取已注册的路由插件
 * @return
 */
export function getAppRoutePlugins(): AppRoutePlugin[] {
  return [...plugins];
}

/**
 * 获取侧边栏与菜单管理使用的库菜单端标识（企业基座声明 enterprise-admin，未声明默认 community-admin）
 * @return
 */
export function getMenuAppCode(): string {
  return getAppRoutePlugins().find((p) => p.menuAppCode)?.menuAppCode ?? 'community-admin';
}

/**
 * 汇总社区菜单与插件注入的菜单分组
 * @return
 */
export function getMenuGroups(): MenuConfigGroup[] {
  const extra = getAppRoutePlugins().flatMap((p) => p.menuGroups ?? []);
  return [...MENU_GROUPS, ...extra];
}
