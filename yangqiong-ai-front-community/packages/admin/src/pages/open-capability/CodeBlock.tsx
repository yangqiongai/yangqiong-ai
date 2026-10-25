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
import { Button, message } from 'antd';
import { CopyOutlined } from '@ant-design/icons';

/**
 * 代码块（等宽字体浅灰底，复制按钮固定在文本下方）
 * @param props
 * @return
 */
const CodeBlock: React.FC<{ value: string; rows?: number }> = ({ value, rows }) => (
  <div>
    <pre
      style={{
        margin: 0,
        padding: '8px 12px',
        background: 'rgba(0, 0, 0, 0.04)',
        borderRadius: 6,
        fontSize: 12,
        lineHeight: 1.7,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        maxHeight: rows ? rows * 24 + 16 : undefined,
        overflowY: rows ? 'auto' : undefined,
        fontFamily: 'SFMono-Regular, Consolas, "Liberation Mono", Menlo, monospace',
      }}
    >
      {value}
    </pre>
    <Button
      type="text"
      size="small"
      icon={<CopyOutlined />}
      style={{ marginTop: 4 }}
      onClick={async () => {
        await navigator.clipboard.writeText(value);
        message.success('已复制');
      }}
    >
      复制
    </Button>
  </div>
);

export default CodeBlock;
