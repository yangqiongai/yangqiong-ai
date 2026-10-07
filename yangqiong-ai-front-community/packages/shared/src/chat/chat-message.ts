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
import type { McpAppsUiExtractResult } from '../components/McpAppRenderer';

/**
 * 已加入对话的引用文件（发送时读取内容注入消息）
 */
export interface ChatFileRef {
  path: string;

  name: string;
}

/**
 * 对话消息（流式渲染通用结构）
 */
export interface ChatMessage {
  id: string;

  role: 'user' | 'assistant';

  content: string;

  thinking?: string;

  streaming?: boolean;

  createTime?: string;

  /**
   * 工具结果携带的 MCP Apps UI 声明与载荷（无声明时为空）
   */
  mcpApp?: McpAppsUiExtractResult;

  /**
   * 本轮对话Agent产生的新增文件（工作台场景）
   */
  producedFiles?: ChatFileRef[];

  /**
   * 用户消息附带的图片（dataURL，气泡内缩略图展示）
   */
  images?: string[];
}

/**
 * 生成本地消息ID
 * @return
 */
export const newId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

/**
 * 复制文本到剪贴板（Clipboard API不可用或失败时降级为隐藏textarea方案）
 * @param text
 * @return
 */
export const copyToClipboard = async (text: string): Promise<void> => {
  if (navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return;
    } catch {
      // 继续尝试降级方案
    }
  }
  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  try {
    if (!document.execCommand('copy')) {
      throw new Error('execCommand copy failed');
    }
  } finally {
    document.body.removeChild(textarea);
  }
};
