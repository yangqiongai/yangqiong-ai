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
import { FileOutlined, FolderOutlined } from '@ant-design/icons';

/**
 * 常见扩展名强调色（未命中回退灰蓝）
 */
const EXT_ACCENT: Record<string, string> = {
  ts: '#3178C6',
  tsx: '#3178C6',
  js: '#D9A514',
  jsx: '#D9A514',
  json: '#F59E0B',
  md: '#4F6BFF',
  txt: '#64748B',
  log: '#94A3B8',
  html: '#F0652F',
  css: '#38BDF8',
  scss: '#F472B6',
  less: '#38BDF8',
  vue: '#34D399',
  py: '#3B82F6',
  java: '#F89820',
  go: '#22D3EE',
  rs: '#F97316',
  c: '#8B5CF6',
  cpp: '#8B5CF6',
  sql: '#8B5CF6',
  yml: '#22D3EE',
  yaml: '#22D3EE',
  xml: '#F59E0B',
  sh: '#10B981',
  bat: '#10B981',
  png: '#F472B6',
  jpg: '#F472B6',
  jpeg: '#F472B6',
  gif: '#F472B6',
  svg: '#34D399',
  webp: '#F472B6',
  ico: '#F59E0B',
  pdf: '#EF4444',
  xls: '#10B981',
  xlsx: '#10B981',
  csv: '#10B981',
  doc: '#3B82F6',
  docx: '#3B82F6',
  ppt: '#FB923C',
  pptx: '#FB923C',
  zip: '#F97316',
  rar: '#F97316',
  '7z': '#F97316',
  gz: '#F97316',
  tar: '#F97316',
};

/**
 * 按文件名取类型强调色（无扩展名回退灰蓝）
 * @param name
 * @return
 */
export const fileAccent = (name: string): string => {
  const dot = name.lastIndexOf('.');
  if (dot === -1 || dot === name.length - 1) {
    return '#64748B';
  }
  return EXT_ACCENT[name.slice(dot + 1).toLowerCase()] ?? '#64748B';
};

interface FileTypeIconProps {
  /**
   * 文件名（按扩展名取强调色）
   */
  name: string;

  /**
   * 是否目录
   */
  dir?: boolean;
}

/**
 * 文件类型彩色图标（目录琥珀金，文件按扩展名着色）
 */
export const FileTypeIcon: React.FC<FileTypeIconProps> = ({ name, dir }) =>
  dir ? (
    <FolderOutlined style={{ color: '#F5A623', fontSize: 14 }} />
  ) : (
    <FileOutlined style={{ color: fileAccent(name), fontSize: 14 }} />
  );
