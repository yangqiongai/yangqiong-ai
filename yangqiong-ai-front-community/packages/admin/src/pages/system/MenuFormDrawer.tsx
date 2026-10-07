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
import { useEffect, useMemo } from 'react';
import {
  App,
  Drawer,
  Form,
  Input,
  InputNumber,
  Select,
  Switch,
  TreeSelect,
} from 'antd';
import React from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import type { MenuSavePayload, MenuTreeNode, MenuType } from '@yangqiong/shared';
import { MENU_ICON_MAP, resolveMenuIcon } from '@/components/layout/menu-icons';

const MENU_TYPE_OPTIONS: { label: string; value: MenuType }[] = [
  { label: '分组', value: 'GROUP' },
  { label: '页面', value: 'PAGE' },
  { label: '外链', value: 'LINK' },
];

interface MenuFormDrawerProps {
  open: boolean;
  appCode: string;
  /** 编辑目标，null 表示新增 */
  record: MenuTreeNode | null;
  /** 新增子菜单时预填的上级分组 ID */
  presetParentId?: number;
  /** 当前端的菜单树，用于上级菜单选择 */
  tree: MenuTreeNode[];
  onClose: () => void;
}

/**
 * 菜单编辑抽屉
 */
export const MenuFormDrawer: React.FC<MenuFormDrawerProps> = ({
  open,
  appCode,
  record,
  presetParentId,
  tree,
  onClose,
}) => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [form] = Form.useForm();
  const menuType = Form.useWatch('menuType', form);

  useEffect(() => {
    if (!open) {
      return;
    }
    if (record) {
      // 先重置再回填，避免上一条记录的字段值残留
      form.resetFields();
      form.setFieldsValue({
        parentId: record.parentId ?? 0,
        menuType: record.menuType,
        menuKey: record.menuKey,
        name: record.name,
        path: record.path ?? undefined,
        icon: record.icon ?? undefined,
        sortOrder: record.sortOrder,
        visible: (record.visible ?? 1) === 1,
        status: (record.status ?? 1) === 1,
        featureKey: record.featureKey ?? undefined,
        permissionCode: record.permissionCode ?? undefined,
      });
    } else {
      form.resetFields();
      form.setFieldsValue({
        parentId: presetParentId ?? 0,
        menuType: 'PAGE',
        visible: true,
        status: true,
      });
    }
  }, [open, record, presetParentId, form]);

  // 上级菜单仅允许选择分组节点
  const parentOptions = useMemo(() => {
    const options: { title: string; value: number }[] = [{ title: '根节点', value: 0 }];
    const walk = (nodes: MenuTreeNode[]) => {
      for (const node of nodes) {
        if (node.menuType === 'GROUP') {
          options.push({ title: node.name ?? node.menuKey ?? '', value: node.id ?? 0 });
          walk(node.children ?? []);
        }
      }
    };
    walk(tree);
    return options;
  }, [tree]);

  const iconOptions = useMemo(
    () =>
      Object.keys(MENU_ICON_MAP).map((name) => ({
        value: name,
        label: (
          <span className="inline-flex items-center gap-2">
            {React.createElement(resolveMenuIcon(name))}
            <span>{name}</span>
          </span>
        ),
      })),
    [],
  );

  const saveMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) => {
      const payload: MenuSavePayload = {
        appCode,
        parentId: (values.parentId as number) ?? 0,
        menuKey: values.menuKey as string,
        menuType: values.menuType as MenuType,
        name: values.name as string,
        path: (values.path as string) || null,
        icon: (values.icon as string) || null,
        sortOrder: values.sortOrder as number | undefined,
        visible: values.visible ? 1 : 0,
        status: values.status ? 1 : 0,
        permissionCode: (values.permissionCode as string) || null,
        featureKey: (values.featureKey as string) || null,
      };
      return record && record.id != null
        ? api.system.menu.update(record.id, payload)
        : api.system.menu.create(payload);
    },
    onSuccess: () => {
      message.success(record ? '菜单更新成功' : '菜单创建成功');
      void queryClient.invalidateQueries({ queryKey: ['system-menu-tree'] });
      onClose();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '保存失败'),
  });

  return (
    <Drawer
      title={record ? `编辑菜单：${record.name ?? ''}` : '新增菜单'}
      width={480}
      open={open}
      onClose={onClose}
      destroyOnClose
      extra={
        <a
          onClick={() =>
            form.validateFields().then((values) => saveMutation.mutate(values))
          }
        >
          保存
        </a>
      }
    >
      <Form form={form} layout="vertical">
        <Form.Item name="parentId" label="上级菜单" rules={[{ required: true }]}>
          <TreeSelect treeData={parentOptions} treeDefaultExpandAll={false} />
        </Form.Item>
        <Form.Item name="menuType" label="类型" rules={[{ required: true, message: '请选择类型' }]}>
          <Select options={MENU_TYPE_OPTIONS} />
        </Form.Item>
        <Form.Item
          name="menuKey"
          label="语义键"
          rules={[
            { required: true, message: '请输入语义键' },
            { pattern: /^[a-zA-Z][a-zA-Z0-9._-]*$/, message: '字母开头，可含数字/点/下划线/中划线' },
          ]}
          extra="端内唯一，如 agent-chat"
        >
          <Input placeholder="agent-chat" />
        </Form.Item>
        <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
          <Input placeholder="菜单显示名称" />
        </Form.Item>
        <Form.Item
          name="path"
          label="路由路径"
          rules={[
            {
              required: menuType === 'PAGE' || menuType === 'LINK',
              message: '页面与外链菜单必须配置路径',
            },
          ]}
        >
          <Input placeholder="/agents" />
        </Form.Item>
        <Form.Item name="icon" label="图标" extra="未注册名称将回落默认图标">
          <Select options={iconOptions} allowClear showSearch optionFilterProp="value" />
        </Form.Item>
        <Form.Item name="sortOrder" label="排序号" extra="同级按排序号升序展示">
          <InputNumber min={0} style={{ width: '100%' }} />
        </Form.Item>
        <Form.Item name="visible" label="全局显示" valuePropName="checked">
          <Switch />
        </Form.Item>
        <Form.Item name="status" label="启用" valuePropName="checked" extra="停用等同菜单不存在">
          <Switch />
        </Form.Item>
        <Form.Item
          name="featureKey"
          label="配置开关键"
          extra="如 ai.agent.evolution.enabled，缺失或非 false 视为开启"
        >
          <Input placeholder="ai.agent.evolution.enabled" />
        </Form.Item>
        <Form.Item
          name="permissionCode"
          label="权限编码"
          extra="如 system:menu:manage，企业版按角色权限过滤；留空不过滤"
        >
          <Input placeholder="system:menu:manage" />
        </Form.Item>
      </Form>
    </Drawer>
  );
};

export default MenuFormDrawer;
