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
  ApiOutlined,
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  BookOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  CodeOutlined,
  CrownOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  GlobalOutlined,
  HistoryOutlined,
  LinkOutlined,
  MessageOutlined,
  NotificationOutlined,
  ProfileOutlined,
  ReadOutlined,
  RobotOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SafetyOutlined,
  ShareAltOutlined,
  SolutionOutlined,
  StarOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  TrophyOutlined,
  UserOutlined,
  WalletOutlined,
} from '@ant-design/icons';

/**
 * 图标注册表：icon 字符串（菜单库 icon 字段）→ antd 图标组件
 */
export const MENU_ICON_MAP: Record<string, ComponentType> = {
  ApiOutlined,
  ApartmentOutlined,
  AppstoreOutlined,
  AuditOutlined,
  BarChartOutlined,
  BookOutlined,
  CloudServerOutlined,
  ClusterOutlined,
  CodeOutlined,
  CrownOutlined,
  DashboardOutlined,
  DatabaseOutlined,
  DeploymentUnitOutlined,
  ExperimentOutlined,
  FileSearchOutlined,
  GlobalOutlined,
  HistoryOutlined,
  LinkOutlined,
  MessageOutlined,
  NotificationOutlined,
  ProfileOutlined,
  ReadOutlined,
  RobotOutlined,
  RocketOutlined,
  SafetyCertificateOutlined,
  SafetyOutlined,
  ShareAltOutlined,
  SolutionOutlined,
  StarOutlined,
  TeamOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  TrophyOutlined,
  UserOutlined,
  WalletOutlined,
};

/**
 * 图标默认回落：未注册的 icon 字符串静默回落，不报错
 */
export const FALLBACK_MENU_ICON: ComponentType = AppstoreOutlined;

/**
 * 按图标名解析菜单图标组件
 * @param icon
 * @return
 */
export function resolveMenuIcon(icon?: string | null): ComponentType {
  return (icon && MENU_ICON_MAP[icon]) || FALLBACK_MENU_ICON;
}
