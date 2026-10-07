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
import { Navigate, Route, Routes } from 'react-router-dom';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';
import { DashboardLayout } from '@/components/layout/DashboardLayout';
import { LoginPage } from '@/pages/auth/LoginPage';
import { DashboardPage } from '@/pages/dashboard/DashboardPage';
import { PlaceholderPage } from '@/pages/PlaceholderPage';
import { configRoutes } from '@/routes/config-routes';
import { dataRoutes } from '@/routes/data-routes';
import { chatRoutes } from '@/routes/chat-routes';
import { opsRoutes } from '@/routes/ops-routes';
import { mgmtRoutes } from '@/routes/mgmt-routes';
import { governanceRoutes } from '@/routes/governance-routes';
import { getAppRoutePlugins, getMenuGroups } from '@/routes/app-plugin';
import type { RouteItem } from '@/routes/app-plugin';

const toRelative = (path: string): string => path.replace(/^\//, '');

/**
 * 应用路由
 */
export function AppRouter() {
  // 插件在应用渲染前注册，渲染时合并社区路由与插件注入的商业路由
  const allRoutes: RouteItem[] = [
    ...configRoutes,
    ...dataRoutes,
    ...chatRoutes,
    ...opsRoutes,
    ...mgmtRoutes,
    ...governanceRoutes,
    ...getAppRoutePlugins().flatMap((p) => p.routes),
  ];

  const routeMap = new Map<string, RouteItem>(
    allRoutes.map((r) => [r.path, r])
  );

  const menuPaths = getMenuGroups()
    .flatMap((g) => g.children)
    .map((c) => c.path);

  const renderedRoutes = menuPaths
    .filter((p) => p !== '/dashboard')
    .map((p) => {
      const route = routeMap.get(p);
      return (
        <Route
          key={p}
          path={toRelative(p)}
          element={
            route ? route.element : <PlaceholderPage menu={p} />
          }
        />
      );
    });

  // 非菜单子路由按 path 去重：插件路由声明在后，与菜单路由的 routeMap 语义一致（插件覆盖社区同路径页面）
  const subRoutes = [...new Map(
    allRoutes
      .filter((r) => !menuPaths.includes(r.path))
      .map((r) => [r.path, r] as const)
  ).values()]
    .map((r) => (
      <Route
        key={r.path}
        path={toRelative(r.path)}
        element={r.element}
      />
    ));

  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<DashboardLayout />}>
          <Route index element={<Navigate to="/dashboard" replace />} />
          <Route path="dashboard" element={<DashboardPage />} />
          {renderedRoutes}
          {subRoutes}
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Route>
      </Route>
    </Routes>
  );
}
