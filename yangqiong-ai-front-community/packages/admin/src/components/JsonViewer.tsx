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
import { useMemo } from 'react';
import { Typography } from 'antd';

const { Text } = Typography;

/**
 * JSON 高亮格式化展示组件
 * 内部依赖正则做语法着色，无第三方插件依赖
 */
export const JsonViewer: React.FC<{ value?: string }> = ({ value }) => {
  const text = useMemo(() => {
    if (!value) return '';
    try {
      return JSON.stringify(JSON.parse(value), null, 2);
    } catch {
      return value;
    }
  }, [value]);

  const tokens = useMemo(() => {
    if (!text) return [];
    const regex =
      /("(?:\\.|[^"\\])*")(\s*:)?|\b(true|false|null)\b|(-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?)|([{}[\],])|(\s+)/g;
    const result: { text: string; color?: string }[] = [];
    let match: RegExpExecArray | null;
    let lastIndex = 0;
    while ((match = regex.exec(text)) !== null) {
      if (match.index > lastIndex) {
        result.push({ text: text.slice(lastIndex, match.index) });
      }
      if (match[6]) {
        // 空白，忽略
      } else if (match[1]) {
        // 字符串，带冒号的后缀视为键
        result.push({ text: match[1], color: match[2] ? '#1677ff' : '#52c41a' });
      } else if (match[3]) {
        result.push({ text: match[3], color: '#eb2f96' });
      } else if (match[4]) {
        result.push({ text: match[4], color: '#d46b08' });
      } else if (match[5]) {
        result.push({ text: match[5], color: '#8c8c8c' });
      }
      lastIndex = match.index + match[0].length;
    }
    if (lastIndex < text.length) {
      result.push({ text: text.slice(lastIndex) });
    }
    return result;
  }, [text]);

  if (!text) return <Text type="secondary">-</Text>;
  return (
    <pre
      style={{
        background: '#f5f5f5',
        padding: 8,
        borderRadius: 6,
        maxHeight: 240,
        overflow: 'auto',
        margin: 0,
        fontSize: 12,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-all',
      }}
    >
      {tokens.map((t, i) => (
        <span key={i} style={t.color ? { color: t.color } : undefined}>
          {t.text}
        </span>
      ))}
    </pre>
  );
};
