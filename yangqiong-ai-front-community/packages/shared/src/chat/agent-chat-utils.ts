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
import { extractMcpAppsUiDeclaration } from '../components/McpAppRenderer';
import { newId, type ChatMessage } from './chat-message';

/**
 * agentConfig 中的对话输出行为配置
 */
export interface AgentOutputConfig {
  reasoning?: { enabled?: boolean; effort?: string; showThinking?: boolean };

  streamOutput?: boolean;
}

/**
 * 解析 agentConfig JSON（容错，非法或空返回空对象）
 * @param text
 * @return
 */
export const parseAgentConfig = (text?: string): AgentOutputConfig => {
  if (!text) {
    return {};
  }
  try {
    const obj = JSON.parse(text);
    return obj && typeof obj === 'object' && !Array.isArray(obj) ? (obj as AgentOutputConfig) : {};
  } catch {
    return {};
  }
};

/**
 * 折叠消息中的文件引用块：仅保留「（引用文件：xxx）」注记，隐藏内嵌的文件内容，
 * 与实时发送时的引用注记样式保持一致（会话恢复历史时同样生效）
 * @param content
 * @return
 */
export const collapseFileRefs = (content: string): string =>
  content.replace(/<file path="([^"]*)">[\s\S]*?<\/file>/g, '（引用文件：$1）');

/**
 * 会话导出JSON结构（后端 exportSession json 格式）
 */
export interface SessionExportData {
  sessionId?: string;

  title?: string;

  agentCode?: string;

  messages?: { role?: string; content?: string }[];
}

/**
 * 解析会话导出JSON并重建消息数组（过滤非对话角色，assistant消息附带MCP UI声明提取）
 * @param json
 * @return
 */
export const restoreMessagesFromExport = (json: string): ChatMessage[] => {
  let parsed: SessionExportData;
  try {
    parsed = JSON.parse(json || '{}') as SessionExportData;
  } catch {
    throw new Error('会话数据格式错误，无法恢复');
  }
  return (parsed.messages ?? [])
    .filter((m) => m.role === 'user' || m.role === 'assistant')
    .map((m) => {
      const content = String(m.content ?? '');
      return {
        id: newId(),
        role: m.role as 'user' | 'assistant',
        content,
        mcpApp:
          m.role === 'assistant'
            ? (extractMcpAppsUiDeclaration(content) ?? undefined)
            : undefined,
      };
    });
};

/**
 * 将文本内容以 JSON 文件形式下载（Blob 方案，导出后立即释放对象URL）
 * @param text
 * @param fileName
 */
export const downloadJsonFile = (text: string, fileName: string): void => {
  const blob = new Blob([text], { type: 'application/json;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName.endsWith('.json') ? fileName : `${fileName}.json`;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
};
