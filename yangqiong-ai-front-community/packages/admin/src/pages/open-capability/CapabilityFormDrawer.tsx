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
  Button,
  Collapse,
  Drawer,
  Form,
  Input,
  InputNumber,
  message,
  Modal,
  Radio,
  Select,
  Space,
  Switch,
  Table,
  Tooltip,
  TreeSelect,
} from 'antd';
import { FullscreenOutlined } from '@ant-design/icons';
import { EnterpriseLockIcon } from '@yangqiong/shared';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/services';
import { CategoryGroupTreeSelect } from '@/components/CategoryGroupTreeSelect';
import { SchemaEditorModal } from '@/components/SchemaEditorModal';
import type {
  CapabilityCategoryNode,
  CapabilityDefinitionHistory,
  CapabilitySpec,
} from '@yangqiong/shared';

export interface CapabilityFormValues {
  code: string;
  name?: string;
  version?: string;
  category?: string;
  agentCode?: string;
  execType?: string;
  description?: string;
  model?: string;
  temperature?: number;
  maxTokens?: number;
  maxIterations?: number;
  tools?: string[];
  skills?: string[];
  replaceSkills?: boolean;
  kbCodes?: string[];
  kbTopK?: number;
  kbScoreThreshold?: number;
  kbUploadCode?: string;
  promptTemplateContent?: string;
  inputSchemaContent?: string;
  outputSchemaContent?: string;
  inputSchemaDescription?: string;
  outputSchemaDescription?: string;
  execMode?: string;
  execTimeoutSeconds?: number;
  retryMaxAttempts?: number;
  retryBackoffMillis?: number;
  contractStrict?: boolean;
  contractAutoRepair?: boolean;
  contextRequired?: boolean;
  contextMaxContexts?: number;
  auditEnabled?: boolean;
  auditLogInput?: boolean;
  auditLogOutput?: boolean;
}

interface AgentOverridesLike {
  model?: string;
  temperature?: number;
  maxTokens?: number;
  maxIterations?: number;
  tools?: string[];
  skills?: string[];
  replaceSkills?: boolean;
  knowledgeBase?: { kbCodes?: string[]; topK?: number; scoreThreshold?: number; uploadKbCode?: string };
}

interface ExecutionLike {
  mode?: string;
  timeout?: string;
  retry?: { maxAttempts?: number; backoffMillis?: number };
}

interface CapabilityFormDrawerProps {
  open: boolean;

  /**
   * 编辑中的能力（null 表示新增）
   */
  editing: CapabilitySpec | null;
  confirmLoading?: boolean;

  /**
   * 关闭抽屉回调
   */
  onClose: () => void;

  /**
   * 提交能力规格回调
   */
  onSubmit: (spec: CapabilitySpec) => void;

  /**
   * 版本回滚成功回调（通知列表刷新）
   */
  onRolledBack?: () => void;
}

/**
 * 版本历史操作类型文案
 */
const HISTORY_OPERATION_LABELS: Record<string, string> = {
  CREATE: '创建',
  UPDATE: '更新',
  ROLLBACK: '回滚',
};

/**
 * 格式化历史快照时间（去除ISO的T分隔符）
 * @param time
 * @return
 */
const formatHistoryTime = (time?: string): string =>
  time ? time.replace('T', ' ').slice(0, 19) : '-';

/**
 * 格式化快照JSON（美化输出，非法时原样返回）
 * @param json
 * @return
 */
const formatSnapshot = (json?: string): string => {
  if (!json?.trim()) {
    return '';
  }
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
};

/**
 * 解析执行超时（'120s'→120），非法或缺省回退120秒
 * @param timeout
 * @return
 */
const parseTimeoutSeconds = (timeout?: string): number => {
  const n = parseInt(timeout ?? '', 10);
  return Number.isFinite(n) && n > 0 ? n : 120;
};

/**
 * JSON文本校验（空值放行）
 * @param _rule
 * @param text
 * @return
 */
const jsonValidator = (_rule: unknown, text?: string): Promise<void> => {
  if (!text?.trim()) {
    return Promise.resolve();
  }
  try {
    JSON.parse(text);
    return Promise.resolve();
  } catch {
    return Promise.reject(new Error('必须是合法的 JSON 文本'));
  }
};

/**
 * 能力规格编辑抽屉（社区版与企业版能力开放页共用）
 */
export const CapabilityFormDrawer: React.FC<CapabilityFormDrawerProps> = ({
  open,
  editing,
  confirmLoading = false,
  onClose,
  onSubmit,
  onRolledBack,
}) => {
  const [form] = Form.useForm<CapabilityFormValues>();
  // Schema大屏编辑弹窗（field对应表单字段名，title为弹窗标题）
  const [jsonEditor, setJsonEditor] = useState<{
    field: 'inputSchemaContent' | 'outputSchemaContent';
    title: string;
  } | null>(null);
  // 版本历史（编辑态按能力编码加载）
  const [historyList, setHistoryList] = useState<CapabilityDefinitionHistory[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyPreview, setHistoryPreview] = useState<CapabilityDefinitionHistory | null>(null);
  // 已选知识库列表（文件上传库选项动态收敛到该范围）
  const selectedKbCodes = Form.useWatch('kbCodes', form);

  /**
   * 加载能力版本历史
   * @param code
   */
  const loadHistory = async (code: string) => {
    setHistoryLoading(true);
    try {
      const list = await api.openCapability.history(code);
      setHistoryList(list ?? []);
    } catch {
      setHistoryList([]);
    } finally {
      setHistoryLoading(false);
    }
  };

  useEffect(() => {
    if (!open || !editing?.code) {
      setHistoryList([]);
      return;
    }
    loadHistory(editing.code);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing?.code]);

  /**
   * 回滚到指定历史版本（以快照内容生成新版本，回滚后关闭抽屉并刷新列表）
   * @param item
   */
  const handleHistoryRollback = (item: CapabilityDefinitionHistory) => {
    if (!editing?.code) {
      return;
    }
    Modal.confirm({
      title: '回滚版本',
      content: `确定回滚到版本 ${item.version || '-'}（${formatHistoryTime(item.createTime)}）？将以该快照内容生成新版本，当前定义被覆盖。`,
      okText: '回滚',
      cancelText: '取消',
      onOk: async () => {
        await api.openCapability.rollback(editing.code, item.id);
        message.success('已回滚并生成新版本');
        await loadHistory(editing.code);
        onRolledBack?.();
        onClose();
      },
    });
  };

  // 执行体与运行时覆盖的可选项（与Agent管理页同源）
  const agentsQuery = useQuery({
    queryKey: ['open-cap-agents'],
    queryFn: () => api.agent.type.list({ page: 1, size: 500 }),
    enabled: open,
  });
  const modelsQuery = useQuery({
    queryKey: ['open-cap-models'],
    queryFn: () => api.model.list({ page: 1, size: 500 }),
    enabled: open,
  });
  const toolsQuery = useQuery({
    queryKey: ['open-cap-tools'],
    queryFn: () => api.tool.config.list({ page: 1, size: 500 }),
    enabled: open,
  });
  const skillsQuery = useQuery({
    queryKey: ['open-cap-skills'],
    queryFn: () => api.skill.definition.list({ page: 1, size: 500 }),
    enabled: open,
  });
  const kbQuery = useQuery({
    queryKey: ['open-cap-kb'],
    queryFn: () => api.knowledge.kb.list(),
    enabled: open,
  });
  const toolCatsQuery = useQuery({
    queryKey: ['open-cap-tool-cats'],
    queryFn: () => api.tool.config.category.tree(),
    enabled: open,
  });
  const skillCatsQuery = useQuery({
    queryKey: ['open-cap-skills-cats'],
    queryFn: () => api.skill.category.tree(),
    enabled: open,
  });
  const categoryTreeQuery = useQuery({
    queryKey: ['open-capability-category-tree'],
    queryFn: () => api.openCapability.categoryTree(),
    enabled: open,
  });

  const resourceOptions = useMemo(
    () => ({
      agents: (agentsQuery.data?.list ?? []).map((a) => ({
        label: `${a.typeName ?? a.typeCode} (${a.typeCode})`,
        value: a.typeCode,
      })),
      models: (modelsQuery.data?.list ?? [])
        .filter((m) => m.modelStatus === 1 || m.modelStatus == null)
        .map((m) => ({ label: `${m.modelName} (${m.modelCode})`, value: m.modelCode })),
      // 仅列 CUSTOM 分类工具，BUILTIN 内置工具自动加载无需勾选
      customTools: (toolsQuery.data?.list ?? [])
        .filter((t) => (t.toolCategory ?? 'CUSTOM') === 'CUSTOM')
        .map((t) => ({ label: `${t.toolName} (${t.toolCode})`, value: t.toolCode, category: t.category })),
      // 仅列非 BUILTIN 信任等级技能，内置技能自动加载无需勾选
      skills: (skillsQuery.data?.list ?? [])
        .filter((s) => s.trustLevel !== 'BUILTIN')
        .map((s) => ({ label: `${s.skillName} (${s.skillId})`, value: s.skillId, category: s.category })),
      knowledgeBases: (kbQuery.data ?? []).map((k) => ({
        label: `${k.kbName} (${k.kbId})`,
        value: k.kbId,
      })),
      // 工具/技能分类树节点（下拉按分类分组展示，与左侧分类树同源）
      toolCategoryNodes: toolCatsQuery.data?.nodes,
      skillCategoryNodes: skillCatsQuery.data?.nodes,
      // 分类树选项（value=分类code，与能力规格category字段对齐）
      categoryTree: (function convert(nodes?: CapabilityCategoryNode[]): any[] {
        return (nodes ?? []).map((node) => ({
          title: node.name,
          value: node.code,
          children: convert(node.children),
        }));
      })(categoryTreeQuery.data?.nodes),
    }),
    [
      agentsQuery.data,
      modelsQuery.data,
      toolsQuery.data,
      skillsQuery.data,
      kbQuery.data,
      toolCatsQuery.data,
      skillCatsQuery.data,
      categoryTreeQuery.data,
    ],
  );

  // 打开时回填：编辑态取能力规格，新增态预填引擎默认值（提交总是显式完整，行为与内置YAML能力一致）
  // 回填值：destroyOnClose下Drawer内容每次打开重建，经initialValues应用（外部form实例setFieldsValue在挂载前调用会失效）
  const initialValues = useMemo<Record<string, unknown>>(() => {
    if (!editing) {
      return {
        version: '1.0.0',
        execType: 'AGENT',
        agentCode: 'default',
        replaceSkills: false,
        execMode: 'sync',
        execTimeoutSeconds: 120,
        retryMaxAttempts: 2,
        retryBackoffMillis: 1000,
        contractStrict: true,
        contractAutoRepair: true,
        contextRequired: false,
        contextMaxContexts: 10,
        auditEnabled: true,
        auditLogInput: true,
        auditLogOutput: true,
      };
    }
    const ov = (editing.agentOverrides ?? {}) as AgentOverridesLike;
    const kb = ov.knowledgeBase ?? {};
    const ex = (editing.execution ?? {}) as ExecutionLike;
    const ct = (editing.contract ?? {}) as Record<string, unknown>;
    const cx = (editing.context ?? {}) as Record<string, unknown>;
    const au = (editing.audit ?? {}) as Record<string, unknown>;
    return {
      code: editing.code,
      name: editing.name,
      version: editing.version,
      category: editing.category,
      // 工作流执行体为企业版能力：存量WORKFLOW能力编辑时强制回落为AGENT
      execType: editing.execType === 'WORKFLOW' ? 'AGENT' : editing.execType ?? 'AGENT',
      agentCode: editing.agentCode ?? 'default',
      description: editing.description,
      model: ov.model,
      temperature: ov.temperature,
      maxTokens: ov.maxTokens,
      maxIterations: ov.maxIterations,
      tools: ov.tools,
      skills: ov.skills,
      replaceSkills: ov.replaceSkills ?? false,
      kbCodes: kb.kbCodes,
      kbTopK: kb.topK,
      kbScoreThreshold: kb.scoreThreshold,
      kbUploadCode: kb.uploadKbCode,
      promptTemplateContent: editing.promptTemplateContent,
      inputSchemaContent: editing.inputSchemaContent,
      outputSchemaContent: editing.outputSchemaContent,
      inputSchemaDescription: editing.inputSchemaDescription,
      outputSchemaDescription: editing.outputSchemaDescription,
      execMode: ex.mode ?? 'sync',
      execTimeoutSeconds: parseTimeoutSeconds(ex.timeout),
      retryMaxAttempts: ex.retry?.maxAttempts ?? 2,
      retryBackoffMillis: ex.retry?.backoffMillis ?? 1000,
      contractStrict: (ct.strict as boolean) ?? true,
      contractAutoRepair: (ct.autoRepair as boolean) ?? true,
      contextRequired: (cx.required as boolean) ?? false,
      contextMaxContexts: (cx.maxContexts as number) ?? 10,
      auditEnabled: (au.enabled as boolean) ?? true,
      auditLogInput: (au.logInput as boolean) ?? true,
      auditLogOutput: (au.logOutput as boolean) ?? true,
    };
  }, [editing]);

  useEffect(() => {
    if (!open) {
      return;
    }
    // 外部form实例的store不随Drawer销毁清空，重开后必须先重置再回填，否则会显示上一个能力残留的值
    form.resetFields();
    if (editing) {
      form.setFieldsValue(initialValues);
      // 存量WORKFLOW能力编辑时已强制回落为AGENT，提示用户重新选择执行Agent
      if (editing.execType === 'WORKFLOW') {
        message.warning('工作流执行体为企业版能力，已切换为 Agent 执行体，请重新选择执行 Agent');
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, editing]);

  /**
   * 解析agentConfig JSON（容错，非法或空返回空对象）
   * @param text
   * @return
   */
  const parseAgentConfig = (text?: string): Record<string, any> => {
    if (!text) {
      return {};
    }
    try {
      const parsed = JSON.parse(text);
      return parsed && typeof parsed === 'object' ? parsed : {};
    } catch {
      return {};
    }
  };

  /**
   * 用户切换执行Agent时联动加载该Agent配置的默认参数（agentConfig无对应字段的参数切换时清空）
   * @param agentCode
   */
  const handleAgentChange = (agentCode?: string) => {
    const agent = (agentsQuery.data?.list ?? []).find((a) => a.typeCode === agentCode);
    const cfg = parseAgentConfig(agent?.agentConfig);
    const kb = (cfg.knowledgeBase ?? {}) as { kbCodes?: string[] };
    const nextKbCodes = kb.kbCodes ?? [];
    const prevUploadCode = form.getFieldValue('kbUploadCode') as string | undefined;
    form.setFieldsValue({
      model: cfg.model,
      temperature: cfg.temperature,
      maxTokens: undefined,
      maxIterations: cfg.maxIterations,
      tools: cfg.tools ?? [],
      skills: cfg.skills ?? [],
      replaceSkills: cfg.bindingMode === 'replace',
      kbCodes: nextKbCodes,
      kbTopK: cfg.topK,
      kbScoreThreshold: undefined,
      // 文件上传库须在知识库范围内，切库后不再属于范围则清空
      kbUploadCode: prevUploadCode && nextKbCodes.includes(prevUploadCode) ? prevUploadCode : undefined,
    });
  };

  /**
   * 组装Agent运行时覆盖（仅写有值字段）
   * @param values
   * @return
   */
  const buildAgentOverrides = (values: CapabilityFormValues): Record<string, unknown> => {
    const ov: Record<string, unknown> = {};
    if (values.model) {
      ov.model = values.model;
    }
    if (values.temperature != null) {
      ov.temperature = values.temperature;
    }
    if (values.maxTokens != null) {
      ov.maxTokens = values.maxTokens;
    }
    if (values.maxIterations != null) {
      ov.maxIterations = values.maxIterations;
    }
    if (values.tools?.length) {
      ov.tools = values.tools;
    }
    if (values.skills?.length) {
      ov.skills = values.skills;
    }
    ov.replaceSkills = values.replaceSkills ?? false;
    if (values.kbCodes?.length || values.kbTopK != null || values.kbScoreThreshold != null) {
      const kb: Record<string, unknown> = {};
      if (values.kbCodes?.length) {
        kb.kbCodes = values.kbCodes;
      }
      if (values.kbTopK != null) {
        kb.topK = values.kbTopK;
      }
      if (values.kbScoreThreshold != null) {
        kb.scoreThreshold = values.kbScoreThreshold;
      }
      if (values.kbUploadCode) {
        kb.uploadKbCode = values.kbUploadCode;
      }
      ov.knowledgeBase = kb;
    }
    return ov;
  };

  /**
   * 表单值转能力规格（Schema/提示词写内联内容字段）
   * @param values
   * @return
   */
  const buildSpec = (values: CapabilityFormValues): CapabilitySpec => {
    return {
      code: values.code.trim(),
      name: values.name,
      version: values.version,
      category: values.category,
      execType: values.execType ?? 'AGENT',
      agentCode: values.agentCode,
      description: values.description,
      agentOverrides: buildAgentOverrides(values),
      inputSchemaContent: values.inputSchemaContent?.trim() || undefined,
      outputSchemaContent: values.outputSchemaContent?.trim() || undefined,
      inputSchemaDescription: values.inputSchemaDescription?.trim() || undefined,
      outputSchemaDescription: values.outputSchemaDescription?.trim() || undefined,
      promptTemplateContent: values.promptTemplateContent?.trim() || undefined,
      execution: {
        mode: values.execMode,
        timeout: `${values.execTimeoutSeconds ?? 120}s`,
        retry: {
          maxAttempts: values.retryMaxAttempts ?? 2,
          backoffMillis: values.retryBackoffMillis ?? 1000,
        },
      },
      contract: {
        strict: values.contractStrict ?? true,
        autoRepair: values.contractAutoRepair ?? true,
      },
      context: {
        required: values.contextRequired ?? false,
        maxContexts: values.contextMaxContexts ?? 10,
      },
      audit: {
        enabled: values.auditEnabled ?? true,
        logInput: values.auditLogInput ?? true,
        logOutput: values.auditLogOutput ?? true,
      },
    };
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    onSubmit(buildSpec(values));
  };

  /**
   * 打开Schema大屏编辑弹窗
   * @param field
   * @param title
   */
  const openJsonEditor = (field: 'inputSchemaContent' | 'outputSchemaContent', title: string) => {
    setJsonEditor({ field, title });
  };

  /**
   * 大屏编辑保存写回表单字段（Schema与JSON描述一并写回）
   * @param text
   * @param description
   */
  const handleJsonSave = (text: string, description: string) => {
    if (jsonEditor) {
      form.setFieldValue(jsonEditor.field, text);
      form.setFieldValue(
        jsonEditor.field === 'inputSchemaContent' ? 'inputSchemaDescription' : 'outputSchemaDescription',
        description,
      );
    }
    setJsonEditor(null);
  };

  /**
   * Schema字段标签（右侧带大屏编辑入口）
   * @param text
   * @param field
   * @return
   */
  const schemaLabel = (text: string, field: 'inputSchemaContent' | 'outputSchemaContent') => (
    <span style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span>{text}</span>
      <Button
        type="link"
        size="small"
        icon={<FullscreenOutlined />}
        onClick={() => openJsonEditor(field, `${text} - 大屏编辑`)}
      >
        大屏编辑
      </Button>
    </span>
  );

  const collapseItems = [
    {
      key: 'base',
      label: '基础信息',
      forceRender: true,
      children: (
        <>
          <Form.Item name="code" label="编码" rules={[{ required: true, message: '请输入能力编码' }]}>
            <Input placeholder="如 project-overview" disabled={!!editing} />
          </Form.Item>
          <Form.Item name="name" label="名称">
            <Input placeholder="能力显示名称" />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="能力用途说明" />
          </Form.Item>
          <Form.Item name="category" label="分类" extra="从分类树中选择，可清空为未分类">
            <TreeSelect
              placeholder="不选择则为未分类"
              allowClear
              showSearch
              treeNodeFilterProp="title"
              treeData={resourceOptions.categoryTree}
              treeDefaultExpandAll
            />
          </Form.Item>
          <Form.Item
            name="execType"
            label="执行体类型"
            extra="Agent=由单个Agent执行。工作流执行体与企业版异步/SSE流式调用均为企业版增强能力"
          >
            <Radio.Group
              optionType="button"
              buttonStyle="solid"
              options={[
                { label: 'Agent', value: 'AGENT' },
                // 工作流执行体为企业版能力：社区版置灰仅作引导，企业版经fork页面承载
                {
                  label: (
                    <Tooltip title="工作流执行体为企业版能力">
                      <span>工作流 <EnterpriseLockIcon style={{ color: '#d48806' }} /></span>
                    </Tooltip>
                  ),
                  value: 'WORKFLOW',
                  disabled: true,
                },
              ]}
            />
          </Form.Item>
          <Form.Item name="agentCode" label="执行 Agent" extra="能力由该 Agent 执行，运行时参数可在下方覆盖">
            <Select
              placeholder="选择执行体"
              allowClear
              showSearch
              optionFilterProp="label"
              options={resourceOptions.agents}
              onChange={handleAgentChange}
            />
          </Form.Item>
        </>
      ),
    },
    // 运行时覆盖面板
    {
      key: 'override',
      label: '运行时覆盖（可选，未配置则使用 Agent 默认值）',
      forceRender: true,
      children: (
        <>
          <Form.Item name="model" label="模型">
            <Select
              placeholder="留空使用 Agent 默认模型"
              allowClear
              showSearch
              optionFilterProp="label"
              options={resourceOptions.models}
            />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item name="temperature" label="温度">
              <InputNumber min={0} max={2} step={0.1} placeholder="0.3" style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="maxTokens" label="最大 Token">
              <InputNumber min={1} placeholder="2048" style={{ width: 130 }} />
            </Form.Item>
            <Form.Item name="maxIterations" label="最大迭代">
              <InputNumber min={1} placeholder="3" style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Form.Item name="tools" label="工具">
            <CategoryGroupTreeSelect
              categories={resourceOptions.toolCategoryNodes}
              options={resourceOptions.customTools}
              placeholder="留空使用 Agent 默认工具"
            />
          </Form.Item>
          <Form.Item name="skills" label="技能">
            <CategoryGroupTreeSelect
              categories={resourceOptions.skillCategoryNodes}
              options={resourceOptions.skills}
              placeholder="留空使用 Agent 默认技能"
            />
          </Form.Item>
          <Form.Item name="replaceSkills" label="替换技能" valuePropName="checked" extra="开启后仅使用上面选择的技能，关闭则在其 Agent 默认技能上追加">
            <Switch />
          </Form.Item>
          <Form.Item name="kbCodes" label="知识库">
            <Select
              mode="multiple"
              placeholder="选择能力可检索的知识库"
              allowClear
              showSearch
              optionFilterProp="label"
              options={resourceOptions.knowledgeBases}
            />
          </Form.Item>
          <Form.Item
            name="kbUploadCode"
            label="文件上传库"
            extra="指定接收调用方上传文件的知识库，需属于已选知识库；不指定则该能力不支持文件上传"
          >
            <Select
              placeholder="不指定则不支持文件上传"
              allowClear
              showSearch
              optionFilterProp="label"
              options={resourceOptions.knowledgeBases.filter(
                (k) => !selectedKbCodes?.length || selectedKbCodes.includes(k.value),
              )}
            />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item name="kbTopK" label="Top K">
              <InputNumber min={1} max={50} placeholder="5" style={{ width: 100 }} />
            </Form.Item>
            <Form.Item name="kbScoreThreshold" label="相似度阈值">
              <InputNumber min={0} max={1} step={0.05} placeholder="0.7" style={{ width: 130 }} />
            </Form.Item>
          </Space>
          <Form.Item
            name="promptTemplateContent"
            label="提示词模板"
            extra="支持引用能力参数占位，留空则由引擎按能力描述自动组装"
          >
            <Input.TextArea rows={4} placeholder="提示词模板内容" />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'contract',
      label: '对外契约（外部调用方依据此了解入参/出参结构）',
      forceRender: true,
      children: (
        <>
          <Form.Item
            name="inputSchemaContent"
            label={schemaLabel('输入 Schema（JSON）', 'inputSchemaContent')}
            rules={[{ validator: jsonValidator }]}
            extra='描述 arguments 的结构与校验规则，如 {"type":"object","properties":{...}}'
          >
            <Input.TextArea rows={6} placeholder='{"type":"object","properties":{}}' />
          </Form.Item>
          <Form.Item
            name="outputSchemaContent"
            label={schemaLabel('输出 Schema（JSON）', 'outputSchemaContent')}
            rules={[{ validator: jsonValidator }]}
            extra="描述结构化输出的字段结构，严格模式下引擎会校验并修复"
          >
            <Input.TextArea rows={6} placeholder='{"type":"object","properties":{}}' />
          </Form.Item>
          <Form.Item name="inputSchemaDescription" hidden>
            <Input />
          </Form.Item>
          <Form.Item name="outputSchemaDescription" hidden>
            <Input />
          </Form.Item>
        </>
      ),
    },
    {
      key: 'advanced',
      label: '高级设置（执行/输出契约/上下文/审计）',
      forceRender: true,
      children: (
        <>
          <Form.Item name="version" label="版本">
            <Input placeholder="1.0.0" />
          </Form.Item>
          <Form.Item
            name="execMode"
            label="执行模式"
            extra="社区版仅支持同步调用；异步/流式调用与企业版工作流执行体均为企业版增强能力"
          >
            <Select
              style={{ width: 220 }}
              options={[{ label: '同步 sync', value: 'sync' }]}
            />
          </Form.Item>
          <Space size="middle" wrap>
            <Form.Item name="execTimeoutSeconds" label="超时（秒）">
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="retryMaxAttempts" label="重试次数">
              <InputNumber min={1} max={10} style={{ width: 110 }} />
            </Form.Item>
            <Form.Item name="retryBackoffMillis" label="重试间隔（毫秒）">
              <InputNumber min={0} style={{ width: 150 }} />
            </Form.Item>
          </Space>
          <Space size="large" wrap>
            <Form.Item name="contractStrict" label="严格输出校验" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="contractAutoRepair" label="输出自动修复" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="contextRequired" label="必须提供数据上下文" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="contextMaxContexts" label="上下文数量上限">
              <InputNumber min={1} style={{ width: 110 }} />
            </Form.Item>
          </Space>
          <Space size="large" wrap>
            <Form.Item name="auditEnabled" label="审计记录" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="auditLogInput" label="记录入参" valuePropName="checked">
              <Switch />
            </Form.Item>
            <Form.Item name="auditLogOutput" label="记录出参" valuePropName="checked">
              <Switch />
            </Form.Item>
          </Space>
        </>
      ),
    },
    ...(editing
      ? [
          {
            key: 'history',
            label: '版本历史（每次保存自动快照，可回滚）',
            children: (
              <Table
                size="small"
                rowKey="id"
                loading={historyLoading}
                dataSource={historyList}
                pagination={{ pageSize: 5, hideOnSinglePage: true, size: 'small' }}
                columns={[
                  {
                    title: '版本',
                    dataIndex: 'version',
                    width: 90,
                    render: (v: string) => v || '-',
                  },
                  {
                    title: '类型',
                    dataIndex: 'operation',
                    width: 80,
                    render: (v: string) => HISTORY_OPERATION_LABELS[v] ?? v ?? '-',
                  },
                  {
                    title: '快照时间',
                    dataIndex: 'createTime',
                    width: 170,
                    render: (v: string) => formatHistoryTime(v),
                  },
                  {
                    title: '操作',
                    key: 'actions',
                    width: 130,
                    render: (_: unknown, record: CapabilityDefinitionHistory) => (
                      <Space size={0}>
                        <Button type="link" size="small" onClick={() => setHistoryPreview(record)}>
                          查看
                        </Button>
                        <Button type="link" size="small" onClick={() => handleHistoryRollback(record)}>
                          回滚
                        </Button>
                      </Space>
                    ),
                  },
                ]}
              />
            ),
          },
        ]
      : []),
  ];

  return (
    <Drawer
      title={editing ? `编辑能力：${editing.code}` : '新增能力'}
      width={640}
      open={open}
      onClose={onClose}
      destroyOnClose
      extra={
        <Space>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={confirmLoading} onClick={handleSubmit}>
            保存
          </Button>
        </Space>
      }
    >
      <Form form={form} layout="vertical" initialValues={initialValues}>
        <Collapse defaultActiveKey={['base', 'contract']} items={collapseItems} />
      </Form>
      <SchemaEditorModal
        open={!!jsonEditor}
        title={jsonEditor?.title ?? ''}
        initialValue={jsonEditor ? (form.getFieldValue(jsonEditor.field) as string | undefined) : undefined}
        initialDescription={
          jsonEditor
            ? (form.getFieldValue(
                jsonEditor.field === 'inputSchemaContent' ? 'inputSchemaDescription' : 'outputSchemaDescription',
              ) as string | undefined)
            : undefined
        }
        onSave={handleJsonSave}
        onCancel={() => setJsonEditor(null)}
      />
      <Modal
        open={!!historyPreview}
        title={`版本快照：${historyPreview?.version || '-'}（${HISTORY_OPERATION_LABELS[historyPreview?.operation ?? ''] ?? '-'} · ${formatHistoryTime(historyPreview?.createTime)}）`}
        footer={null}
        width={760}
        onCancel={() => setHistoryPreview(null)}
      >
        <pre
          style={{
            maxHeight: '60vh',
            overflow: 'auto',
            margin: 0,
            padding: 12,
            background: '#f6f8fa',
            borderRadius: 6,
            fontSize: 12,
            fontFamily: 'JetBrains Mono, Consolas, monospace',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}
        >
          {formatSnapshot(historyPreview?.definitionJson)}
        </pre>
      </Modal>
    </Drawer>
  );
};
