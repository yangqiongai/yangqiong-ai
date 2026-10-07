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
import { Alert, AutoComplete, Button, Col, Form, Input, Modal, Row, Select, Space, Switch } from 'antd';
import { ApiOutlined } from '@ant-design/icons';
import type { ModelInfo, ModelTestResult } from '@yangqiong/shared';
import { api } from '@/services';

export interface ModelFormValues {
  provider?: string;
  modelCode: string;
  modelName: string;
  modelType?: string;
  apiEndpoint?: string;
  apiKey?: string;
  modelConfig?: string;
  isDefault?: number;
  modelStatus?: number;
  remark?: string;
  supportReasoning?: number;
  supportImage?: number;
  scopeId?: string;
}

/**
 * 常用供应方类型选项，支持自定义输入其他供应方
 */
const PROVIDER_OPTIONS: string[] = [
  'openai',
  'anthropic',
  'gemini',
  'ollama',
  'dashscope',
  'djl',
];

interface ModelFormModalProps {
  open: boolean;
  editing: ModelInfo | null;
  loading: boolean;
  onOk: (values: ModelFormValues) => void;
  onCancel: () => void;
}

/**
 * 模型表单弹窗
 */
export const ModelFormModal: React.FC<ModelFormModalProps> = ({
  open,
  editing,
  loading,
  onOk,
  onCancel,
}) => {
  const [form] = Form.useForm<ModelFormValues>();
  const [testing, setTesting] = useState<boolean>(false);
  const [testResult, setTestResult] = useState<ModelTestResult | null>(null);

  useEffect(() => {
    if (open) {
      setTestResult(null);
      if (editing) {
        // 先重置再回填，避免上一条记录的字段值残留
        form.resetFields();
        form.setFieldsValue({
          provider: editing.provider,
          modelCode: editing.modelCode,
          modelName: editing.modelName,
          modelType: editing.modelType,
          apiEndpoint: editing.apiEndpoint,
          // 后端返回掩码密钥（前4位+****+后4位），直接回填展示
          apiKey: editing.apiKey,
          modelConfig: editing.modelConfig,
          isDefault: editing.isDefault,
          modelStatus: editing.modelStatus,
          remark: editing.remark,
          supportReasoning: editing.supportReasoning ?? 1,
          supportImage: editing.supportImage ?? 0,
          scopeId: editing.scopeId,
        });
      } else {
        form.resetFields();
        form.setFieldsValue({ modelStatus: 1, isDefault: 0, supportReasoning: 1, supportImage: 0 });
      }
    }
  }, [open, editing, form]);

  const handleOk = async () => {
    const values = await form.validateFields();
    onOk(values);
  };

  /**
   * 用当前表单配置测试模型连通性（无需先保存）
   * @return
   */
  const handleTest = async () => {
    setTesting(true);
    setTestResult(null);
    try {
      const res = await api.model.testConnectivity(form.getFieldsValue());
      setTestResult(res);
    } catch (err) {
      setTestResult({ success: false, error: err instanceof Error ? err.message : '测试请求失败' });
    } finally {
      setTesting(false);
    }
  };

  return (
    <Modal
      title={editing ? '编辑模型' : '新增模型'}
      open={open}
      onOk={handleOk}
      onCancel={onCancel}
      confirmLoading={loading}
      destroyOnClose
      width={860}
    >
      <Form form={form} layout="vertical">
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="provider"
              label="供应方类型"
              rules={[{ required: true, message: '请选择或输入供应方类型' }]}
            >
              <AutoComplete
                options={PROVIDER_OPTIONS.map((p) => ({ value: p }))}
                placeholder="选择或输入供应方类型"
                filterOption={(input, option) =>
                  (option?.value ?? '').toLowerCase().includes(input.toLowerCase())
                }
              />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item
              name="modelCode"
              label="模型编码"
              rules={[{ required: true, message: '请输入模型编码' }]}
            >
              <Input placeholder="如 gpt-4o" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item
              name="modelName"
              label="模型名称"
              rules={[{ required: true, message: '请输入模型名称' }]}
            >
              <Input placeholder="请输入模型名称" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="modelType" label="模型类型">
              <Input placeholder="如 chat / embedding" />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="apiEndpoint" label="接口地址">
              <Input placeholder="如 https://api.openai.com/v1" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="apiKey" label="API Key">
              <Input placeholder={editing ? '掩码展示，如需更换请输入新密钥' : 'API Key'} />
            </Form.Item>
          </Col>
        </Row>
        <Form.Item name="modelConfig" label="模型配置">
          <Input.TextArea rows={2} placeholder="模型配置 JSON" />
        </Form.Item>
        <Form.Item name="remark" label="描述">
          <Input.TextArea rows={2} placeholder="请输入描述" />
        </Form.Item>
        <Row gutter={16}>
          <Col span={12}>
            <Form.Item name="scopeId" label="作用域 ID">
              <Input placeholder="留空表示全局" />
            </Form.Item>
          </Col>
          <Col span={12}>
            <Form.Item name="modelStatus" label="状态">
              <Select
                options={[
                  { label: '启用', value: 1 },
                  { label: '禁用', value: 0 },
                ]}
              />
            </Form.Item>
          </Col>
        </Row>
        <Row gutter={16}>
          <Col span={8}>
            <Form.Item
              name="isDefault"
              label="是否默认"
              valuePropName="checked"
              getValueFromEvent={(checked) => (checked ? 1 : 0)}
            >
              <Switch />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="supportReasoning"
              label="是否支持推理"
              valuePropName="checked"
              getValueFromEvent={(checked) => (checked ? 1 : 0)}
              extra="关闭后智能体界面的推理配置将被禁用"
            >
              <Switch />
            </Form.Item>
          </Col>
          <Col span={8}>
            <Form.Item
              name="supportImage"
              label="是否支持图片"
              valuePropName="checked"
              getValueFromEvent={(checked) => (checked ? 1 : 0)}
            >
              <Switch />
            </Form.Item>
          </Col>
        </Row>
        <Space style={{ marginBottom: 16 }}>
          <Button icon={<ApiOutlined />} loading={testing} onClick={() => void handleTest()}>
            测试连接
          </Button>
          {testResult && (
            <Alert
              type={testResult.success ? 'success' : 'error'}
              showIcon
              style={{ flex: 1, margin: 0 }}
              message={
                testResult.success
                  ? `连接成功，耗时 ${testResult.latencyMs ?? 0}ms${testResult.reply ? `，回复：${testResult.reply}` : ''}`
                  : `连接失败：${testResult.error ?? '未知错误'}`
              }
            />
          )}
        </Space>
      </Form>
    </Modal>
  );
};
