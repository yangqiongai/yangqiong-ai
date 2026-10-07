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
import * as AntdIcons from '@ant-design/icons';
import { Button, Popover, Tooltip, Upload, message } from 'antd';
import React from 'react';

/**
 * Agent图标预设（name对应@ant-design/icons导出名，from/to为渐变起止色）
 */
export interface AgentIconPreset {
  name: string;
  from: string;
  to: string;
}

/**
 * 默认图标编码
 */
export const DEFAULT_AGENT_ICON = 'RobotOutlined';

/**
 * Agent图标预设清单（agent/技术/数据语义的彩色渐变图标）
 */
export const AGENT_ICON_PRESETS: AgentIconPreset[] = [
  { name: 'RobotOutlined', from: '#6366f1', to: '#8b5cf6' },
  { name: 'AndroidOutlined', from: '#22c55e', to: '#16a34a' },
  { name: 'ThunderboltOutlined', from: '#f59e0b', to: '#f97316' },
  { name: 'MessageOutlined', from: '#3b82f6', to: '#06b6d4' },
  { name: 'CommentOutlined', from: '#8b5cf6', to: '#d946ef' },
  { name: 'SendOutlined', from: '#3b82f6', to: '#8b5cf6' },
  { name: 'CustomerServiceOutlined', from: '#ec4899', to: '#f43f5e' },
  { name: 'ToolOutlined', from: '#64748b', to: '#334155' },
  { name: 'SettingOutlined', from: '#64748b', to: '#0ea5e9' },
  { name: 'ControlOutlined', from: '#475569', to: '#0ea5e9' },
  { name: 'SlidersOutlined', from: '#0ea5e9', to: '#6366f1' },
  { name: 'ApiOutlined', from: '#334155', to: '#64748b' },
  { name: 'CodeOutlined', from: '#0f172a', to: '#475569' },
  { name: 'BugOutlined', from: '#ef4444', to: '#be123c' },
  { name: 'BookOutlined', from: '#a855f7', to: '#6366f1' },
  { name: 'ReadOutlined', from: '#14b8a6', to: '#0d9488' },
  { name: 'FileTextOutlined', from: '#6b7280', to: '#4b5563' },
  { name: 'FileSearchOutlined', from: '#0ea5e9', to: '#14b8a6' },
  { name: 'ProfileOutlined', from: '#64748b', to: '#8b5cf6' },
  { name: 'SolutionOutlined', from: '#6366f1', to: '#14b8a6' },
  { name: 'SearchOutlined', from: '#0ea5e9', to: '#6366f1' },
  { name: 'BulbOutlined', from: '#fbbf24', to: '#f59e0b' },
  { name: 'ExperimentOutlined', from: '#d946ef', to: '#6366f1' },
  { name: 'GlobalOutlined', from: '#06b6d4', to: '#3b82f6' },
  { name: 'CloudOutlined', from: '#38bdf8', to: '#3b82f6' },
  { name: 'CloudServerOutlined', from: '#0284c7', to: '#4f46e5' },
  { name: 'DatabaseOutlined', from: '#f43f5e', to: '#f97316' },
  { name: 'HddOutlined', from: '#475569', to: '#0ea5e9' },
  { name: 'BarChartOutlined', from: '#f59e0b', to: '#ef4444' },
  { name: 'LineChartOutlined', from: '#10b981', to: '#06b6d4' },
  { name: 'PieChartOutlined', from: '#f97316', to: '#f43f5e' },
  { name: 'FundOutlined', from: '#ec4899', to: '#8b5cf6' },
  { name: 'ApartmentOutlined', from: '#6366f1', to: '#0ea5e9' },
  { name: 'ClusterOutlined', from: '#8b5cf6', to: '#ec4899' },
  { name: 'DeploymentUnitOutlined', from: '#0ea5e9', to: '#8b5cf6' },
  { name: 'PartitionOutlined', from: '#8b5cf6', to: '#06b6d4' },
  { name: 'NodeIndexOutlined', from: '#6366f1', to: '#ec4899' },
  { name: 'BranchesOutlined', from: '#10b981', to: '#6366f1' },
  { name: 'ShareAltOutlined', from: '#06b6d4', to: '#10b981' },
  { name: 'GatewayOutlined', from: '#334155', to: '#6366f1' },
  { name: 'InteractionOutlined', from: '#3b82f6', to: '#14b8a6' },
  { name: 'FunnelPlotOutlined', from: '#f97316', to: '#8b5cf6' },
  { name: 'PlayCircleOutlined', from: '#f43f5e', to: '#f59e0b' },
  { name: 'SafetyOutlined', from: '#10b981', to: '#059669' },
  { name: 'SafetyCertificateOutlined', from: '#16a34a', to: '#65a30d' },
  { name: 'RocketOutlined', from: '#ef4444', to: '#f97316' },
];

const PRESET_MAP = new Map(AGENT_ICON_PRESETS.map((p) => [p.name, p]));

const FALLBACK_PRESET: AgentIconPreset = { name: DEFAULT_AGENT_ICON, from: '#6366f1', to: '#8b5cf6' };

const ICON_WRAPPER_BASE: React.CSSProperties = {
  borderRadius: 8,
  display: 'inline-flex',
  alignItems: 'center',
  justifyContent: 'center',
  flexShrink: 0,
  overflow: 'hidden',
  boxShadow: '0 1px 2px rgba(0, 0, 0, 0.12)',
};

/**
 * 判断是否为base64图片图标（上传的自定义图标）
 * @param icon
 * @return
 */
export function isImageIcon(icon?: string): boolean {
  return !!icon && icon.startsWith('data:image');
}

/**
 * Agent图标渲染（预置：圆角渐变底+白色图标；上传：base64图片按尺寸裁剪展示）
 * @param icon
 * @param size
 * @return
 */
export function AgentIconView({ icon, size = 28 }: { icon?: string; size?: number }) {
  const wrapperStyle: React.CSSProperties = { ...ICON_WRAPPER_BASE, width: size, height: size };
  if (isImageIcon(icon)) {
    return <img src={icon} alt="agent" style={{ ...wrapperStyle, objectFit: 'cover' }} />;
  }
  const name = icon && PRESET_MAP.has(icon) ? icon : DEFAULT_AGENT_ICON;
  const preset = PRESET_MAP.get(name) ?? FALLBACK_PRESET;
  const icons = AntdIcons as unknown as Record<string, React.ComponentType<{ style?: React.CSSProperties }>>;
  const IconComp = icons[name] ?? AntdIcons.RobotOutlined;
  return (
    <span
      style={{
        ...wrapperStyle,
        background: `linear-gradient(135deg, ${preset.from}, ${preset.to})`,
      }}
    >
      <IconComp style={{ color: '#fff', fontSize: Math.round(size * 0.55) }} />
    </span>
  );
}

/**
 * 允许上传的图标格式（扩展名与MIME白名单）
 */
const ALLOWED_ICON_EXTS = ['png', 'jpg', 'jpeg', 'svg', 'gif', 'bmp'];

const ALLOWED_ICON_TYPES = ['image/png', 'image/jpeg', 'image/svg+xml', 'image/gif', 'image/bmp'];

/**
 * 读取本地图片为base64（前端预览用，保存时由后端统一压缩到80px）
 * @param file
 * @param onLoad
 */
function readImageAsDataUrl(file: File, onLoad: (dataUrl: string) => void) {
  const ext = file.name.split('.').pop()?.toLowerCase() ?? '';
  if (!ALLOWED_ICON_EXTS.includes(ext) || !ALLOWED_ICON_TYPES.includes(file.type)) {
    message.error('仅支持 png / jpg / jpeg / svg / gif / bmp 格式');
    return;
  }
  if (file.size > 2 * 1024 * 1024) {
    message.error('图片不能超过 2MB');
    return;
  }
  const reader = new FileReader();
  reader.onload = () => onLoad(String(reader.result));
  reader.readAsDataURL(file);
}

/**
 * Agent图标选择器（预置网格点选 + 本地上传 + 恢复默认）
 * @param value
 * @param onChange
 * @return
 */
export function AgentIconPicker({
  value,
  onChange,
}: {
  value?: string;
  onChange?: (value?: string) => void;
}) {
  return (
    <Popover
      trigger="click"
      placement="bottomLeft"
      content={
        <div style={{ width: 336 }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6 }}>
            {AGENT_ICON_PRESETS.map((preset) => (
              <Tooltip key={preset.name} title={preset.name.replace(/Outlined$/, '')}>
                <span
                  onClick={() => onChange?.(preset.name)}
                  style={{
                    cursor: 'pointer',
                    padding: 2,
                    borderRadius: 10,
                    lineHeight: 0,
                    border: value === preset.name ? '2px solid #1677ff' : '2px solid transparent',
                  }}
                >
                  <AgentIconView icon={preset.name} size={32} />
                </span>
              </Tooltip>
            ))}
          </div>
          <div style={{ marginTop: 8, display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Upload
              accept=".png,.jpg,.jpeg,.svg,.gif,.bmp"
              showUploadList={false}
              beforeUpload={(file) => {
                readImageAsDataUrl(file, (dataUrl) => onChange?.(dataUrl));
                return false;
              }}
            >
              <Button size="small">上传图片</Button>
            </Upload>
            <Button size="small" onClick={() => onChange?.(undefined)}>
              恢复默认
            </Button>
          </div>
        </div>
      }
    >
      <span
        style={{
          display: 'inline-flex',
          alignItems: 'center',
          gap: 8,
          cursor: 'pointer',
          padding: '4px 8px',
          border: '1px solid #d9d9d9',
          borderRadius: 6,
          background: '#fff',
        }}
      >
        <AgentIconView icon={value} size={30} />
        <span style={{ fontSize: 12, color: '#666' }}>点击更换图标</span>
      </span>
    </Popover>
  );
}
