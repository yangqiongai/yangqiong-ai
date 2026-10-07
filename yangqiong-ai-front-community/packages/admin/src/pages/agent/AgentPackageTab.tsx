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
import { useState } from 'react';
import { App, Button, Card, Input, Space, Statistic, Typography } from 'antd';
import { DownloadOutlined, ImportOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { api } from '@/services';
import { tryPrettyJson } from '@yangqiong/shared';
import type { ConfigPackageImportResult } from '@yangqiong/shared';

/**
 * Agent 配置包
 */
export const AgentPackageTab: React.FC<{ agentCode: string }> = ({ agentCode }) => {
  const { message } = App.useApp();

  const [exportAll, setExportAll] = useState(false);
  const [exportResult, setExportResult] = useState('');
  const [importText, setImportText] = useState('');
  const [importResult, setImportResult] = useState<ConfigPackageImportResult | null>(null);

  const exportMutation = useMutation({
    mutationFn: () =>
      api.registry.configPackage.export(exportAll ? undefined : [agentCode]),
    onSuccess: (data) => {
      setExportResult(tryPrettyJson(data) ?? data);
      message.success('配置包导出成功');
    },
    onError: () => message.error('导出失败，请确认注册中心已启用'),
  });

  const importMutation = useMutation({
    mutationFn: () => api.registry.configPackage.import(importText),
    onSuccess: (data) => {
      setImportResult(data);
      message.success('配置包导入完成');
    },
    onError: () => message.error('导入失败，请检查配置包 JSON'),
  });

  const downloadPackage = () => {
    const blob = new Blob([exportResult], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `agent-config-package-${exportAll ? 'all' : agentCode}.json`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap' }}>
      <Card
        size="small"
        title="导出配置包"
        style={{ flex: 1, minWidth: 320 }}
        extra={
          <Button
            size="small"
            type="primary"
            icon={<DownloadOutlined />}
            loading={exportMutation.isPending}
            onClick={() => exportMutation.mutate()}
          >
            导出
          </Button>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <Button
            size="small"
            type={exportAll ? 'primary' : 'default'}
            onClick={() => setExportAll((v) => !v)}
          >
            {exportAll ? '范围：全部 Agent' : '范围：当前 Agent'}
          </Button>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            导出定义、版本快照与环境档的合并包（含哈希），用于跨环境迁移；configHash 重复时导入自动跳过。
          </Typography.Text>
          {exportResult && (
            <>
              <Input.TextArea
                rows={10}
                readOnly
                style={{ fontFamily: 'monospace', fontSize: 12 }}
                value={exportResult}
              />
              <Button size="small" onClick={downloadPackage}>
                下载 JSON 文件
              </Button>
            </>
          )}
        </Space>
      </Card>
      <Card
        size="small"
        title="导入配置包"
        style={{ flex: 1, minWidth: 320 }}
        extra={
          <Button
            size="small"
            type="primary"
            icon={<ImportOutlined />}
            loading={importMutation.isPending}
            disabled={!importText.trim()}
            onClick={() => importMutation.mutate()}
          >
            导入
          </Button>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size={8}>
          <Input.TextArea
            rows={10}
            style={{ fontFamily: 'monospace', fontSize: 12 }}
            placeholder="粘贴配置包 JSON 内容"
            value={importText}
            onChange={(e) => setImportText(e.target.value)}
          />
          {importResult && (
            <Space size={24}>
              <Statistic title="新导入" value={importResult.imported ?? 0} valueStyle={{ fontSize: 20 }} />
              <Statistic title="跳过(重复)" value={importResult.skipped ?? 0} valueStyle={{ fontSize: 20 }} />
            </Space>
          )}
        </Space>
      </Card>
    </div>
  );
};
