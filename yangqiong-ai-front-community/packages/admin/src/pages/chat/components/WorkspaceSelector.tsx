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
import React from 'react';
import { Button, Select, Space, Tag, Tooltip, Typography } from 'antd';
import { SettingOutlined } from '@ant-design/icons';
import { WORKSPACE_TYPE_META } from '@yangqiong/shared/chat';
import type { WorkspaceItem } from '@yangqiong/shared';

const { Text } = Typography;

/**
 * 工作区类型标签色
 */
const WORKSPACE_TYPE_COLOR: Record<string, string> = {
  SERVER: 'blue',
  CONNECTOR: 'green',
  BROWSER: 'purple',
};

interface WorkspaceSelectorProps {
  /** 雪花ID后端序列化为字符串，全程保持字符串避免精度丢失 */
  value?: string;
  onChange?: (value: string | undefined) => void;
  workspaces: WorkspaceItem[];
  loading?: boolean;
  onManage?: () => void;

  /**
   * 下拉浮层类名（深色主题页面传入）
   */
  dropdownClassName?: string;
}

/**
 * 工作区下拉选择器
 */
export const WorkspaceSelector: React.FC<WorkspaceSelectorProps> = ({
  value,
  onChange,
  workspaces,
  loading,
  onManage,
  dropdownClassName,
}) => {
  const renderLabel = (item: WorkspaceItem) => (
    <Space size={6}>
      <span>{item.name}</span>
      {item.valid === false && <Tag color="red" style={{ marginInlineEnd: 0 }}>失效</Tag>}
      {/* 云端存储为默认类型不显示标识，仅本地连接/浏览器直连展示以示区分 */}
      {item.type && item.type !== 'SERVER' && (
        <Tag color={WORKSPACE_TYPE_COLOR[item.type]} style={{ marginInlineEnd: 0 }}>
          {WORKSPACE_TYPE_META[item.type].text}
        </Tag>
      )}
    </Space>
  );

  return (
    <Space size={8}>
      <Select<string>
        value={value}
        onChange={onChange}
        loading={loading}
        placeholder="选择工作区"
        style={{ minWidth: 200, maxWidth: 320 }}
        dropdownClassName={dropdownClassName}
        options={workspaces.map((item) => ({ value: item.id, label: renderLabel(item) }))}
        optionRender={(option) => {
          const item = workspaces.find((w) => w.id === option.value);
          if (!item) {
            return <span>{option.label}</span>;
          }
          return (
            <Space size={6}>
              {renderLabel(item)}
              <Tooltip title={item.rootPath}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  {item.rootPath}
                </Text>
              </Tooltip>
            </Space>
          );
        }}
        notFoundContent="暂无工作区"
        allowClear
      />
      <Button icon={<SettingOutlined />} onClick={onManage}>
        管理
      </Button>
    </Space>
  );
};
