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
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MenuListPage } from './MenuListPage';
import type { MenuTreeNode } from '@yangqiong/shared';

const { treeMock, sortMock, deleteMock } = vi.hoisted(() => ({
  treeMock: { impl: vi.fn() },
  sortMock: { impl: vi.fn() },
  deleteMock: { impl: vi.fn() },
}));

vi.mock('@/services', () => ({
  api: {
    system: {
      menu: {
        tree: (...args: unknown[]) => treeMock.impl(...args),
        my: vi.fn(),
        create: vi.fn(),
        update: vi.fn(),
        delete: (...args: unknown[]) => deleteMock.impl(...args),
        sort: (...args: unknown[]) => sortMock.impl(...args),
        scopes: vi.fn(),
        saveScopes: vi.fn(),
      },
    },
  },
}));

const treeFixture: MenuTreeNode[] = [
  {
    id: 1101,
    menuKey: 'system',
    menuType: 'GROUP',
    name: '系统管理',
    icon: 'AuditOutlined',
    sortOrder: 10,
    visible: 1,
    status: 1,
    children: [
      {
        id: 1233,
        menuKey: 'users',
        parentId: 1101,
        menuType: 'PAGE',
        name: '用户管理',
        path: '/users',
        icon: 'UserOutlined',
        sortOrder: 1,
        visible: 1,
        status: 1,
        children: [],
      },
      {
        id: 1234,
        menuKey: 'menus',
        parentId: 1101,
        menuType: 'PAGE',
        name: '菜单管理',
        path: '/menus',
        icon: 'ProfileOutlined',
        sortOrder: 2,
        visible: 0,
        status: 1,
        children: [],
      },
    ],
  },
];

const cleanups: Array<() => void> = [];

/**
 * 等待react-query解析完成(其通知调度走setTimeout宏任务)
 */
const flush = async () => {
  for (let round = 0; round < 10; round++) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
};

/**
 * 渲染菜单管理页（QueryClient 包裹）
 * @return
 */
const renderPage = (): HTMLElement => {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <MenuListPage />
      </QueryClientProvider>,
    );
  });
  cleanups.push(() => {
    act(() => root.unmount());
    container.remove();
  });
  return container;
};

beforeEach(() => {
  treeMock.impl.mockReset();
  sortMock.impl.mockReset();
  deleteMock.impl.mockReset();
  treeMock.impl.mockResolvedValue(treeFixture);
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
});

afterEach(() => {
  while (cleanups.length) {
    cleanups.pop()?.();
  }
  vi.restoreAllMocks();
});

describe('MenuListPage', () => {
  it('默认拉取社区管理端菜单树并渲染分组与页面行', async () => {
    const container = renderPage();
    await flush();
    expect(treeMock.impl).toHaveBeenCalledWith('community-admin');
    const text = container.textContent ?? '';
    expect(text).toContain('系统管理');
    expect(text).toContain('用户管理');
    expect(text).toContain('menus');
  });

  it('管理树渲染隐藏与停用状态标记', async () => {
    const container = renderPage();
    await flush();
    const text = container.textContent ?? '';
    // 菜单管理行 visible=0 应出现隐藏标记
    expect(text).toContain('隐藏');
    expect(text).not.toContain('停用');
  });

  it('社区版不渲染端Tab，固定只拉取社区管理端菜单', async () => {
    const container = renderPage();
    await flush();
    // 不应存在任何 Tabs 结构
    expect(container.querySelectorAll('.ant-tabs-tab')).toHaveLength(0);
    // 仅以社区管理端 appCode 拉取
    expect(treeMock.impl).toHaveBeenCalledTimes(1);
    expect(treeMock.impl).toHaveBeenCalledWith('community-admin');
  });
});
