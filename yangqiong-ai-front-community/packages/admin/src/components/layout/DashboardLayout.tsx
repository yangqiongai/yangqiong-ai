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
import React, { useEffect, useMemo, useState } from 'react';
import {
  Avatar,
  Dropdown,
  Layout,
  Menu,
  Space,
  Tag,
  Typography,
  theme,
} from 'antd';
import type { MenuProps } from 'antd';
import {
  DownOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/services';
import { getAppRoutePlugins, getMenuAppCode } from '@/routes/app-plugin';
import { resolveMenuGroups } from './menu-merge';
import { MENU_GROUPS } from './menu-config';
import { useAuthStore } from '@/store/auth-store';

const { Header, Sider, Content, Footer } = Layout;
const { Text } = Typography;

export const DashboardLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const {
    token: { colorBgContainer },
  } = theme.useToken();

  const user = useAuthStore((s) => s.user);
  const tenantId = useAuthStore((s) => s.tenantId);
  const platformAdmin = useAuthStore((s) => s.platformAdmin);
  const logout = useAuthStore((s) => s.logout);

  // 库菜单优先：加载中/失败回退静态菜单全量（行为与改造前一致）
  // 端标识由插件声明（企业基座 enterprise-admin），未声明默认社区端
  const menuAppCode = getMenuAppCode();
  const { data: dbMenuTree } = useQuery({
    queryKey: ['layout-db-menu', menuAppCode],
    queryFn: () => api.system.menu.my(menuAppCode),
    staleTime: 30_000,
    retry: 1,
  });

  // 租户切换器由插件注入，社区版默认不渲染
  const TenantSwitcher = getAppRoutePlugins()
    .map((p) => p.tenantSwitcher)
    .find((c) => !!c);
  // 全局挂件由插件注入，社区版无注入则不渲染
  const globalWidgets = getAppRoutePlugins().flatMap((p) => p.globalWidgets ?? []);
  // 端分流解析菜单（社区端行为不变；企业端仅用库树，社区静态菜单一律不并入）
  const isCommunity = menuAppCode === 'community-admin';
  const pluginGroups = useMemo(
    () => getAppRoutePlugins().flatMap((p) => p.menuGroups ?? []),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    []
  );
  const extraMenuGroups = useMemo(() => [...pluginGroups, ...MENU_GROUPS], [pluginGroups]);
  const menuGroups = useMemo(
    () => resolveMenuGroups(dbMenuTree, isCommunity, extraMenuGroups, pluginGroups),
    [dbMenuTree, isCommunity, extraMenuGroups, pluginGroups]
  );

  const items = useMemo<MenuProps['items']>(
    () =>
      menuGroups.map((group) => ({
        key: group.key,
        icon: group.icon ? React.createElement(group.icon) : null,
        label: group.label,
        children: group.children.map((child) => ({
          key: child.path,
          icon: child.icon ? React.createElement(child.icon) : null,
          label: child.label,
        })),
      })),
    [menuGroups]
  );

  // 精确匹配不到时按一级路径前缀归组（如 /workflow/:name/edit 归属 /workflows 所在分组）
  const matchMenuPaths = (pathname: string): string[] => {
    const paths = menuGroups.flatMap((g) => g.children).map((c) => c.path);
    const exact = paths.filter(
      (p) => pathname === p || pathname.startsWith(`${p}/`)
    );
    if (exact.length) {
      return exact;
    }
    const firstSeg = `/${pathname.split('/')[1] ?? ''}`;
    return paths.filter((p) => firstSeg !== '/' && p.startsWith(firstSeg));
  };

  const selectedKeys = useMemo(() => {
    const matched = matchMenuPaths(location.pathname).sort(
      (a, b) => b.length - a.length
    );
    return matched.length ? [matched[0]] : [];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, menuGroups]);

  const defaultOpenKeys = useMemo(() => {
    // 默认只展开当前路由所属分组
    const matched = matchMenuPaths(location.pathname);
    const group = menuGroups.find((g) =>
      g.children.some((c) => matched.includes(c.path))
    );
    return group ? [group.key] : [];
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.pathname, menuGroups]);

  const [openKeys, setOpenKeys] = useState<string[]>(defaultOpenKeys);

  // 切换路由时自动展开所属分组（保持手风琴，仅一个分组展开）
  useEffect(() => {
    setOpenKeys(defaultOpenKeys);
  }, [defaultOpenKeys]);

  const handleOpenChange: MenuProps['onOpenChange'] = (keys) => {
    const latest = keys.find((k) => !openKeys.includes(k));
    setOpenKeys(latest ? [latest] : []);
  };

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    navigate(key);
  };

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'logout',
      icon: React.createElement(LogoutOutlined),
      label: '退出登录',
    },
  ];

  const onUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key === 'logout') {
      handleLogout();
    }
  };

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider
        collapsible
        collapsed={collapsed}
        onCollapse={setCollapsed}
        width={220}
        theme="dark"
      >
        <div
          style={{
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
          }}
        >
          <div
            style={{
              height: 56,
              flexShrink: 0,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff',
              fontWeight: 600,
              fontSize: collapsed ? 16 : 15,
              whiteSpace: 'nowrap',
              overflow: 'hidden',
            }}
          >
            {collapsed ? '泱穹' : '泱穹智能体平台'}
          </div>
          <div style={{ flex: 1, minHeight: 0, overflowY: 'auto' }}>
            <Menu
              theme="dark"
              mode="inline"
              items={items}
              selectedKeys={selectedKeys}
              openKeys={openKeys}
              onOpenChange={handleOpenChange}
              onClick={handleMenuClick}
            />
          </div>
        </div>
      </Sider>
      <Layout
        style={{ height: '100vh', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}
      >
        <Header
          style={{
            background: colorBgContainer,
            padding: '0 16px',
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            boxShadow: '0 1px 4px rgba(0,0,0,0.08)',
          }}
        >
          {React.createElement(collapsed ? MenuUnfoldOutlined : MenuFoldOutlined, {
            onClick: () => setCollapsed(!collapsed),
            style: { fontSize: 18, cursor: 'pointer' },
          })}
          <Space size="middle">
            {platformAdmin && <Tag color="blue">平台管理员</Tag>}
            {platformAdmin ? (
              TenantSwitcher && <TenantSwitcher />
            ) : (
              tenantId && <Text type="secondary">当前租户：{tenantId}</Text>
            )}
            <Dropdown
              menu={{ items: userMenuItems, onClick: onUserMenuClick }}
              placement="bottomRight"
            >
              <Space style={{ cursor: 'pointer' }}>
                <Avatar icon={React.createElement(UserOutlined)} />
                <Text>{user?.name || '未登录'}</Text>
                <DownOutlined />
              </Space>
            </Dropdown>
          </Space>
        </Header>
        <Content
          style={
            location.pathname.startsWith('/governance-dashboard')
              ? {
                  // 框架通用外边距保留，仅去掉内层 padding 白边；背景与驾驶舱画布同色避免圆角露白
                  margin: 16,
                  padding: 0,
                  background: '#0B1220',
                  borderRadius: 8,
                  flex: 1,
                  minHeight: 0,
                  overflowY: 'auto',
                }
              : location.pathname.startsWith('/dashboard')
              ? {
                  // 工作台自绘通栏背景，去掉内层 padding 避免底部白条露出
                  margin: 16,
                  padding: 0,
                  background: '#f2f4f8',
                  borderRadius: 8,
                  flex: 1,
                  minHeight: 0,
                  overflowY: 'auto',
                }
              : {
                  margin: 16,
                  padding: 24,
                  background: colorBgContainer,
                  borderRadius: 8,
                  flex: 1,
                  minHeight: 0,
                  overflowY: 'auto',
                }
          }
        >
          <Outlet />
        </Content>
        <Footer style={{ textAlign: 'center', padding: '12px 0', flexShrink: 0 }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Powered by 泱穹 AI
          </Text>
        </Footer>
        {globalWidgets.map((Widget, index) => (
          <Widget key={index} />
        ))}
      </Layout>
    </Layout>
  );
};
