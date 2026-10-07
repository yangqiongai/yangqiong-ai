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
import React, { useEffect, useState } from 'react';
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Popconfirm,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { ApiOutlined, PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { mcpEcosystemApi } from '@/services/mcp-ecosystem-api';
import type { McpServerExposeInfo } from '@/services/mcp-ecosystem-api';
import { api } from '@/services';

const { Title, Text, Paragraph } = Typography;
const { TextArea } = Input;

const EXPOSE_TYPE_OPTIONS = [
  { label: '工具（TOOL）', value: 'TOOL', color: 'blue' },
  { label: '代理长任务（AGENT）', value: 'AGENT', color: 'purple' },
  { label: '能力提示词（PROMPT）', value: 'PROMPT', color: 'cyan' },
  { label: '知识资源（RESOURCE）', value: 'RESOURCE', color: 'geekblue' },
];

const EXPOSE_TYPE_COLOR: Record<string, string> = EXPOSE_TYPE_OPTIONS.reduce(
  (acc, item) => {
    acc[item.value] = item.color;
    return acc;
  },
  {} as Record<string, string>
);

interface ExposeQuery {
  page: number;
  size: number;
  exposeType?: string;
  exposeCode?: string;
}

interface ExposeFormValues {
  exposeType: string;
  exposeCode: string;
  displayName?: string;
  description?: string;
  renderAllowed: boolean;
  enabled: boolean;
  remark?: string;
}

/**
 * MCP 出口白名单管理
 */
export const McpExposePage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const [query, setQuery] = useState<ExposeQuery>({ page: 1, size: 10 });
  const [formOpen, setFormOpen] = useState(false);
  const [current, setCurrent] = useState<McpServerExposeInfo | null>(null);
  const [form] = Form.useForm<ExposeFormValues>();

  const { data: pageData, isLoading, isFetching } = useQuery({
    queryKey: ['mcp-expose-list', query],
    queryFn: () => mcpEcosystemApi.expose.page(query),
  });

  const exposeType = Form.useWatch('exposeType', form);

  const agentsQuery = useQuery({
    queryKey: ['mcp-expose-agent-options', formOpen],
    queryFn: () => api.agent.type.list({ page: 1, size: 500 }),
    enabled: formOpen && exposeType === 'AGENT',
  });

  const capabilitiesQuery = useQuery({
    queryKey: ['mcp-expose-capability-options', formOpen],
    queryFn: () => api.openCapability.list({ page: 1, size: 500 }),
    enabled: formOpen && exposeType === 'PROMPT',
  });

  const toolsQuery = useQuery({
    queryKey: ['mcp-expose-tool-options', formOpen],
    queryFn: () => mcpEcosystemApi.expose.toolOptions(),
    enabled: formOpen && exposeType === 'TOOL',
  });

  useEffect(() => {
    if (formOpen) {
      if (current) {
        // 先重置再回填，避免上一条记录的字段值残留
        form.resetFields();
        form.setFieldsValue({
          exposeType: current.exposeType,
          exposeCode: current.exposeCode,
          displayName: current.displayName,
          description: current.description,
          renderAllowed: current.renderAllowed === 1,
          enabled: current.enabled === 1,
          remark: current.remark,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({
          exposeType: 'TOOL',
          renderAllowed: true,
          enabled: true,
        });
      }
    }
  }, [formOpen, current, form]);

  const saveMutation = useMutation({
    mutationFn: (values: ExposeFormValues) =>
      mcpEcosystemApi.expose.save({
        id: current?.id,
        exposeType: values.exposeType,
        exposeCode: values.exposeCode,
        displayName: values.displayName,
        description: values.description,
        renderAllowed: values.renderAllowed ? 1 : 0,
        enabled: values.enabled ? 1 : 0,
        remark: values.remark,
      }),
    onSuccess: () => {
      message.success(current ? '白名单更新成功' : '白名单创建成功');
      void queryClient.invalidateQueries({ queryKey: ['mcp-expose-list'] });
      setFormOpen(false);
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '白名单保存失败'),
  });

  // render_allowed 无独立端点，复用保存端点按类型+编码 upsert 覆盖
  const renderToggleMutation = useMutation({
    mutationFn: ({ record, allowed }: { record: McpServerExposeInfo; allowed: boolean }) =>
      mcpEcosystemApi.expose.save({ ...record, renderAllowed: allowed ? 1 : 0 }),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['mcp-expose-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '渲染授权更新失败'),
  });

  const toggleMutation = useMutation({
    mutationFn: (id: number | undefined) => mcpEcosystemApi.expose.toggle(id!),
    onSuccess: () => {
      message.success('状态切换成功');
      void queryClient.invalidateQueries({ queryKey: ['mcp-expose-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '状态切换失败'),
  });

  const deleteMutation = useMutation({
    mutationFn: (id: number | undefined) => mcpEcosystemApi.expose.remove(id!),
    onSuccess: () => {
      message.success('白名单删除成功');
      void queryClient.invalidateQueries({ queryKey: ['mcp-expose-list'] });
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '白名单删除失败'),
  });

  const handleFormOk = async () => {
    try {
      const values = await form.validateFields();
      saveMutation.mutate(values);
    } catch {
      // 校验失败由表单提示
    }
  };

  const columns: ColumnsType<McpServerExposeInfo> = [
    {
      title: '暴露类型',
      dataIndex: 'exposeType',
      width: 150,
      render: (v?: string) => (v ? <Tag color={EXPOSE_TYPE_COLOR[v]}>{v}</Tag> : '-'),
    },
    { title: '暴露编码', dataIndex: 'exposeCode', width: 200, ellipsis: true },
    { title: '显示名称', dataIndex: 'displayName', width: 180, ellipsis: true },
    { title: '描述', dataIndex: 'description', ellipsis: true },
    {
      title: 'Apps 渲染',
      dataIndex: 'renderAllowed',
      width: 110,
      render: (v: number | undefined, record) => (
        <Switch
          size="small"
          checked={v === 1}
          loading={renderToggleMutation.isPending}
          onChange={(checked) =>
            renderToggleMutation.mutate({ record, allowed: checked })
          }
        />
      ),
    },
    {
      title: '启用',
      dataIndex: 'enabled',
      width: 90,
      render: (v: number | undefined, record) => (
        <Switch
          size="small"
          checked={v === 1}
          loading={toggleMutation.isPending}
          onChange={() => toggleMutation.mutate(record.id)}
        />
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 140,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Button
            type="link"
            size="small"
            onClick={() => {
              setCurrent(record);
              setFormOpen(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除该白名单？" onConfirm={() => deleteMutation.mutate(record.id)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  // MCP 端点路径由服务端配置决定，默认 /mcp
  const mcpEndpoint = `${window.location.origin}/mcp`;

  return (
    <div>
      <Title level={4} style={{ marginBottom: 16 }}>
        MCP 出口白名单
      </Title>
      <Card styles={{ body: { paddingTop: 0 } }}>
        <Space wrap style={{ marginBottom: 16, paddingTop: 16 }}>
          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={() => {
              setCurrent(null);
              setFormOpen(true);
            }}
          >
            新增白名单
          </Button>
          <Button
            icon={<ReloadOutlined />}
            onClick={() =>
              void queryClient.invalidateQueries({ queryKey: ['mcp-expose-list'] })
            }
          >
            刷新
          </Button>
          <Select
            allowClear
            placeholder="暴露类型筛选"
            style={{ width: 180 }}
            options={EXPOSE_TYPE_OPTIONS}
            onChange={(v?: string) => setQuery((q) => ({ ...q, exposeType: v, page: 1 }))}
          />
          <Input.Search
            allowClear
            placeholder="暴露编码筛选"
            style={{ width: 220 }}
            onSearch={(v) => setQuery((q) => ({ ...q, exposeCode: v || undefined, page: 1 }))}
          />
        </Space>
        <Table<McpServerExposeInfo>
          rowKey={(record) => String(record.id ?? `${record.exposeType}-${record.exposeCode}`)}
          columns={columns}
          dataSource={pageData?.list}
          loading={isLoading || isFetching}
          scroll={{ x: 1100 }}
          pagination={{
            current: pageData?.page ?? query.page,
            pageSize: pageData?.size ?? query.size,
            total: pageData?.total ?? 0,
            showSizeChanger: true,
            showTotal: (t) => `共 ${t} 条`,
            onChange: (page, size) => setQuery((q) => ({ ...q, page, size })),
          }}
        />
      </Card>

      <Card
        title={
          <Space>
            <ApiOutlined />
            <span>Inspector 连接指引</span>
          </Space>
        }
        style={{ marginTop: 16 }}
      >
        <Space direction="vertical" size="small" style={{ width: '100%' }}>
          <Paragraph style={{ marginBottom: 0 }}>
            <Text strong>MCP 端点 URL：</Text>
            <Text code copyable style={{ marginInlineStart: 8, color: '#1677ff' }}>
              {mcpEndpoint}
            </Text>
          </Paragraph>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            在 MCP Inspector 中选择 Streamable HTTP 传输并填入上述端点，即可发现白名单内已启用的工具、代理长任务、能力提示词与知识资源；端点路径以服务端 MCP 配置为准（默认 /mcp）。
          </Paragraph>
          <Paragraph type="secondary" style={{ marginBottom: 0 }}>
            凭证申请：MCP 调用凭证由平台管理员统一发放，请联系管理员提交使用方信息与所需暴露资源清单，审批后开通账号并使用平台 Token 作为 Bearer 凭证接入。
          </Paragraph>
        </Space>
      </Card>

      <Modal
        title={current ? '编辑白名单' : '新增白名单'}
        open={formOpen}
        onOk={handleFormOk}
        onCancel={() => setFormOpen(false)}
        confirmLoading={saveMutation.isPending}
        forceRender
        maskClosable={false}
        width={620}
      >
        <Form form={form} layout="vertical" preserve={false}>
          <Form.Item
            name="exposeType"
            label="暴露类型"
            rules={[{ required: true, message: '请选择暴露类型' }]}
          >
            <Select
              options={EXPOSE_TYPE_OPTIONS}
              placeholder="请选择暴露类型"
              onChange={() => form.setFieldValue('exposeCode', undefined)}
            />
          </Form.Item>
          <Form.Item
            name="exposeCode"
            label="暴露编码"
            rules={[{ required: true, message: '请选择暴露编码' }]}
            extra={
              exposeType === 'AGENT' || exposeType === 'PROMPT' || exposeType === 'TOOL'
                ? undefined
                : '知识资源类型需填写资源来源键；代理、能力、工具类型可直接下拉选择'
            }
          >
            {exposeType === 'AGENT' ? (
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="请选择代理"
                loading={agentsQuery.isFetching}
                options={(agentsQuery.data?.list ?? []).map((a) => ({
                  value: a.typeCode,
                  label: `${a.typeName} (${a.typeCode})`,
                }))}
              />
            ) : exposeType === 'PROMPT' ? (
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="请选择能力"
                loading={capabilitiesQuery.isFetching}
                options={(capabilitiesQuery.data?.list ?? []).map((c) => ({
                  value: c.code,
                  label: `${c.name || c.code} (${c.code})`,
                }))}
              />
            ) : exposeType === 'TOOL' ? (
              <Select
                showSearch
                optionFilterProp="label"
                placeholder="请选择平台运行时工具"
                loading={toolsQuery.isFetching}
                options={(toolsQuery.data ?? []).map((t) => ({
                  value: t.name,
                  label: `${t.name}${t.description ? `：${t.description}` : ''}`,
                }))}
              />
            ) : (
              <Input placeholder="资源来源键" />
            )}
          </Form.Item>
          <Form.Item name="displayName" label="对外显示名称">
            <Input placeholder="请输入对外显示名称" />
          </Form.Item>
          <Form.Item
            name="description"
            label="对外描述"
            extra="描述内可携带 [ui:form] / [ui:chart] / [ui:card] 标记指定 MCP Apps 渲染模板，缺省回落 card"
          >
            <TextArea placeholder="请输入对外描述" autoSize={{ minRows: 2, maxRows: 4 }} />
          </Form.Item>
          <Form.Item
            name="renderAllowed"
            label="允许 Apps 渲染"
            valuePropName="checked"
            extra="开启后工具结果携带 MCP Apps UI 声明（SEP-1865 预留）"
          >
            <Switch />
          </Form.Item>
          <Form.Item name="enabled" label="启用" valuePropName="checked">
            <Switch />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <TextArea placeholder="请输入备注" autoSize={{ minRows: 2, maxRows: 4 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default McpExposePage;
