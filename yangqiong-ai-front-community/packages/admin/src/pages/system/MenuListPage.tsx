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
import { App, Button, Card, Popconfirm, Space, Table, Tag, Tooltip, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  ReloadOutlined,
  SubnodeOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { getMenuAppCode } from '@/routes/app-plugin';
import { resolveMenuIcon } from '@/components/layout/menu-icons';
import type { MenuTreeNode } from '@yangqiong/shared';
import { MenuFormDrawer } from './MenuFormDrawer';

const { Title, Text } = Typography;

const MENU_TYPE_COLORS: Record<string, string> = {
  GROUP: 'blue',
  PAGE: 'green',
  LINK: 'purple',
};

const MENU_TYPE_LABELS: Record<string, string> = {
  GROUP: '分组',
  PAGE: '页面',
  LINK: '外链',
};

/**
 * 菜单管理
 */
interface MenuListPageProps {
  /**
   * 端标识覆盖：双 Tab 场景由外层按 Tab 传入，缺省取插件注册的菜单库端
   */
  appCode?: string;

  /**
   * 是否隐藏页面标题（外层已展示标题时传 true）
   */
  hideTitle?: boolean;
}

export const MenuListPage: React.FC<MenuListPageProps> = ({ appCode: appCodeProp, hideTitle }) => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [formOpen, setFormOpen] = useState(false);
  const [current, setCurrent] = useState<MenuTreeNode | null>(null);
  const [presetParentId, setPresetParentId] = useState<number | undefined>(undefined);

  // 渲染时取端标识：企业插件注册发生在主入口模块体内，晚于本模块顶层求值，不能在模块级缓存
  const appCode = appCodeProp ?? getMenuAppCode();

  const { data: tree = [], isLoading, isFetching } = useQuery({
    queryKey: ['system-menu-tree', appCode],
    queryFn: () => api.system.menu.tree(appCode),
  });

  const sortMutation = useMutation({
    mutationFn: ({ id, direction }: { id: number; direction: 'UP' | 'DOWN' }) =>
      api.system.menu.sort(id, direction),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['system-menu-tree'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '排序失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.system.menu.delete(id),
    onSuccess: () => {
      message.success('菜单删除成功');
      void queryClient.invalidateQueries({ queryKey: ['system-menu-tree'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '删除失败'),
  });

  const handleDelete = (record: MenuTreeNode) => {
    // 有子节点时前端预检拦截，后端仍兜底拒绝
    if (record.children && record.children.length > 0) {
      message.warning('存在子菜单，请先删除子菜单');
      return;
    }
    deleteMutation.mutate(record.id as number);
  };

  const openCreate = (parentId?: number) => {
    setCurrent(null);
    setPresetParentId(parentId);
    setFormOpen(true);
  };

  const columns: ColumnsType<MenuTreeNode> = [
    {
      title: '名称',
      dataIndex: 'name',
      width: 300,
      render: (_, record) => {
        const Icon = resolveMenuIcon(record.icon);
        return (
          <Space size="small">
            {React.createElement(Icon)}
            <span>{record.name}</span>
            <Text type="secondary" style={{ fontSize: 12 }}>
              {record.menuKey}
            </Text>
          </Space>
        );
      },
    },
    {
      title: '类型',
      dataIndex: 'menuType',
      width: 80,
      render: (v?: string) => <Tag color={MENU_TYPE_COLORS[v ?? '']}>{MENU_TYPE_LABELS[v ?? ''] ?? v}</Tag>,
    },
    { title: '路径', dataIndex: 'path', width: 190, render: (v?: string) => v ?? '-' },
    {
      title: '图标',
      dataIndex: 'icon',
      width: 200,
      render: (v?: string) => v ?? <Text type="secondary">-</Text>,
    },
    { title: '排序', dataIndex: 'sortOrder', width: 70 },
    {
      title: '显隐',
      dataIndex: 'visible',
      width: 80,
      render: (v?: number) => <Tag color={v === 1 ? 'green' : 'default'}>{v === 1 ? '显示' : '隐藏'}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 80,
      render: (v?: number) => <Tag color={v === 1 ? 'green' : 'red'}>{v === 1 ? '启用' : '停用'}</Tag>,
    },
    {
      title: 'feature_key',
      dataIndex: 'featureKey',
      width: 240,
      render: (v?: string) => v ?? <Text type="secondary">-</Text>,
    },
    {
      title: 'permission_code',
      dataIndex: 'permissionCode',
      width: 200,
      render: (v?: string) => v ?? <Text type="secondary">-</Text>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 180,
      fixed: 'right',
      render: (_, record) => (
        <Space size={2}>
          <Tooltip title="编辑">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => {
                setCurrent(record);
                setFormOpen(true);
              }}
            />
          </Tooltip>
          {record.menuType === 'GROUP' && (
            <Tooltip title="新增子菜单">
              <Button
                type="text"
                size="small"
                icon={<SubnodeOutlined />}
                onClick={() => openCreate(record.id)}
              />
            </Tooltip>
          )}
          <Tooltip title="上移">
            <Button
              type="text"
              size="small"
              icon={<ArrowUpOutlined />}
              onClick={() => sortMutation.mutate({ id: record.id as number, direction: 'UP' })}
            />
          </Tooltip>
          <Tooltip title="下移">
            <Button
              type="text"
              size="small"
              icon={<ArrowDownOutlined />}
              onClick={() => sortMutation.mutate({ id: record.id as number, direction: 'DOWN' })}
            />
          </Tooltip>
          <Popconfirm title="确认删除该菜单？" onConfirm={() => handleDelete(record)}>
            <Tooltip title="删除">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // 全部含子节点的 key，首次加载后默认展开
  const allParentKeys = useMemo(() => {
    const keys: React.Key[] = [];
    const walk = (nodes: MenuTreeNode[]) => {
      for (const node of nodes) {
        if (node.children && node.children.length > 0) {
          keys.push(node.id as number);
          walk(node.children);
        }
      }
    };
    walk(tree);
    return keys;
  }, [tree]);

  // 受控展开：初始默认全展开，之后由用户自由收起/展开
  const [expandedKeys, setExpandedKeys] = useState<React.Key[]>([]);
  useEffect(() => {
    if (tree.length > 0 && expandedKeys.length === 0) {
      setExpandedKeys(allParentKeys);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [tree, allParentKeys]);

  return (
    <div>
      {!hideTitle && (
        <Title level={4} style={{ marginBottom: 16 }}>
          菜单管理
        </Title>
      )}
      <Card styles={{ body: { paddingBottom: 0 } }} style={{ marginBottom: 16 }}>
        <Space wrap style={{ marginBottom: 16 }}>
          <Button
            icon={<ReloadOutlined />}
            onClick={() => void queryClient.invalidateQueries({ queryKey: ['system-menu-tree'] })}
          >
            刷新
          </Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => openCreate()}>
            新增菜单
          </Button>
        </Space>
      </Card>
      <Card styles={{ body: { paddingTop: 0 } }}>
        <Table<MenuTreeNode>
          rowKey="id"
          columns={columns}
          dataSource={tree}
          loading={isLoading || isFetching}
          scroll={{ x: 1620 }}
          pagination={false}
          expandedRowKeys={expandedKeys}
          onExpandedRowsChange={(keys) => setExpandedKeys([...keys])}
        />
      </Card>

      <MenuFormDrawer
        open={formOpen}
        appCode={appCode}
        record={current}
        presetParentId={presetParentId}
        tree={tree}
        onClose={() => {
          setFormOpen(false);
          setPresetParentId(undefined);
        }}
      />
    </div>
  );
};

export default MenuListPage;
