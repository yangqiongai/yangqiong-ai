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
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import type { Components } from 'react-markdown';
import type { McpAppsUiExtractResult } from '@yangqiong/shared';

/**
 * 对话消息（流式渲染通用结构）
 */
export interface ChatMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  thinking?: string;
  streaming?: boolean;

  /**
   * 工具结果携带的 MCP Apps UI 声明与载荷（无声明时为空）
   */
  mcpApp?: McpAppsUiExtractResult;

  /**
   * 本轮对话产生的工作区文件（回复结束后快照对比得出，点击可预览）
   */
  producedFiles?: { path: string; name: string }[];
}

/**
 * 生成本地消息ID
 */
export const newId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;

const mdComponents: Components = {
  p: (props) => <p style={{ margin: '4px 0' }} {...props} />,
  ul: (props) => <ul style={{ margin: '4px 0', paddingLeft: 20 }} {...props} />,
  ol: (props) => <ol style={{ margin: '4px 0', paddingLeft: 20 }} {...props} />,
  li: (props) => <li style={{ margin: '2px 0' }} {...props} />,
  h1: (props) => <h1 style={{ margin: '8px 0 4px', fontSize: 16 }} {...props} />,
  h2: (props) => <h2 style={{ margin: '8px 0 4px', fontSize: 15 }} {...props} />,
  h3: (props) => <h3 style={{ margin: '6px 0 4px', fontSize: 14 }} {...props} />,
  blockquote: (props) => (
    <blockquote style={{ margin: '4px 0', borderLeft: '3px solid #d9d9d9', paddingLeft: 12, color: '#8c8c8c' }} {...props} />
  ),
  hr: () => <hr style={{ border: 'none', borderTop: '1px solid #f0f0f0', margin: '8px 0' }} />,
  a: (props) => <a style={{ color: '#1677ff' }} target="_blank" rel="noreferrer" {...props} />,
  img: (props) => (
    <img
      style={{ maxWidth: '100%', borderRadius: 6, margin: '4px 0', display: 'block' }}
      alt={props.alt ?? '图片'}
      {...props}
    />
  ),
  table: (props) => (
    <div style={{ margin: '8px 0', overflowX: 'auto' }}>
      <table style={{ borderCollapse: 'collapse', fontSize: 12 }} {...props} />
    </div>
  ),
  th: (props) => (
    <th style={{ border: '1px solid #f0f0f0', background: '#fafafa', padding: '4px 8px', textAlign: 'left' }} {...props} />
  ),
  td: (props) => (
    <td style={{ border: '1px solid #f0f0f0', padding: '4px 8px', verticalAlign: 'top' }} {...props} />
  ),
  code: ({ className, children, ...rest }) => {
    const isBlock = /language-/.test(className ?? '');
    if (isBlock) {
      return (
        <code style={{ fontFamily: 'SFMono-Regular, Consolas, monospace', fontSize: 12 }} className={className} {...rest}>
          {children}
        </code>
      );
    }
    return (
      <code style={{ background: '#f5f5f5', borderRadius: 4, padding: '1px 4px', fontFamily: 'SFMono-Regular, Consolas, monospace', fontSize: 12 }} {...rest}>
        {children}
      </code>
    );
  },
  pre: (props) => (
    <pre
      style={{
        margin: '8px 0',
        overflowX: 'auto',
        background: '#f6f8fa',
        color: '#24292f',
        border: '1px solid #e5e7eb',
        borderRadius: 6,
        padding: 12,
        fontSize: 12,
        lineHeight: 1.6,
      }}
      {...props}
    />
  ),
};

/**
 * 对话消息 Markdown 渲染
 */
export const MarkdownContent: React.FC<{ content: string }> = ({ content }) => (
  <div style={{ fontSize: 14, lineHeight: 1.7, wordBreak: 'break-word' }}>
    <ReactMarkdown remarkPlugins={[remarkGfm]} components={mdComponents}>
      {content}
    </ReactMarkdown>
  </div>
);

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
