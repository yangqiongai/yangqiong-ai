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
import React, { useState } from 'react';
import {
  Alert,
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
  Tabs,
  Tag,
  Typography,
} from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import type { ColumnsType, TablePaginationConfig } from 'antd/es/table';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { formatDate } from '@yangqiong/shared';
import type {
  ConnectorCredential,
  ConnectorDescriptor,
  ConnectorField,
  ConnectorInstance,
  ConnectorToolDefinition,
  ProviderCatalogItem,
} from '@yangqiong/shared';

const PROVIDERS_KEY = 'connector-providers';
const CREDENTIALS_KEY = 'connector-credentials';
const INSTANCES_KEY = 'connector-instances';
const PAGE_SIZE = 20;
const STATUS_ENABLED = 'ENABLED';

interface CredentialFormValues {
  providerCode: string;

  name: string;

  extraFields?: Record<string, string>;
}

interface InstanceFormValues {
  instanceCode: string;

  providerCode: string;

  name: string;

  agentCode?: string;

  credentialId?: string;

  enabled: boolean;

  extraFields?: Record<string, string>;
}

/**
 * 解析JSON对象（失败返回空对象）
 */
const parseJson = (json?: string): Record<string, string> => {
  if (!json) {
    return {};
  }
  try {
    const parsed = JSON.parse(json);
    return parsed && typeof parsed === 'object' ? (parsed as Record<string, string>) : {};
  } catch {
    return {};
  }
};

/**
 * 提供商下拉选项
 */
const providerOptions = (providers: ProviderCatalogItem[] | undefined) =>
  (providers ?? []).map((item) => ({
    value: item.providerCode ?? '',
    label: item.descriptor?.displayName ?? item.providerCode ?? '',
  }));

/**
 * 状态标签
 */
const statusTag = (status?: string) =>
  status === STATUS_ENABLED ? <Tag color="green">启用</Tag> : <Tag>停用</Tag>;

/**
 * 凭证掩码信息渲染
 */
const renderMasked = (maskedJson?: string) => {
  if (!maskedJson) {
    return '-';
  }
  const masked = parseJson(maskedJson);
  const entries = Object.entries(masked);
  if (entries.length === 0) {
    return '-';
  }
  return (
    <Space size={[4, 4]} wrap>
      {entries.map(([key, value]) => (
        <Tag key={key}>
          {key}: {value}
        </Tag>
      ))}
    </Space>
  );
};

/**
 * 按提供商描述渲染动态字段（凭证/配置通用）
 * @param fields 字段声明
 * @param secretHint 编辑态敏感字段提示
 * @param initialValues 初始值
 * @return 表单项
 */
const renderDescriptorFields = (
  fields: ConnectorField[] | undefined,
  secretHint?: string,
  initialValues?: Record<string, string>,
) =>
  (fields ?? []).map((field) => (
    <Form.Item
      key={field.name}
      name={['extraFields', field.name as string]}
      label={field.label}
      initialValue={field.name ? initialValues?.[field.name] : undefined}
      rules={field.required ? [{ required: true, message: `请输入${field.label}` }] : undefined}
      extra={field.secret && secretHint ? secretHint : field.placeholder || undefined}
      preserve={false}
    >
      {field.secret ? (
        <Input.Password
          placeholder={field.placeholder || `请输入${field.label}`}
          autoComplete="new-password"
        />
      ) : (
        <Input placeholder={field.placeholder || `请输入${field.label}`} />
      )}
    </Form.Item>
  ));

/**
 * 连接器管理
 */
export const ConnectorPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();

  const [instancePage, setInstancePage] = useState(1);
  const [instanceProviderFilter, setInstanceProviderFilter] = useState<string>();
  const [credentialPage, setCredentialPage] = useState(1);
  const [credentialProviderFilter, setCredentialProviderFilter] = useState<string>();

  const [instanceModalOpen, setInstanceModalOpen] = useState(false);
  const [editingInstance, setEditingInstance] = useState<ConnectorInstance | null>(null);
  const [credentialModalOpen, setCredentialModalOpen] = useState(false);
  const [editingCredential, setEditingCredential] = useState<ConnectorCredential | null>(null);
  const [toolsInstance, setToolsInstance] = useState<ConnectorInstance | null>(null);
  const [callbackInstance, setCallbackInstance] = useState<ConnectorInstance | null>(null);

  const [instanceForm] = Form.useForm<InstanceFormValues>();
  const [credentialForm] = Form.useForm<CredentialFormValues>();

  const providersQuery = useQuery({
    queryKey: [PROVIDERS_KEY],
    queryFn: () => api.connector.providers(),
  });
  const providerMap = new Map<string, ConnectorDescriptor | undefined>(
    (providersQuery.data ?? []).map((item) => [item.providerCode ?? '', item.descriptor]),
  );

  const instancesQuery = useQuery({
    queryKey: [INSTANCES_KEY, instancePage, instanceProviderFilter],
    queryFn: () =>
      api.connector.instances.page({
        pageNum: instancePage,
        pageSize: PAGE_SIZE,
        providerCode: instanceProviderFilter || undefined,
      }),
  });

  const credentialsQuery = useQuery({
    queryKey: [CREDENTIALS_KEY, credentialPage, credentialProviderFilter],
    queryFn: () =>
      api.connector.credentials.page({
        pageNum: credentialPage,
        pageSize: PAGE_SIZE,
        providerCode: credentialProviderFilter || undefined,
      }),
  });

  const credentialOptionsQuery = useQuery({
    queryKey: [CREDENTIALS_KEY, 'options'],
    queryFn: () => api.connector.credentials.page({ pageNum: 1, pageSize: 500 }),
  });

  const watchedInstanceProvider = Form.useWatch('providerCode', instanceForm);
  const watchedCredentialProvider = Form.useWatch('providerCode', credentialForm);
  const instanceDescriptor = providerMap.get(watchedInstanceProvider ?? '');
  const credentialDescriptor = providerMap.get(watchedCredentialProvider ?? '');

  const invalidateInstances = () => {
    queryClient.invalidateQueries({ queryKey: [INSTANCES_KEY] });
  };

  const invalidateCredentials = () => {
    queryClient.invalidateQueries({ queryKey: [CREDENTIALS_KEY] });
  };

  const saveInstanceMutation = useMutation({
    mutationFn: (values: InstanceFormValues) =>
      api.connector.instances.save({
        id: editingInstance?.id,
        version: editingInstance?.version,
        instanceCode: values.instanceCode,
        providerCode: values.providerCode,
        name: values.name,
        agentCode: values.agentCode?.trim() || undefined,
        credentialId: values.credentialId || undefined,
        configJson: JSON.stringify(values.extraFields ?? {}),
        status: editingInstance ? undefined : values.enabled ? STATUS_ENABLED : 'DISABLED',
      }),
    onSuccess: () => {
      message.success('实例已保存');
      closeInstanceModal();
      invalidateInstances();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const deleteInstanceMutation = useMutation({
    mutationFn: (id: string) => api.connector.instances.remove(id),
    onSuccess: () => {
      message.success('删除成功');
      invalidateInstances();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const statusMutation = useMutation({
    mutationFn: (record: ConnectorInstance) =>
      api.connector.instances.changeStatus(record.id!, record.status !== STATUS_ENABLED),
    onSuccess: () => {
      message.success('状态已更新');
      invalidateInstances();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '更新失败');
    },
  });

  const saveCredentialMutation = useMutation({
    mutationFn: (values: CredentialFormValues) => {
      const extra = values.extraFields ?? {};
      const hasContent = Object.values(extra).some((v) => v && String(v).trim() !== '');
      if (!editingCredential && !hasContent) {
        return Promise.reject(new Error('请填写凭证内容'));
      }
      return api.connector.credentials.save({
        id: editingCredential?.id,
        providerCode: values.providerCode,
        name: values.name,
        credentialJson: hasContent ? JSON.stringify(extra) : undefined,
      });
    },
    onSuccess: () => {
      message.success('凭证已保存');
      closeCredentialModal();
      invalidateCredentials();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '保存失败');
    },
  });

  const deleteCredentialMutation = useMutation({
    mutationFn: (id: string) => api.connector.credentials.remove(id),
    onSuccess: () => {
      message.success('删除成功');
      invalidateCredentials();
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '删除失败');
    },
  });

  const testCredentialMutation = useMutation({
    mutationFn: (id: string) => api.connector.credentials.test(id),
    onSuccess: (reason) => {
      if (reason) {
        message.error(`连通性测试失败：${reason}`);
      } else {
        message.success('连通性测试通过');
      }
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '测试失败');
    },
  });

  const closeInstanceModal = () => {
    setInstanceModalOpen(false);
    setEditingInstance(null);
  };

  const closeCredentialModal = () => {
    setCredentialModalOpen(false);
    setEditingCredential(null);
  };

  const openCreateInstance = () => {
    setEditingInstance(null);
    instanceForm.resetFields();
    setInstanceModalOpen(true);
  };

  const openEditInstance = (record: ConnectorInstance) => {
    setEditingInstance(record);
    instanceForm.resetFields();
    instanceForm.setFieldsValue({
      instanceCode: record.instanceCode,
      providerCode: record.providerCode,
      name: record.name,
      agentCode: record.agentCode,
      credentialId: record.credentialId ?? undefined,
      enabled: record.status === STATUS_ENABLED,
      extraFields: parseJson(record.configJson),
    });
    setInstanceModalOpen(true);
  };

  const openCreateCredential = () => {
    setEditingCredential(null);
    credentialForm.resetFields();
    setCredentialModalOpen(true);
  };

  const openEditCredential = (record: ConnectorCredential) => {
    setEditingCredential(record);
    credentialForm.resetFields();
    credentialForm.setFieldsValue({
      providerCode: record.providerCode,
      name: record.name,
    });
    setCredentialModalOpen(true);
  };

  const handleInstanceSubmit = async () => {
    const values = await instanceForm.validateFields();
    saveInstanceMutation.mutate(values);
  };

  const handleCredentialSubmit = async () => {
    const values = await credentialForm.validateFields();
    saveCredentialMutation.mutate(values);
  };

  const pagination = (current: number, total: number): TablePaginationConfig => ({
    current,
    pageSize: PAGE_SIZE,
    total,
    showSizeChanger: false,
    showTotal: (t) => `共 ${t} 条`,
  });

  const providerFilterSelect = (value: string | undefined, onChange: (v?: string) => void) => (
    <Select
      allowClear
      placeholder="全部提供商"
      style={{ width: 200, marginBottom: 16 }}
      value={value}
      onChange={onChange}
      loading={providersQuery.isLoading}
      options={providerOptions(providersQuery.data)}
    />
  );

  const instanceColumns: ColumnsType<ConnectorInstance> = [
    {
      title: '实例编码',
      dataIndex: 'instanceCode',
      width: 160,
      render: (v: string) => <Typography.Text code>{v}</Typography.Text>,
    },
    { title: '名称', dataIndex: 'name', width: 160, ellipsis: true },
    {
      title: '提供商',
      dataIndex: 'providerCode',
      width: 110,
      render: (v: string) => (
        <Tag color="blue">
          {providerMap.get(v)?.displayName ?? v}
        </Tag>
      ),
    },
    {
      title: '目标Agent',
      dataIndex: 'agentCode',
      width: 130,
      ellipsis: true,
      render: (v?: string) => v ?? '-',
    },
    {
      title: '凭证',
      dataIndex: 'credentialId',
      width: 130,
      ellipsis: true,
      render: (v?: string) =>
        (credentialOptionsQuery.data?.records ?? []).find((c) => c.id === v)?.name ?? '-',
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (_: unknown, record: ConnectorInstance) => (
        <Switch
          size="small"
          checked={record.status === STATUS_ENABLED}
          loading={statusMutation.isPending && statusMutation.variables?.id === record.id}
          onChange={() => statusMutation.mutate(record)}
        />
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 240,
      fixed: 'right',
      render: (_: unknown, record: ConnectorInstance) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEditInstance(record)}>
            编辑
          </Button>
          <Button type="link" size="small" onClick={() => setToolsInstance(record)}>
            工具
          </Button>
          <Button type="link" size="small" onClick={() => setCallbackInstance(record)}>
            回调地址
          </Button>
          <Popconfirm title="确认删除该实例？" onConfirm={() => deleteInstanceMutation.mutate(record.id!)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const credentialColumns: ColumnsType<ConnectorCredential> = [
    { title: '名称', dataIndex: 'name', width: 180, ellipsis: true },
    {
      title: '提供商',
      dataIndex: 'providerCode',
      width: 110,
      render: (v: string) => (
        <Tag color="blue">
          {providerMap.get(v)?.displayName ?? v}
        </Tag>
      ),
    },
    {
      title: '凭证信息',
      dataIndex: 'maskedJson',
      width: 320,
      render: (v?: string) => renderMasked(v),
    },
    {
      title: '状态',
      dataIndex: 'status',
      width: 90,
      render: (v?: string) => statusTag(v),
    },
    {
      title: '更新时间',
      dataIndex: 'updateTime',
      width: 170,
      render: (v?: string) => (v ? formatDate(v) : '-'),
    },
    {
      title: '操作',
      width: 200,
      fixed: 'right',
      render: (_: unknown, record: ConnectorCredential) => (
        <Space size="small">
          <Button type="link" size="small" onClick={() => openEditCredential(record)}>
            编辑
          </Button>
          <Button
            type="link"
            size="small"
            loading={
              testCredentialMutation.isPending && testCredentialMutation.variables === record.id
            }
            onClick={() => testCredentialMutation.mutate(record.id!)}
          >
            测试
          </Button>
          <Popconfirm title="确认删除该凭证？" onConfirm={() => deleteCredentialMutation.mutate(record.id!)}>
            <Button type="link" size="small" danger>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const providerColumns: ColumnsType<ProviderCatalogItem> = [
    {
      title: '提供商',
      dataIndex: 'providerCode',
      width: 120,
      render: (v: string) => <Tag color="blue">{v}</Tag>,
    },
    {
      title: '名称',
      width: 120,
      render: (_: unknown, record: ProviderCatalogItem) => record.descriptor?.displayName ?? '-',
    },
    {
      title: '分类',
      width: 100,
      render: (_: unknown, record: ProviderCatalogItem) => record.descriptor?.category ?? '-',
    },
    {
      title: '入站消息',
      width: 100,
      render: (_: unknown, record: ProviderCatalogItem) =>
        record.descriptor?.inboundSupported ? (
          <Tag color="green">支持</Tag>
        ) : (
          <Tag>不支持</Tag>
        ),
    },
    {
      title: '工具数',
      width: 80,
      render: (_: unknown, record: ProviderCatalogItem) => record.descriptor?.tools?.length ?? 0,
    },
    {
      title: '描述',
      dataIndex: 'description',
      ellipsis: true,
      render: (_: unknown, record: ProviderCatalogItem) => record.descriptor?.description ?? '-',
    },
  ];

  const toolsQuery = useQuery({
    queryKey: ['connector-instance-tools', toolsInstance?.id],
    queryFn: () => api.connector.instances.tools(toolsInstance!.id!),
    enabled: !!toolsInstance,
  });

  const callbackUrlQuery = useQuery({
    queryKey: ['connector-callback-url', callbackInstance?.id],
    queryFn: () => api.connector.instances.callbackUrl(callbackInstance!.id!),
    enabled: !!callbackInstance,
  });

  const credentialSelectOptions = (credentialOptionsQuery.data?.records ?? [])
    .filter((c) => c.providerCode === watchedInstanceProvider)
    .map((c) => ({ value: c.id ?? '', label: c.name ?? c.id ?? '' }));

  return (
    <Card title="连接器管理">
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="连接器将钉钉/企微/飞书/数据库等外部能力成品化为Agent工具与入站回调网关；实例绑定agentCode后工具自动装配，入站型提供商必须绑定目标Agent；凭证密钥仅显示尾号掩码，编辑时留空保留原密钥"
      />
      <Tabs
        defaultActiveKey="instances"
        items={[
          {
            key: 'instances',
            label: '实例管理',
            children: (
              <>
                <Space style={{ marginBottom: 16 }}>
                  {providerFilterSelect(instanceProviderFilter, (v) => {
                    setInstanceProviderFilter(v);
                    setInstancePage(1);
                  })}
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreateInstance}>
                    新增实例
                  </Button>
                </Space>
                <Table<ConnectorInstance>
                  rowKey="id"
                  columns={instanceColumns}
                  dataSource={instancesQuery.data?.records ?? []}
                  loading={instancesQuery.isLoading}
                  scroll={{ x: 1150 }}
                  pagination={pagination(instancePage, instancesQuery.data?.total ?? 0)}
                  onChange={(p) => setInstancePage(p.current ?? 1)}
                />
              </>
            ),
          },
          {
            key: 'credentials',
            label: '凭证管理',
            children: (
              <>
                <Space style={{ marginBottom: 16 }}>
                  {providerFilterSelect(credentialProviderFilter, (v) => {
                    setCredentialProviderFilter(v);
                    setCredentialPage(1);
                  })}
                  <Button type="primary" icon={<PlusOutlined />} onClick={openCreateCredential}>
                    新增凭证
                  </Button>
                </Space>
                <Table<ConnectorCredential>
                  rowKey="id"
                  columns={credentialColumns}
                  dataSource={credentialsQuery.data?.records ?? []}
                  loading={credentialsQuery.isLoading}
                  scroll={{ x: 1000 }}
                  pagination={pagination(credentialPage, credentialsQuery.data?.total ?? 0)}
                  onChange={(p) => setCredentialPage(p.current ?? 1)}
                />
              </>
            ),
          },
          {
            key: 'providers',
            label: '提供商目录',
            children: (
              <Table<ProviderCatalogItem>
                rowKey="providerCode"
                columns={providerColumns}
                dataSource={providersQuery.data ?? []}
                loading={providersQuery.isLoading}
                pagination={false}
                expandable={{
                  expandedRowRender: (record) => (
                    <Table<ConnectorToolDefinition>
                      rowKey="name"
                      size="small"
                      pagination={false}
                      dataSource={record.descriptor?.tools ?? []}
                      columns={[
                        { title: '工具名', dataIndex: 'name', width: 220 },
                        { title: '描述', dataIndex: 'description' },
                      ]}
                      expandable={{
                        rowExpandable: (tool) => !!tool.parametersSchema,
                        expandedRowRender: (tool) => (
                          <Typography.Paragraph code style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                            {tool.parametersSchema}
                          </Typography.Paragraph>
                        ),
                      }}
                    />
                  ),
                }}
              />
            ),
          },
        ]}
      />

      <Modal
        title={editingInstance ? '编辑实例' : '新增实例'}
        open={instanceModalOpen}
        onCancel={closeInstanceModal}
        onOk={handleInstanceSubmit}
        confirmLoading={saveInstanceMutation.isPending}
        width={560}
        destroyOnClose
      >
        <Form form={instanceForm} layout="vertical" initialValues={{ enabled: true }}>
          <Form.Item name="providerCode" label="提供商" rules={[{ required: true, message: '请选择提供商' }]}>
            <Select
              placeholder="请选择提供商"
              disabled={!!editingInstance}
              loading={providersQuery.isLoading}
              options={providerOptions(providersQuery.data)}
              onChange={() => instanceForm.setFieldsValue({ extraFields: {} })}
            />
          </Form.Item>
          <Form.Item
            name="instanceCode"
            label="实例编码"
            extra={editingInstance ? '实例编码不可修改（回调路由键）' : '入站回调地址路由键，字母/数字/中划线'}
            rules={[
              { required: true, message: '请输入实例编码' },
              { pattern: /^[a-zA-Z0-9_-]{1,64}$/, message: '仅允许字母/数字/下划线/中划线，最长64字符' },
            ]}
          >
            <Input placeholder="如 dingtalk-main" disabled={!!editingInstance} />
          </Form.Item>
          <Form.Item name="name" label="实例名称" rules={[{ required: true, message: '请输入实例名称' }]}>
            <Input placeholder="请输入实例名称" />
          </Form.Item>
          <Form.Item
            name="credentialId"
            label="关联凭证"
            extra={instanceDescriptor?.credentialFields?.length ? undefined : '该提供商无需凭证'}
          >
            <Select
              allowClear
              placeholder={instanceDescriptor?.credentialFields?.length ? '请选择凭证' : '免凭证'}
              disabled={!instanceDescriptor?.credentialFields?.length}
              loading={credentialOptionsQuery.isLoading}
              options={credentialSelectOptions}
            />
          </Form.Item>
          <Form.Item
            name="agentCode"
            label="目标Agent编码"
            rules={
              instanceDescriptor?.inboundSupported
                ? [{ required: true, message: '入站型提供商必须绑定目标Agent编码' }]
                : undefined
            }
            extra={
              instanceDescriptor?.inboundSupported
                ? '入站消息将异步驱动该Agent执行并回复'
                : '可空：仅使用出站工具时不绑定'
            }
          >
            <Input placeholder="如 customer-service" />
          </Form.Item>
          {renderDescriptorFields(instanceDescriptor?.configFields)}
          {!editingInstance && (
            <Form.Item name="enabled" label="创建后启用" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>

      <Modal
        title={editingCredential ? '编辑凭证' : '新增凭证'}
        open={credentialModalOpen}
        onCancel={closeCredentialModal}
        onOk={handleCredentialSubmit}
        confirmLoading={saveCredentialMutation.isPending}
        width={520}
        destroyOnClose
      >
        <Form form={credentialForm} layout="vertical">
          <Form.Item name="providerCode" label="提供商" rules={[{ required: true, message: '请选择提供商' }]}>
            <Select
              placeholder="请选择提供商"
              disabled={!!editingCredential}
              loading={providersQuery.isLoading}
              options={providerOptions(providersQuery.data)}
              onChange={() => credentialForm.setFieldsValue({ extraFields: {} })}
            />
          </Form.Item>
          <Form.Item name="name" label="凭证名称" rules={[{ required: true, message: '请输入凭证名称' }]}>
            <Input placeholder="请输入凭证名称" />
          </Form.Item>
          {renderDescriptorFields(
            credentialDescriptor?.credentialFields,
            editingCredential ? '留空保留原密钥' : undefined,
          )}
          {editingCredential && (
            <Alert
              type="warning"
              showIcon
              message="凭证内容整体加密存储且不回显；如需变更请完整填写全部字段（敏感字段留空表示整体保留原凭证）"
            />
          )}
        </Form>
      </Modal>

      <Modal
        title={`实例工具 - ${toolsInstance?.name ?? ''}`}
        open={!!toolsInstance}
        onCancel={() => setToolsInstance(null)}
        footer={null}
        width={680}
        destroyOnClose
      >
        <Table<ConnectorToolDefinition>
          rowKey="name"
          size="small"
          loading={toolsQuery.isLoading}
          dataSource={toolsQuery.data ?? []}
          pagination={false}
          columns={[
            { title: '工具名', dataIndex: 'name', width: 220 },
            { title: '描述', dataIndex: 'description' },
          ]}
          expandable={{
            rowExpandable: (tool) => !!tool.parametersSchema,
            expandedRowRender: (tool) => (
              <Typography.Paragraph code style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                {tool.parametersSchema}
              </Typography.Paragraph>
            ),
          }}
        />
      </Modal>

      <Modal
        title={`入站回调地址 - ${callbackInstance?.name ?? ''}`}
        open={!!callbackInstance}
        onCancel={() => setCallbackInstance(null)}
        footer={null}
        width={640}
        destroyOnClose
      >
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 16 }}
          message="将该地址配置到对应平台开放后台（钉钉机器人消息接收地址/企微回调URL/飞书事件订阅请求地址）"
        />
        {callbackUrlQuery.isLoading ? (
          <Typography.Text type="secondary">生成中...</Typography.Text>
        ) : (
          <Typography.Text code copyable style={{ wordBreak: 'break-all' }}>
            {callbackUrlQuery.data ?? '-'}
          </Typography.Text>
        )}
      </Modal>
    </Card>
  );
};
