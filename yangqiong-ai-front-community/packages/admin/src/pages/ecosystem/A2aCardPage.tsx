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
import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Card,
  Descriptions,
  Empty,
  List,
  Select,
  Space,
  Spin,
  Switch,
  Tag,
  Typography,
  App,
} from 'antd';
import { ReloadOutlined, RobotOutlined, SafetyOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { mcpEcosystemApi } from '@/services/mcp-ecosystem-api';
import type { A2aAgentCardInfo } from '@/services/mcp-ecosystem-api';

const { Title, Text, Paragraph } = Typography;

/**
 * 美化 JSON 文本
 * @param card
 * @return
 */
const prettyJson = (card: A2aAgentCardInfo | undefined): string =>
  card ? JSON.stringify(card, null, 2) : '';

const DEFINITIONS_KEY = 'a2a-registry-definitions';

/**
 * A2A 代理卡预览
 */
export const A2aCardPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [agentCode, setAgentCode] = useState<string>('');
  const [optimisticCard, setOptimisticCard] = useState<boolean | null>(null);

  // 数据源为注册中心定义：仅注册中心内 ENABLED 且开启卡片发布的Agent才有对外卡片
  const { data: definitionsPage, isLoading: definitionsLoading } = useQuery({
    queryKey: [DEFINITIONS_KEY],
    queryFn: () => api.registry.definition.page({ pageNum: 1, pageSize: 200 }),
  });

  const definitions = useMemo(() => {
    const list = definitionsPage?.records ?? [];
    // 跨 scope 可能返回同名编码行，按 agentCode 去重避免选项 key 重复
    const seen = new Set<string>();
    return list.filter((item) => {
      if (!item.agentCode || seen.has(item.agentCode)) {
        return false;
      }
      seen.add(item.agentCode);
      return true;
    });
  }, [definitionsPage]);

  const agentOptions = useMemo(
    () =>
      definitions.map((item) => ({
        label: item.agentName ? `${item.agentName}（${item.agentCode}）` : item.agentCode,
        value: item.agentCode,
      })),
    [definitions],
  );

  const selectedDef = useMemo(
    () => definitions.find((item) => item.agentCode === agentCode),
    [definitions, agentCode],
  );

  const cardEnabled = optimisticCard ?? selectedDef?.cardEnabled === 1;
  const definitionEnabled = selectedDef?.status === 'ENABLED';

  useEffect(() => {
    if (!agentCode && agentOptions.length > 0) {
      setAgentCode(agentOptions[0].value);
    }
  }, [agentCode, agentOptions]);

  const toggleCardMutation = useMutation({
    mutationFn: (enabled: boolean) =>
      api.registry.definition.updateCardEnabled(agentCode, enabled),
    // 乐观更新：点击立即翻转，避免等待列表刷新造成"无反应"体感
    onMutate: (enabled) => {
      setOptimisticCard(enabled);
    },
    onSuccess: async (_data, enabled) => {
      message.success(enabled ? '卡片已启用，外部可获取代理卡' : '卡片已禁用，外部将无法获取代理卡');
      await queryClient.invalidateQueries({ queryKey: [DEFINITIONS_KEY] });
      setOptimisticCard(null);
    },
    onError: (err) => {
      setOptimisticCard(null);
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const {
    data: card,
    isLoading,
    isFetching,
    error,
    refetch,
  } = useQuery({
    queryKey: ['a2a-card', agentCode],
    queryFn: () => mcpEcosystemApi.card.preview(agentCode),
    // 仅注册中心启用且卡片开关打开时才拉取卡片
    enabled: !!agentCode && cardEnabled,
    retry: false,
  });

  const cardError = error instanceof Error ? error.message : '';

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        A2A 代理卡
      </Title>
      <Card styles={{ body: { paddingTop: 0 } }}>
        <Space wrap style={{ marginBottom: 16, paddingTop: 16 }}>
          <Select
            showSearch
            optionFilterProp="label"
            loading={definitionsLoading}
            value={agentCode || undefined}
            onChange={(code: string) => {
              setOptimisticCard(null);
              setAgentCode(code);
            }}
            options={agentOptions}
            placeholder="请选择注册中心 Agent"
            style={{ width: 300 }}
          />
          <Space size={8}>
            <Text type="secondary">卡片对外发布</Text>
            <Switch
              checked={cardEnabled}
              loading={toggleCardMutation.isPending}
              disabled={!selectedDef}
              onChange={(checked) => toggleCardMutation.mutate(checked)}
            />
          </Space>
          {selectedDef ? (
            <Tag color={definitionEnabled ? 'green' : 'orange'}>
              {selectedDef.status === 'ENABLED'
                ? '定义已启用'
                : selectedDef.status === 'DRAFT'
                  ? '草稿'
                  : '定义已停用'}
            </Tag>
          ) : null}
          <Button
            icon={<ReloadOutlined />}
            disabled={!agentCode || !cardEnabled}
            loading={isFetching}
            onClick={() => void refetch()}
          >
            刷新预览
          </Button>
        </Space>

        {!agentCode ? (
          <Empty description="请选择注册中心 Agent 预览代理卡" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : !definitionEnabled ? (
          <Alert
            type="info"
            showIcon
            message="Agent 定义未启用"
            description="仅注册中心中状态为启用（ENABLED）的 Agent 可对外发布 A2A 卡片，请先在注册中心完成版本发布与启用。"
          />
        ) : !cardEnabled ? (
          <Alert
            type="warning"
            showIcon
            message="卡片未启用（默认禁用）"
            description="开启上方“卡片对外发布”开关后，外部才能通过 /.well-known/agent-card.json 获取该 Agent 的代理卡。"
          />
        ) : isLoading ? (
          <Spin tip="加载代理卡中..." style={{ display: 'block', margin: '80px auto' }} />
        ) : cardError && !card ? (
          <Alert
            type="warning"
            showIcon
            message="代理卡获取失败"
            description={`${cardError}（Agent 需为启用状态、开启卡片对外发布且存在已发布版本）`}
          />
        ) : card ? (
          <Space direction="vertical" size="middle" style={{ width: '100%' }}>
            <Descriptions
              title={
                <Space>
                  <RobotOutlined />
                  <span>卡片信息</span>
                  {card.signatures?.length ? (
                    <Tag color="green" icon={<SafetyOutlined />}>
                      已签名
                    </Tag>
                  ) : (
                    <Tag>未签名</Tag>
                  )}
                </Space>
              }
              bordered
              column={2}
              size="small"
            >
              <Descriptions.Item label="名称">{card.name ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="版本">{card.version ?? '-'}</Descriptions.Item>
              <Descriptions.Item label="描述" span={2}>
                {card.description ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="服务端点" span={2}>
                {card.url ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="传输协议">
                {card.preferredTransport ?? '-'}
              </Descriptions.Item>
              <Descriptions.Item label="能力">
                <Space size={4} wrap>
                  {card.capabilities?.streaming ? <Tag color="blue">streaming</Tag> : null}
                  {card.capabilities?.pushNotifications ? (
                    <Tag color="blue">pushNotifications</Tag>
                  ) : null}
                  {!card.capabilities?.streaming && !card.capabilities?.pushNotifications
                    ? '-'
                    : null}
                </Space>
              </Descriptions.Item>
              <Descriptions.Item label="输入模式">
                {(card.defaultInputModes ?? []).join('、') || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="输出模式">
                {(card.defaultOutputModes ?? []).join('、') || '-'}
              </Descriptions.Item>
            </Descriptions>

            <Card type="inner" title="技能列表" size="small">
              <List
                size="small"
                dataSource={card.skills ?? []}
                locale={{ emptyText: '暂无技能' }}
                renderItem={(skill) => (
                  <List.Item>
                    <List.Item.Meta
                      title={skill.name ?? skill.id}
                      description={
                        <Space direction="vertical" size={2}>
                          <span>{skill.description ?? '-'}</span>
                          <span>
                            {(skill.tags ?? []).map((tag) => (
                              <Tag key={tag}>{tag}</Tag>
                            ))}
                          </span>
                        </Space>
                      }
                    />
                  </List.Item>
                )}
              />
            </Card>

            {card.signatures?.length ? (
              <Card type="inner" title="签名信息" size="small">
                <Paragraph style={{ marginBottom: 0 }}>
                  <Text strong>JWS：</Text>
                  <Text
                    code
                    style={{ wordBreak: 'break-all', fontSize: 12 }}
                  >{`${card.signatures[0].protectedHeader ?? ''}.${card.signatures[0].signature ?? ''}`}</Text>
                </Paragraph>
              </Card>
            ) : null}

            {card.publicKeyFingerprint ? (
              <Card type="inner" title="公钥指纹" size="small">
                <Paragraph style={{ marginBottom: 0 }}>
                  <Text code copyable>
                    {card.publicKeyFingerprint}
                  </Text>
                </Paragraph>
              </Card>
            ) : (
              <Paragraph type="secondary" style={{ marginBottom: 0 }}>
                后端未返回公钥指纹（签名公钥指纹下发为服务端契约预留）。
              </Paragraph>
            )}

            <Card type="inner" title="原始 JSON" size="small">
              <pre
                style={{
                  margin: 0,
                  background: '#282c34',
                  color: '#abb2bf',
                  borderRadius: 6,
                  padding: 12,
                  fontSize: 12,
                  maxHeight: 360,
                  overflow: 'auto',
                }}
              >
                {prettyJson(card)}
              </pre>
            </Card>
          </Space>
        ) : (
          <Empty description="暂无卡片数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        )}
      </Card>
    </div>
  );
};

export default A2aCardPage;
