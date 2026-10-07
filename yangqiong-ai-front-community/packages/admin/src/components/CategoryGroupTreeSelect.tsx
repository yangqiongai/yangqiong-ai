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
import React, { useMemo } from 'react';
import { TreeSelect } from 'antd';
import type { CategoryTreeNode } from '@yangqiong/shared';

/**
 * 分类分组树选择器
 * @author yangqiong
 */

/** 分类分组节点的value前缀（防与业务值冲突） */
const CATEGORY_PREFIX = '__cat__:';

/** 未分类虚拟组节点的value */
const UNGROUPED_VALUE = '__ungrouped__';

/**
 * 分组树下拉的树节点结构
 */
interface TreeNodeData {
  title: string;
  value: string;
  selectable?: boolean;
  children?: TreeNodeData[];
}

/**
 * 可选项（category=所属分类树节点code，空为未分类）
 */
export interface CategoryGroupOption {
  label: string;
  value: string;
  category?: string;
}

interface CategoryGroupTreeSelectProps {
  /**
   * 分类树节点（与管理界面左侧分类树同源；为空时降级为不分组平铺）
   */
  categories?: CategoryTreeNode[];

  /**
   * 可选项列表
   */
  options: CategoryGroupOption[];

  value?: string[];

  onChange?: (v: string[] | undefined) => void;

  placeholder?: string;

  disabled?: boolean;

  loading?: boolean;

  allowClear?: boolean;
}

/**
 * 按分类树分组的TreeSelect（分类节点仅作分组不可选中，值数组只含业务code，工具/MCP/技能多选共用）
 */
export const CategoryGroupTreeSelect: React.FC<CategoryGroupTreeSelectProps> = ({
  categories,
  options,
  value,
  onChange,
  placeholder,
  disabled,
  loading,
  allowClear = true,
  ...rest
}) => {
  const treeData = useMemo<TreeNodeData[]>(() => {
    // 分类树缺失时降级：平铺所有项为一级节点（不分组）
    if (!categories?.length) {
      return options.map((o) => ({ title: o.label, value: o.value }));
    }

    // 收集分类树全部code，category不在树中的项归入未分类，避免选项丢失
    const codeSet = new Set<string>();
    const collect = (nodes?: CategoryTreeNode[]): void =>
      (nodes ?? []).forEach((n) => {
        codeSet.add(n.code);
        collect(n.children);
      });
    collect(categories);

    // 按分类code索引直接挂载的项
    const byCategory = new Map<string, CategoryGroupOption[]>();
    const ungrouped: CategoryGroupOption[] = [];
    options.forEach((o) => {
      if (o.category && codeSet.has(o.category)) {
        const list = byCategory.get(o.category) ?? [];
        list.push(o);
        byCategory.set(o.category, list);
      } else {
        ungrouped.push(o);
      }
    });

    // 分类节点title=名称(直接子项数)，子节点=该分类挂载项+嵌套子分类
    const build = (nodes?: CategoryTreeNode[]): TreeNodeData[] =>
      (nodes ?? []).map((node) => {
        const items: TreeNodeData[] = (byCategory.get(node.code) ?? []).map((o) => ({
          title: o.label,
          value: o.value,
        }));
        return {
          title: `${node.name} (${items.length})`,
          value: `${CATEGORY_PREFIX}${node.code}`,
          selectable: false,
          children: [...items, ...build(node.children)],
        };
      });

    const tree = build(categories);
    // category为空的项统一挂未分类虚拟组
    if (ungrouped.length) {
      tree.push({
        title: `未分类 (${ungrouped.length})`,
        value: UNGROUPED_VALUE,
        selectable: false,
        children: ungrouped.map((o) => ({ title: o.label, value: o.value })),
      });
    }
    return tree;
  }, [categories, options]);

  return (
    <TreeSelect
      multiple
      showSearch
      treeNodeFilterProp="title"
      treeDefaultExpandAll
      allowClear={allowClear}
      style={{ width: '100%' }}
      placeholder={placeholder}
      disabled={disabled}
      loading={loading}
      treeData={treeData}
      value={value}
      onChange={(v) => onChange?.(v)}
      {...rest}
    />
  );
};
