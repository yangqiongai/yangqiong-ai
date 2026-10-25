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
import React, { useMemo, useState } from 'react';
import {
  Alert,
  Button,
  Descriptions,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Select,
  Space,
  Steps,
  Switch,
  Tag,
  Typography,
  message,
} from 'antd';
import { CaretRightOutlined } from '@ant-design/icons';
import { api } from '@/services';
import { useMutation } from '@tanstack/react-query';
import type { CapabilityDebugResponse, CapabilityDebugStep, CapabilitySpec } from '@yangqiong/shared';
import CodeBlock from './CodeBlock';

/**
 * 入参Schema的属性定义（仅用到演示所需的基础类型）
 */
interface SchemaProperty {
  type?: string;
  description?: string;
  enum?: string[];
}

/**
 * 步骤图标色（通过绿/失败红/跳过灰）
 */
const stepStatusOf = (step?: CapabilityDebugStep): 'finish' | 'error' | 'wait' => {
  if (!step) {
    return 'wait';
  }
  return step.passed ? 'finish' : 'error';
};

/**
 * 能力测试抽屉
 * <p>
 * 按能力调用全流程（入参校验 → Prompt渲染 → Agent执行 → 契约校验）分步展示中间产物，
 * 帮助理解能力的使用过程与定位问题。
 * </p>
 * @param props
 * @return
 */
const CapabilityTestDrawer: React.FC<{
  open: boolean;
  spec: CapabilitySpec | null;
  onClose: () => void;
}> = ({ open, spec, onClose }) => {
  const [form] = Form.useForm();
  const [response, setResponse] = useState<CapabilityDebugResponse | null>(null);

  const schema = useMemo(() => {
    if (!spec?.inputSchemaContent) {
      return null;
    }
    try {
      const parsed = JSON.parse(spec.inputSchemaContent) as {
        required?: string[];
        properties?: Record<string, SchemaProperty>;
      };
      return { required: parsed.required ?? [], properties: parsed.properties ?? {} };
    } catch {
      return null;
    }
  }, [spec]);

  const debugMutation = useMutation({
    mutationFn: (values: Record<string, unknown>) => api.openCapability.debug(spec!.code, values),
    onSuccess: (data) => {
      setResponse(data);
    },
    onError: (err) => {
      message.error(err instanceof Error ? err.message : '调试执行失败');
    },
  });

  /**
   * 关闭时清空执行结果与表单
   */
  const handleClose = () => {
    setResponse(null);
    form.resetFields();
    onClose();
  };

  /**
   * 提交入参执行调试调用
   * @param values
   */
  const handleRun = (values: Record<string, unknown>) => {
    const args: Record<string, unknown> = {};
    Object.entries(values).forEach(([key, value]) => {
      // 空串与undefined不作为入参传递，保持与开放接口调用语义一致
      if (value !== undefined && value !== null && value !== '') {
        args[key] = value;
      }
    });
    setResponse(null);
    debugMutation.mutate(args);
  };

  const prettyJson = (value: unknown) => {
    try {
      return JSON.stringify(value, null, 2);
    } catch {
      return String(value);
    }
  };

  const hasResponse = Boolean(response);
  const stepCurrent = useMemo(() => {
    if (!response) {
      return 0;
    }
    // 已执行到的步骤：第一步失败停在0，之后按通过情况推进
    if (!response.inputValidation?.passed) {
      return 0;
    }
    if (!response.agentExecution?.passed) {
      return 2;
    }
    if (!response.outputContract?.passed) {
      return 3;
    }
    return 4;
  }, [response]);

  return (
    <Drawer
      title={spec ? `测试能力：${spec.name || spec.code}` : '测试能力'}
      width={680}
      open={open}
      onClose={handleClose}
      destroyOnClose
    >
      {spec && (
        <>
          <Descriptions
            size="small"
            column={2}
            items={[
              { key: 'code', label: '编码', children: spec.code },
              { key: 'agent', label: '调用智能体', children: spec.agentCode },
              { key: 'version', label: '版本', children: spec.version },
              {
                key: 'exec',
                label: '执行方式',
                children: spec.execType === 'WORKFLOW' ? `工作流 ${spec.workflowCode ?? ''}` : 'Agent',
              },
            ]}
          />

          <Form
            form={form}
            layout="vertical"
            style={{ marginTop: 16 }}
            onFinish={handleRun}
          >
            {schema ? (
              Object.entries(schema.properties).map(([key, prop]) => {
                const required = schema.required.includes(key);
                const label = (
                  <Space size={4}>
                    <span>{key}</span>
                    {required && <span style={{ color: '#ff4d4f' }}>*</span>}
                  </Space>
                );
                return (
                  <Form.Item
                    key={key}
                    name={key}
                    label={label}
                    rules={required ? [{ required: true, message: `请填写 ${key}` }] : undefined}
                    extra={prop.description}
                  >
                    {prop.enum ? (
                      <Select
                        placeholder={`选择 ${key}`}
                        allowClear
                        options={prop.enum.map((v) => ({ label: v, value: v }))}
                      />
                    ) : prop.type === 'number' || prop.type === 'integer' ? (
                      <InputNumber style={{ width: '100%' }} placeholder={`请输入 ${key}`} />
                    ) : prop.type === 'boolean' ? (
                      <Switch />
                    ) : (
                      <Input.TextArea rows={2} placeholder={`请输入 ${key}`} />
                    )}
                  </Form.Item>
                );
              })
            ) : (
              <Alert type="info" showIcon message="该能力未配置入参Schema，调用时无需传参" />
            )}

            <Button
              type="primary"
              htmlType="submit"
              icon={<CaretRightOutlined />}
              loading={debugMutation.isPending}
            >
              执行测试
            </Button>
          </Form>

          <Typography.Paragraph
            type="secondary"
            style={{ marginTop: 8, marginBottom: 0, fontSize: 12 }}
          >
            测试走与开放接口相同的执行链路，逐步展示校验、Prompt 渲染、Agent 执行与契约校验结果，便于理解调用过程。
          </Typography.Paragraph>

          {hasResponse && (
            <div style={{ marginTop: 20 }}>
              <Alert
                type={response!.success ? 'success' : 'error'}
                showIcon
                message={response!.success ? '调用成功' : '调用失败'}
                description={
                  <Space size={4} wrap style={{ fontSize: 12 }}>
                    <span>耗时 {response!.durationMillis ?? 0} ms</span>
                    {response!.callId && <span>调用ID {response!.callId}</span>}
                    {response!.tokenMetrics && (
                      <span>
                        Token {prettyJson(response!.tokenMetrics)}
                      </span>
                    )}
                  </Space>
                }
              />

              <Steps
                direction="vertical"
                size="small"
                current={stepCurrent}
                style={{ marginTop: 16 }}
                items={[
                  {
                    title: '入参校验',
                    status: stepStatusOf(response!.inputValidation),
                    description: response!.inputValidation && (
                      <div style={{ fontSize: 12 }}>
                        <span>{response!.inputValidation.message}</span>
                        {response!.inputValidation.errors?.map((err) => (
                          <div key={err}>
                            <Tag color="error">{err}</Tag>
                          </div>
                        ))}
                      </div>
                    ),
                  },
                  {
                    title: 'Prompt 渲染',
                    status: response!.renderedPrompt ? 'finish' : 'wait',
                    description: response!.renderedPrompt && (
                      <CodeBlock value={response!.renderedPrompt} rows={8} />
                    ),
                  },
                  {
                    title: 'Agent 执行',
                    status: stepStatusOf(response!.agentExecution),
                    description: response!.agentExecution && (
                      <div style={{ fontSize: 12 }}>
                        <span>{response!.agentExecution.message ?? response!.agentExecution.errors?.join('; ')}</span>
                        {response!.agentOutput && (
                          <div style={{ marginTop: 8 }}>
                            <CodeBlock value={response!.agentOutput} rows={8} />
                          </div>
                        )}
                      </div>
                    ),
                  },
                  {
                    title: '输出契约校验',
                    status: stepStatusOf(response!.outputContract),
                    description: response!.outputContract && (
                      <div style={{ fontSize: 12 }}>
                        <span>{response!.outputContract.message ?? response!.outputContract.errors?.join('; ')}</span>
                        {response!.structuredOutput !== undefined && response!.structuredOutput !== null && (
                          <div style={{ marginTop: 8 }}>
                            <CodeBlock value={prettyJson(response!.structuredOutput)} rows={10} />
                          </div>
                        )}
                      </div>
                    ),
                  },
                ]}
              />

              {!response!.success && response!.errorMessage && (
                <Alert
                  type="error"
                  showIcon
                  style={{ marginTop: 12 }}
                  message={response!.errorMessage}
                />
              )}

              {debugMutation.isSuccess &&
                !response!.renderedPrompt &&
                !response!.agentOutput &&
                response!.success && (
                  <Empty description="该能力未返回中间产物" style={{ marginTop: 24 }} />
                )}
            </div>
          )}
        </>
      )}
    </Drawer>
  );
};

export default CapabilityTestDrawer;
