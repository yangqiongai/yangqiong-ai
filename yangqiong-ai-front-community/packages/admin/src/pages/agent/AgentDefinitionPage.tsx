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
  App,
  Badge,
  Button,
  Card,
  Col,
  Collapse,
  Descriptions,
  Divider,
  Drawer,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Row,
  Select,
  Slider,
  Space,
  Spin,
  Switch,
  Tabs,
  Tag,
  theme,
  Tooltip,
  TreeSelect,
  Typography,
} from 'antd';
import {
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  PlusOutlined,
  SyncOutlined,
} from '@ant-design/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/services';
import { AgentIconPicker, AgentIconView } from '@/components/AgentIcon';
import { CategoryGroupTreeSelect } from '@/components/CategoryGroupTreeSelect';
import { CategoryTreeService, CategoryTreePanel, UNGROUPED_KEY } from '@/components/CategoryTreePanel';
import { getAppRoutePlugins } from '@/routes/app-plugin';
import { useAuthStore } from '@/store/auth-store';
import { EnterpriseLockIcon, formatDate } from '@yangqiong/shared';
import type { AgentTypeInfo, AgentDirectoryNode } from '@yangqiong/shared';
import {
  BINDING_MODE_OPTIONS,
  configSummary,
  EXECUTION_PARADIGM_OPTIONS,
  EXECUTION_PARADIGM_DESC,
  EXECUTION_PARADIGM_TOKEN_TIP,
  MOUNT_HINT,
  parseAgentConfig,
  REFLECTION_PARADIGM_TYPES,
  stringifyAgentConfig,
  TOKEN_HEAVY_PARADIGM_TYPES,
  tryPrettyJson,
  useAgentResourceOptions,
} from '@yangqiong/shared/agent';
import type {
  AgentConfigStructured,
  AgentResourceOptions,
} from '@yangqiong/shared/agent';
import { AgentScheduleTab } from './AgentScheduleTab';

const AGENT_LIST_KEY = 'agent-runtime-list';

/**
 * 智能体目录树缓存key（列表过滤/表单TreeSelect/树面板共用）
 */
const AGENT_DIRECTORY_KEY = 'agent-directory-tree';

/**
 * 智能体目录数据服务（对接CategoryTreePanel）
 */
const agentDirectoryService: CategoryTreeService = {
  tree: () => api.agent.directory.tree(),
  create: (data) => api.agent.directory.create({ ...data, name: data.name ?? '' }),
  update: (id, data) => api.agent.directory.update(id, { ...data, name: data.name ?? '' }),
  remove: (id) => api.agent.directory.remove(id),
};

/**
 * 社区版锁定的企业版 Tab（标签展示锁定态引导升级，内容不渲染）
 */
const LOCKED_ENTERPRISE_TABS = [
  { key: 'versions', label: '版本与发布' },
  { key: 'config', label: '版本装配清单' },
  { key: 'profile', label: '环境配置档' },
  { key: 'drift', label: '配置漂移' },
  { key: 'package', label: '配置包' },
  { key: 'releases', label: '发布流水' },
];

/**
 * Agent 分类展示
 */
const CATEGORY_OPTIONS = [
  { label: 'CHAT（对话）', value: 'CHAT' },
  { label: 'QA（问答）', value: 'QA' },
  { label: 'EXTRACTION（抽取）', value: 'EXTRACTION' },
  { label: 'REVIEW（审阅）', value: 'REVIEW' },
  { label: 'REPORT（报告）', value: 'REPORT' },
  { label: 'ORCHESTRATION（编排）', value: 'ORCHESTRATION' },
];

/**
 * 会话类型展示
 */
const SESSION_TYPE_OPTIONS = [
  { label: 'CHAT（普通对话）', value: 'CHAT' },
  { label: 'KB_QA（知识库问答）', value: 'KB_QA' },
  { label: 'DOC_QA（文档问答）', value: 'DOC_QA' },
  { label: 'ORCHESTRATION（编排）', value: 'ORCHESTRATION' },
];

/**
 * 目录树转TreeSelect数据（value取目录编码，与挂载字段directory_code对齐）
 */
interface DirectoryTreeSelectNode {
  title: string;
  value: string;
  children?: DirectoryTreeSelectNode[];
}

const toDirectoryTreeData = (nodes: AgentDirectoryNode[]): DirectoryTreeSelectNode[] =>
  nodes.map((node) => ({
    title: node.name,
    value: node.code,
    children: toDirectoryTreeData(node.children ?? []),
  }));

/**
 * Agent 表单值（四段式）
 */
interface AgentFormValues {
  agentCode: string;
  agentName: string;
  description?: string;
  icon?: string;
  category?: string;

  /**
   * 所属目录编码（空串=未分类）
   */
  directoryCode?: string;
  sessionType?: string;
  sortOrder?: number;
  status: boolean;
  remark?: string;
  config?: AgentConfigStructured;
}

/**
 * 挂载资源选项（共享单源）
 */
type ResourceOptions = AgentResourceOptions;

/**
 * 隐藏值承载控件（仅注册表单字段，不渲染UI）
 */
const HiddenArrayValue: React.FC = () => null;

/**
 * 折叠面板分组标题（左侧强调色条+加粗放大，与字段label区分层级）
 * @param props
 * @return
 */
const SectionLabel: React.FC<{ text: string }> = ({ text }) => {
  const { token } = theme.useToken();
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
      <span style={{ width: 4, height: 16, borderRadius: 2, background: token.colorBorder, display: 'inline-block' }} />
      <span style={{ fontSize: 15, fontWeight: 600, color: token.colorTextHeading }}>{text}</span>
    </span>
  );
};

/**
 * agentConfig 结构化字段组（运行参数 + 能力挂载四域，多处复用）
 * @param props
 * @return
 */
const AgentConfigFormItems: React.FC<{
  options: ResourceOptions;
  prefix?: string[];
  part?: 'runtime' | 'mount' | 'all';
  disabled?: boolean;
}> = ({ options, prefix, part = 'all', disabled }) => {
  const form = Form.useFormInstance();
  const nameOf = (name: string): string | (string | number)[] =>
    prefix?.length ? [...prefix, name] : name;
  const kbPrefix = prefix?.length ? [...prefix, 'knowledgeBase'] : ['knowledgeBase'];
  const reasoningPrefix = prefix?.length ? [...prefix, 'reasoning'] : ['reasoning'];
  const showRuntime = part !== 'mount';
  const showMount = part !== 'runtime';

  // 按所选知识库编码同步名称到kbNames冗余字段，便于配置JSON直读
  const syncKbNames = (codes?: string[]) => {
    const names = (codes ?? [])
      .map((code) => options.knowledgeBases.find((kb) => kb.value === code)?.name)
      .filter((v): v is string => Boolean(v));
    form.setFieldValue([...kbPrefix, 'kbNames'], names);
  };

  useEffect(() => {
    // 编辑载入时按已有kbCodes补齐名称展示字段
    const codes = form.getFieldValue([...kbPrefix, 'kbCodes']) as string[] | undefined;
    if (codes?.length) {
      syncKbNames(codes);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [options.knowledgeBases]);

  /**
   * 基础能力面板（模型/提示词/工具/知识库等常用配置，各part均含基础字段）
   */
  const basicPanel = (
    <>
      {showRuntime && (
        <>
          <Form.Item name={nameOf('model')} label="模型" extra="留空使用请求方指定或系统默认模型">
            <Select
              placeholder="选择默认模型"
              options={options.models}
              showSearch
              optionFilterProp="label"
              allowClear
              disabled={disabled}
            />
          </Form.Item>
          <Form.Item name={nameOf('systemPrompt')} label="系统提示词">
            <Input.TextArea rows={4} placeholder="该 Agent 的系统提示词" disabled={disabled} />
          </Form.Item>
          <Form.Item name={nameOf('temperature')} label="温度（0~2）">
            <Slider min={0} max={2} step={0.1} style={{ width: 320 }} disabled={disabled} />
          </Form.Item>
          <Form.Item name={nameOf('maxIterations')} label="最大迭代次数" extra="留空使用处理器默认值">
            <InputNumber min={1} max={100} style={{ width: 200 }} disabled={disabled} />
          </Form.Item>
          <Form.Item noStyle shouldUpdate>
            {({ getFieldValue }) => {
              // 所选模型不支持推理时，推理相关配置整体禁用
              const selectedModel = options.modelItems.find(
                (m) => m.modelCode === getFieldValue(nameOf('model')),
              );
              const reasoningLocked = selectedModel?.supportReasoning === 0;
              return (
                <>
                  <Form.Item
                    name={[...reasoningPrefix, 'enabled']}
                    label="推理模式"
                    valuePropName="checked"
                    extra={
                      reasoningLocked
                        ? '当前所选模型不支持推理，已禁用推理配置'
                        : '开启后请求默认携带推理参数；是否输出思考内容取决于所选模型能力'
                    }
                  >
                    <Switch disabled={disabled || reasoningLocked} />
                  </Form.Item>
                  <Form.Item
                    name={[...reasoningPrefix, 'effort']}
                    label="推理力度"
                    extra="仅在推理模式开启时生效，默认 medium"
                  >
                    <Select
                      placeholder="默认 medium"
                      options={[
                        { label: 'low', value: 'low' },
                        { label: 'medium', value: 'medium' },
                        { label: 'high', value: 'high' },
                      ]}
                      allowClear
                      disabled={
                        disabled ||
                        reasoningLocked ||
                        !getFieldValue([...reasoningPrefix, 'enabled'])
                      }
                    />
                  </Form.Item>
                  <Form.Item
                    name={[...reasoningPrefix, 'showThinking']}
                    label="展示思考过程"
                    valuePropName="checked"
                    initialValue={true}
                    extra="关闭后对话界面隐藏思维链（不影响模型内部推理）"
                  >
                    <Switch disabled={disabled || reasoningLocked} />
                  </Form.Item>
                </>
              );
            }}
          </Form.Item>
          <Form.Item
            name={nameOf('streamOutput')}
            label="流式输出"
            valuePropName="checked"
            initialValue={true}
            extra="关闭后对话界面强制使用非流式返回"
          >
            <Switch disabled={disabled} />
          </Form.Item>
        </>
      )}
      {showMount && (
        <>
          {part === 'mount' && (
            <Alert type="info" showIcon message={MOUNT_HINT} style={{ marginBottom: 16 }} />
          )}
          <Form.Item name={nameOf('tools')} label="工具（仅 CUSTOM 分类，内置工具自动加载）">
            <CategoryGroupTreeSelect
              categories={options.toolCategoryNodes}
              options={options.customTools}
              placeholder="未选择 = 仅内置工具自动加载"
              disabled={disabled}
            />
          </Form.Item>
          <Form.Item name={nameOf('mcpServers')} label="MCP 服务（启用中）">
            <CategoryGroupTreeSelect
              categories={options.mcpCategoryNodes}
              options={options.mcpServers}
              placeholder="未选择 = 不挂载 MCP"
              disabled={disabled}
            />
          </Form.Item>
          <Form.Item name={nameOf('skills')} label="技能（非 BUILTIN 信任等级，内置技能自动加载）">
            <CategoryGroupTreeSelect
              categories={options.skillCategoryNodes}
              options={options.skills}
              placeholder="未选择 = 仅内置技能自动加载"
              disabled={disabled}
            />
          </Form.Item>
          <Form.Item name={[...kbPrefix, 'kbCodes']} label="知识库">
            <Select
              mode="multiple"
              placeholder="未选择 = 不注入知识上下文"
              options={options.knowledgeBases}
              showSearch
              optionFilterProp="label"
              allowClear
              disabled={disabled}
              onChange={(vals) => syncKbNames(vals as string[])}
            />
          </Form.Item>
          <Form.Item name={[...kbPrefix, 'kbNames']} hidden>
            <HiddenArrayValue />
          </Form.Item>
          <Form.Item
            name={nameOf('topK')}
            label="知识检索 topK"
            extra="默认 5；检索在请求时立即执行并注入 knowledgeContext"
          >
            <InputNumber min={1} max={50} style={{ width: 200 }} disabled={disabled} />
          </Form.Item>
          <Form.Item
            name={nameOf('rerankEnabled')}
            label="检索重排序"
            valuePropName="checked"
            extra="默认关闭；开启后检索结果经大模型重排再注入，全局总开关关闭时不生效"
          >
            <Switch disabled={disabled} />
          </Form.Item>
          <Form.Item name={nameOf('rerankModelCode')} label="重排模型" extra="留空与主模型一致">
            <Select
              placeholder="留空与主模型一致"
              options={options.models}
              showSearch
              optionFilterProp="label"
              allowClear
              disabled={disabled}
            />
          </Form.Item>
        </>
      )}
    </>
  );

  /**
   * 高级能力面板（处理器/执行范式/绑定模式/插件挂载，各part均含高级字段）
   */
  const advancedPanel = (
    <>
      {showRuntime && (
        <>
          <Form.Item name={nameOf('processor')} label="处理器" extra="缺省按Agent编码路由，未匹配走默认处理器">
            <Select
              placeholder="默认路由"
              options={options.processors}
              showSearch
              optionFilterProp="label"
              allowClear
              disabled={disabled}
            />
          </Form.Item>
          <Form.Item noStyle shouldUpdate>
            {({ getFieldValue }) => {
              // 执行范式：反思类范式需展示反思/修订次数，规划类范式仅步数
              const paradigmPath = nameOf('executionParadigm');
              const paradigm = getFieldValue(
                Array.isArray(paradigmPath) ? [...paradigmPath, 'type'] : [paradigmPath, 'type'],
              ) as string | undefined;
              const isReflection = REFLECTION_PARADIGM_TYPES.includes(paradigm ?? '');
              // Token高消耗范式（反思/自我精炼/问题分解）展示用量提示，规划类范式调用次数固定不提示
              const isTokenHeavy = TOKEN_HEAVY_PARADIGM_TYPES.includes(paradigm ?? '');
              return (
                <>
                  <Form.Item
                    name={Array.isArray(paradigmPath) ? [...paradigmPath, 'type'] : [paradigmPath, 'type']}
                    label="执行范式"
                    extra={paradigm ? EXECUTION_PARADIGM_DESC[paradigm] : '不配置时默认使用 ReAct 推理行动循环'}
                  >
                    <Select
                      placeholder="默认 ReAct（推理行动）"
                      options={EXECUTION_PARADIGM_OPTIONS.map((opt) => ({ label: opt.label, value: opt.value }))}
                      allowClear
                      disabled={disabled}
                    />
                  </Form.Item>
                  {paradigm && paradigm !== 'react' && (
                    <Form.Item
                      name={Array.isArray(paradigmPath) ? [...paradigmPath, 'maxSteps'] : [paradigmPath, 'maxSteps']}
                      label="范式最大步数"
                      extra="带工具的最大执行步数，默认 10"
                    >
                      <InputNumber min={1} max={50} style={{ width: 200 }} disabled={disabled} />
                    </Form.Item>
                  )}
                  {paradigm === 'reflexion' && (
                    <Form.Item
                      name={
                        Array.isArray(paradigmPath)
                          ? [...paradigmPath, 'maxReflections']
                          : [paradigmPath, 'maxReflections']
                      }
                      label="最大反思次数"
                      extra="评估未通过时的反思重试上限，默认 2"
                    >
                      <InputNumber min={0} max={5} style={{ width: 200 }} disabled={disabled} />
                    </Form.Item>
                  )}
                  {paradigm === 'self-refine' && (
                    <Form.Item
                      name={
                        Array.isArray(paradigmPath)
                          ? [...paradigmPath, 'maxRefinements']
                          : [paradigmPath, 'maxRefinements']
                      }
                      label="最大修订次数"
                      extra="批评不满意的修订上限，默认 2"
                    >
                      <InputNumber min={0} max={5} style={{ width: 200 }} disabled={disabled} />
                    </Form.Item>
                  )}
                  {isTokenHeavy && (
                    <Alert
                      type="warning"
                      showIcon
                      style={{ marginBottom: 16 }}
                      message={EXECUTION_PARADIGM_TOKEN_TIP}
                      description={
                        isReflection
                          ? '反思范式说明：每轮反思将从原始输入重新执行，已完成的工具调用会重复执行，调用成本随反思轮次增加；评估由主模型自评完成。'
                          : undefined
                      }
                    />
                  )}
                </>
              );
            }}
          </Form.Item>
        </>
      )}
      {showMount && (
        <>
          {part === 'all' && (
            <Form.Item name={nameOf('bindingMode')} label="绑定模式">
              <Select
                placeholder="默认 append"
                options={BINDING_MODE_OPTIONS}
                allowClear
                disabled={disabled}
              />
            </Form.Item>
          )}
          {getAppRoutePlugins()
            .flatMap((p) => p.agentMountFields ?? [])
            .map((Field, index) => (
              <Field key={index} prefix={prefix} options={options} disabled={disabled} />
            ))}
        </>
      )}
    </>
  );

  return (
    <Collapse
      ghost
      defaultActiveKey={['basic']}
      // forceRender 强制挂载收起面板内的表单字段，避免提交时 validateFields 丢失未展开面板的值
      items={[
        {
          key: 'basic',
          label: <SectionLabel text="基础能力" />,
          forceRender: true,
          children: basicPanel,
        },
        {
          key: 'advanced',
          label: <SectionLabel text="高级能力" />,
          forceRender: true,
          children: advancedPanel,
        },
      ]}
    />
  );
};

/**
 * Agent 管理
 */
export const AgentDefinitionPage: React.FC = () => {
  const { message } = App.useApp();
  const queryClient = useQueryClient();
  const loginUserId = useAuthStore((s) => s.user?.id);

  // 插件注入的详情扩展能力：同 key Tab 覆盖锁定占位，governance 操作覆盖锁定按钮（企业版注入，社区版保持锁定态）
  const pluginDetailTabs = getAppRoutePlugins().flatMap((p) => p.agentDetailTabs ?? []);
  const governanceAction = getAppRoutePlugins()
    .flatMap((p) => p.agentDetailActions ?? [])
    .find((a) => a.key === 'governance');
  const GovernanceRenderer = governanceAction?.render;

  const [collapsed, setCollapsed] = useState(false);
  const [selectedCode, setSelectedCode] = useState<string | null>(null);

  // 列表筛选与前端分页
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');
  const [statusFilter, setStatusFilter] = useState<number | undefined>();
  const [categoryFilter, setCategoryFilter] = useState<string | undefined>();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(10);

  // 智能体目录树（默认显示，Card右上角按钮折叠，交互与列表折叠一致）
  const [directoryVisible, setDirectoryVisible] = useState(true);
  const [directoryFilter, setDirectoryFilter] = useState<string | undefined>();

  // 新建/编辑 Drawer
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingAgent, setEditingAgent] = useState<AgentTypeInfo | null>(null);
  const [form] = Form.useForm<AgentFormValues>();

  // 能力挂载 Tab 表单
  const [mountForm] = Form.useForm<AgentConfigStructured>();

  const [configModalOpen, setConfigModalOpen] = useState(false);
  const [configText, setConfigText] = useState('');

  const resourceOptions = useAgentResourceOptions(api, drawerOpen || !!selectedCode);

  const invalidateAgents = () => {
    queryClient.invalidateQueries({ queryKey: [AGENT_LIST_KEY] });
    // 同步失效对话页的Agent列表缓存，保证输出行为配置保存后立即生效
    queryClient.invalidateQueries({ queryKey: ['agent-types'] });
    // 挂载关系变化后同步刷新目录树计数
    queryClient.invalidateQueries({ queryKey: [AGENT_DIRECTORY_KEY] });
  };

  // 运行时全量列表（权威主源）
  const agentsQuery = useQuery({
    queryKey: [AGENT_LIST_KEY],
    queryFn: () => api.agent.type.list({ page: 1, size: 1000 }),
  });

  // 目录树数据（树面板/表单TreeSelect/列表过滤共用缓存）
  const directoryTreeQuery = useQuery({
    queryKey: [AGENT_DIRECTORY_KEY],
    queryFn: () => api.agent.directory.tree(),
    enabled: directoryVisible || drawerOpen || !!directoryFilter,
  });

  // 目录编码 -> 名称映射（概览展示用）
  const directoryNameMap = useMemo(() => {
    const map = new Map<string, string>();
    const walk = (node: AgentDirectoryNode) => {
      map.set(node.code, node.name);
      (node.children ?? []).forEach(walk);
    };
    (directoryTreeQuery.data?.nodes ?? []).forEach(walk);
    return map;
  }, [directoryTreeQuery.data]);

  // 选中目录的过滤范围（含子孙目录的编码集合；UNGROUPED_KEY 表示未分类）
  const directoryScopeCodes = useMemo(() => {
    if (!directoryFilter || directoryFilter === UNGROUPED_KEY) {
      return null;
    }
    const codes = new Set<string>();
    const walk = (node: AgentDirectoryNode) => {
      codes.add(node.code);
      (node.children ?? []).forEach(walk);
    };
    const find = (nodes: AgentDirectoryNode[]): AgentDirectoryNode | null => {
      for (const node of nodes) {
        if (node.code === directoryFilter) return node;
        const hit = find(node.children ?? []);
        if (hit) return hit;
      }
      return null;
    };
    const target = find(directoryTreeQuery.data?.nodes ?? []);
    if (target) {
      walk(target);
    }
    return codes;
  }, [directoryFilter, directoryTreeQuery.data]);

  const selectedAgent = useMemo(
    () => (agentsQuery.data?.list ?? []).find((a) => a.typeCode === selectedCode) ?? null,
    [agentsQuery.data, selectedCode],
  );

  // 能力挂载：选中 Agent 变化时先清空再回填表单，避免 setFieldsValue 合并残留上一个 Agent 的配置
  useEffect(() => {
    mountForm.resetFields();
    mountForm.setFieldsValue(parseAgentConfig(selectedAgent?.agentConfig));
  }, [selectedAgent?.typeCode, selectedAgent?.agentConfig, mountForm]);

  // 运行状态快捷切换（后端 toggle 语义）
  const toggleStatusMutation = useMutation({
    mutationFn: (agentCode: string) => api.agent.type.toggleStatus(agentCode),
    onSuccess: () => {
      message.success('操作成功');
      invalidateAgents();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '操作失败'),
  });

  const syncMutation = useMutation({
    mutationFn: () => api.agent.type.sync(),
    onSuccess: () => {
      message.success('同步完成');
      invalidateAgents();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '同步失败'),
  });

  /**
   * 新建/编辑保存：运行时 agent 全字段
   */
  const saveAgentMutation = useMutation({
    mutationFn: async (values: AgentFormValues) => {
      const agentPayload: Omit<AgentTypeInfo, 'id' | 'updateTime'> = {
        typeCode: values.agentCode,
        typeName: values.agentName,
        typeDescription: values.description,
        typeIcon: values.icon,
        typeCategory: values.category,
        // TreeSelect清空(undefined)视为移至未分类，提交空串保留序列化
        directoryCode: values.directoryCode ?? '',
        typeStatus: values.status ? 1 : 0,
        typeOrder: values.sortOrder,
        sessionType: values.sessionType,
        // 重排序开关默认关闭，未配置时显式写入false
        agentConfig: stringifyAgentConfig({
          ...(values.config ?? {}),
          rerankEnabled: values.config?.rerankEnabled ?? false,
        }),
        remark: values.remark,
      };
      if (editingAgent) {
        await api.agent.type.update(editingAgent.typeCode, agentPayload);
      } else {
        await api.agent.type.create(agentPayload);
      }
    },
    onSuccess: () => {
      message.success(editingAgent ? 'Agent 已更新' : 'Agent 已创建');
      setDrawerOpen(false);
      setEditingAgent(null);
      invalidateAgents();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '保存失败'),
  });

  /**
   * 能力挂载保存：四域清单单源直写运行时 agentConfig
   */
  const saveMountMutation = useMutation({
    mutationFn: ({ agentCode, configJson }: { agentCode: string; configJson: string }) =>
      api.agent.type.update(agentCode, { agentConfig: configJson }),
    onSuccess: () => {
      message.success('能力挂载已保存，保存即生效');
      invalidateAgents();
    },
    onError: (err) => message.error(err instanceof Error ? err.message : '保存失败'),
  });

  /**
   * 保存能力挂载四域清单
   */
  const handleSaveMount = async () => {
    if (!selectedAgent) return;
    const values = await mountForm.validateFields().catch(() => null);
    if (!values) return;
    await saveMountMutation.mutateAsync({
      agentCode: selectedAgent.typeCode,
      // 重排序开关默认关闭，未配置时显式写入false
      configJson: stringifyAgentConfig({ ...values, rerankEnabled: values.rerankEnabled ?? false }),
    }).catch(() => undefined);
  };

  const handleSearch = () => {
    setKeyword(searchKeyword);
    setPage(1);
  };

  const openCreateAgent = () => {
    setEditingAgent(null);
    form.resetFields();
    form.setFieldsValue({ status: true, config: {} });
    setDrawerOpen(true);
  };

  const openEditAgent = (agent: AgentTypeInfo) => {
    setEditingAgent(agent);
    form.resetFields();
    form.setFieldsValue({
      agentCode: agent.typeCode,
      agentName: agent.typeName,
      description: agent.typeDescription,
      icon: agent.typeIcon,
      category: agent.typeCategory,
      directoryCode: agent.directoryCode,
      sessionType: agent.sessionType,
      sortOrder: agent.typeOrder,
      status: agent.typeStatus === 1,
      remark: agent.remark,
      config: parseAgentConfig(agent.agentConfig),
    });
    setDrawerOpen(true);
  };

  // 列表数据：运行时主源 + 前端筛选排序
  const mergedRows = useMemo(() => {
    const list = agentsQuery.data?.list ?? [];
    return list
      .filter((a) => {
        if (statusFilter != null && a.typeStatus !== statusFilter) return false;
        if (categoryFilter && a.typeCategory !== categoryFilter) return false;
        // 目录过滤：未分类=无目录编码，指定目录=该目录及子孙目录
        if (directoryFilter === UNGROUPED_KEY) {
          if (a.directoryCode) return false;
        } else if (directoryScopeCodes && !directoryScopeCodes.has(a.directoryCode ?? '')) {
          return false;
        }
        if (keyword) {
          const kw = keyword.toLowerCase();
          if (![a.typeName, a.typeCode].some((v) => v != null && v.toLowerCase().includes(kw))) {
            return false;
          }
        }
        return true;
      })
      .sort((a, b) => (a.typeOrder ?? 0) - (b.typeOrder ?? 0));
  }, [agentsQuery.data, keyword, statusFilter, categoryFilter, directoryFilter, directoryScopeCodes]);

  const pagedRows = useMemo(
    () => mergedRows.slice((page - 1) * size, page * size),
    [mergedRows, page, size],
  );

  const categoryOptions = useMemo(() => {
    const categories = new Set(
      (agentsQuery.data?.list ?? []).map((a) => a.typeCategory).filter(Boolean) as string[],
    );
    return Array.from(categories).map((c) => ({ label: c, value: c }));
  }, [agentsQuery.data]);

  // 概览 Tab
  const overviewTab = selectedAgent ? (
    <div>
      <Card size="small" title="基础属性" style={{ marginBottom: 16 }}>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="编码">{selectedAgent.typeCode}</Descriptions.Item>
          <Descriptions.Item label="名称">{selectedAgent.typeName}</Descriptions.Item>
          <Descriptions.Item label="描述" span={2}>
            {selectedAgent.typeDescription ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="分类">{selectedAgent.typeCategory ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="所属目录">
            {selectedAgent.directoryCode
              ? (directoryNameMap.get(selectedAgent.directoryCode) ?? selectedAgent.directoryCode)
              : '未分类'}
          </Descriptions.Item>
          <Descriptions.Item label="会话类型">{selectedAgent.sessionType ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="排序号">{selectedAgent.typeOrder ?? '-'}</Descriptions.Item>
          <Descriptions.Item label="运行状态">
            <Badge
              status={selectedAgent.typeStatus === 1 ? 'success' : 'default'}
              text={selectedAgent.typeStatus === 1 ? '启用' : '禁用'}
            />
          </Descriptions.Item>
          <Descriptions.Item label="备注" span={2}>
            {selectedAgent.remark ?? '-'}
          </Descriptions.Item>
          <Descriptions.Item label="更新时间">
            {selectedAgent.updateTime ? formatDate(selectedAgent.updateTime) : '-'}
          </Descriptions.Item>
        </Descriptions>
      </Card>
      <Card
        size="small"
        title="运行配置摘要（agentConfig）"
        style={{ marginBottom: 16 }}
        extra={
          <Button
            size="small"
            onClick={() => {
              setConfigText(tryPrettyJson(selectedAgent.agentConfig) ?? '{}');
              setConfigModalOpen(true);
            }}
          >
            查看完整 JSON
          </Button>
        }
      >
        <Typography.Text>{configSummary(parseAgentConfig(selectedAgent.agentConfig))}</Typography.Text>
      </Card>
    </div>
  ) : null;

  // 能力挂载 Tab（四域正向清单直写 agentConfig）
  const mountTab = (
    <div>
      <Alert type="info" showIcon message={MOUNT_HINT} style={{ marginBottom: 12 }} />
      <Form form={mountForm} layout="vertical">
        <AgentConfigFormItems options={resourceOptions} />
      </Form>
      <div
        style={{
          position: 'sticky',
          bottom: 0,
          marginTop: 8,
          paddingTop: 8,
          background: '#fff',
          borderTop: '1px solid #f0f0f0',
          textAlign: 'right',
        }}
      >
        <Button
          type="primary"
          loading={saveMountMutation.isPending}
          onClick={handleSaveMount}
        >
          保存（保存即生效）
        </Button>
      </div>
    </div>
  );

  return (
    <div
      style={{
        display: 'flex',
        gap: 16,
        alignItems: 'stretch',
        minHeight: 0,
        // 固定视口高度，左右各自内部滚动，页面主体不再出现滚动条
        height: 'calc(100vh - 230px)',
      }}
    >
      {/* 智能体目录树（默认显示，Card右上角折叠，交互与列表折叠一致） */}
      {!collapsed && directoryVisible && (
        <div style={{ width: 260, flexShrink: 0, height: '100%', overflow: 'auto' }}>
          <CategoryTreePanel
            title="智能体目录"
            queryKey={AGENT_DIRECTORY_KEY}
            service={agentDirectoryService}
            countOf={(node) => (node as AgentDirectoryNode).agentCount}
            codeExtra="字母/数字/下划线/中划线，保存后不可修改"
            extra={
              <Button size="small" icon={<MenuFoldOutlined />} onClick={() => setDirectoryVisible(false)} />
            }
            value={directoryFilter}
            onChange={(code) => {
              setDirectoryFilter(code);
              setPage(1);
            }}
          />
        </div>
      )}
      {!collapsed && !directoryVisible && (
        <Button
          icon={<MenuUnfoldOutlined />}
          onClick={() => setDirectoryVisible(true)}
          style={{ flexShrink: 0 }}
        />
      )}

      {/* 左侧 Agent 列表 */}
      {collapsed ? (
        <Button icon={<MenuUnfoldOutlined />} onClick={() => setCollapsed(false)} style={{ flexShrink: 0 }} />
      ) : (
        <Card
          title="Agent 列表"
          style={{ width: 380, flexShrink: 0, display: 'flex', flexDirection: 'column' }}
          styles={{
            body: {
              padding: 12,
              overflow: 'hidden',
              flex: 1,
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
            },
          }}
          extra={
            <Space size={4}>
              <Tooltip title="同步运行时（POST /sync）">
                <Button
                  size="small"
                  icon={<SyncOutlined spin={syncMutation.isPending} />}
                  onClick={() => syncMutation.mutate()}
                />
              </Tooltip>
              <Button type="primary" size="small" icon={<PlusOutlined />} onClick={openCreateAgent}>
                新建 Agent
              </Button>
              <Button size="small" icon={<MenuFoldOutlined />} onClick={() => setCollapsed(true)} />
            </Space>
          }
        >
          <Space direction="vertical" style={{ width: '100%', flexShrink: 0 }} size={8}>
            <Space.Compact style={{ width: '100%' }}>
              <Input
                placeholder="编码/名称"
                value={searchKeyword}
                onChange={(e) => setSearchKeyword(e.target.value)}
                onPressEnter={handleSearch}
                allowClear
              />
              <Button type="primary" onClick={handleSearch}>
                查询
              </Button>
            </Space.Compact>
            <Space style={{ width: '100%' }}>
              <Select
                allowClear
                placeholder="运行状态"
                style={{ width: 120 }}
                value={statusFilter}
                onChange={(v) => {
                  setStatusFilter(v);
                  setPage(1);
                }}
                options={[
                  { label: '启用', value: 1 },
                  { label: '禁用', value: 0 },
                ]}
              />
              <Select
                allowClear
                placeholder="分类"
                style={{ width: 140 }}
                value={categoryFilter}
                onChange={(v) => {
                  setCategoryFilter(v);
                  setPage(1);
                }}
                options={categoryOptions}
              />
            </Space>
          </Space>
          <div style={{ marginTop: 8, flex: 1, minHeight: 0, overflow: 'auto' }}>
            {agentsQuery.isLoading && (
              <div style={{ textAlign: 'center', padding: 16 }}>
                <Spin />
              </div>
            )}
            {pagedRows.map((agent) => {
              return (
                <div
                  key={agent.typeCode}
                  onClick={() => setSelectedCode(agent.typeCode)}
                  style={{
                    padding: '8px 10px',
                    borderRadius: 6,
                    cursor: 'pointer',
                    background: selectedCode === agent.typeCode ? '#e6f4ff' : undefined,
                    marginBottom: 4,
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 12,
                  }}
                >
                  <AgentIconView icon={agent.typeIcon} size={30} />
                  <div style={{ minWidth: 0, flex: 1, textAlign: 'left' }}>
                    <div style={{ fontWeight: 500 }}>
                      <Badge
                        status={agent.typeStatus === 1 ? 'success' : 'default'}
                        style={{ marginRight: 6 }}
                      />
                      {agent.typeName}
                    </div>
                    <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                      {agent.typeCode} · {agent.typeCategory ?? '-'} · {agent.sessionType ?? '-'}
                    </Typography.Text>
                  </div>
                  <div style={{ flexShrink: 0, textAlign: 'right' }} onClick={(e) => e.stopPropagation()}>
                    <Switch
                      size="small"
                      checked={agent.typeStatus === 1}
                      checkedChildren="启"
                      unCheckedChildren="停"
                      loading={toggleStatusMutation.isPending}
                      onChange={() => toggleStatusMutation.mutate(agent.typeCode)}
                    />
                  </div>
                </div>
              );
            })}
            {!agentsQuery.isLoading && mergedRows.length === 0 && (
              <Empty
                description={
                  <span>
                    暂无运行时 Agent
                    <br />
                    可点击「新建 Agent」或「同步运行时」
                  </span>
                }
              />
            )}
          </div>
          <div style={{ textAlign: 'right', marginTop: 8, flexShrink: 0 }}>
            <Select
              size="small"
              value={size}
              style={{ width: 90 }}
              onChange={(v) => {
                setSize(v);
                setPage(1);
              }}
              options={[10, 20, 50].map((n) => ({ label: `${n} 条/页`, value: n }))}
            />
            <Button size="small" type="link" disabled={page <= 1} onClick={() => setPage((p) => p - 1)}>
              上一页
            </Button>
            <span style={{ fontSize: 12 }}>
              {page} / {Math.max(1, Math.ceil(mergedRows.length / size))}
            </span>
            <Button
              size="small"
              type="link"
              disabled={page * size >= mergedRows.length}
              onClick={() => setPage((p) => p + 1)}
            >
              下一页
            </Button>
          </div>
        </Card>
      )}

      {/* 右侧详情 */}
      <div style={{ flex: 1, minWidth: 0, height: '100%' }}>
        {selectedAgent ? (
          <Card
            title={
              <Space>
                <span>{selectedAgent.typeName}</span>
                <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                  {selectedAgent.typeCode}
                </Typography.Text>
                <Tag color={selectedAgent.typeStatus === 1 ? 'green' : 'default'}>
                  {selectedAgent.typeStatus === 1 ? '启用' : '禁用'}
                </Tag>
              </Space>
            }
            style={{ height: '100%', display: 'flex', flexDirection: 'column' }}
            styles={{
              body: { paddingTop: 8, flex: 1, minHeight: 0, overflow: 'auto' },
            }}
            extra={
              <Space>
                <Switch
                  checkedChildren="启用"
                  unCheckedChildren="禁用"
                  checked={selectedAgent.typeStatus === 1}
                  loading={toggleStatusMutation.isPending}
                  onChange={() => toggleStatusMutation.mutate(selectedAgent.typeCode)}
                />
                {GovernanceRenderer ? (
                  <GovernanceRenderer agentCode={selectedAgent.typeCode} />
                ) : (
                  <Tooltip title="纳入治理为企业版能力">
                    <span>
                      <Button size="small" disabled>
                        纳入治理 <EnterpriseLockIcon style={{ color: '#d48806' }} />
                      </Button>
                    </span>
                  </Tooltip>
                )}
                <Button type="primary" size="small" onClick={() => openEditAgent(selectedAgent)}>
                  编辑 Agent
                </Button>
              </Space>
            }
          >
            <Tabs
              defaultActiveKey="overview"
              items={[
                { key: 'overview', label: '概览', children: overviewTab },
                { key: 'mount', label: '能力挂载', children: mountTab },
                { key: 'schedule', label: '调度任务', children: <AgentScheduleTab agentCode={selectedAgent.typeCode} userId={loginUserId} /> },
                // 锁定占位 Tab：企业插件注入同 key 内容 Tab 后自动覆盖，社区版无注入保持锁定态
                ...LOCKED_ENTERPRISE_TABS.filter(({ key }) => !pluginDetailTabs.some((t) => t.key === key)).map(
                  ({ key, label }) => ({
                    key,
                    label: (
                      <Tooltip title={`${label}为企业版能力`}>
                        <span>{label} <EnterpriseLockIcon style={{ color: '#d48806' }} /></span>
                      </Tooltip>
                    ),
                    disabled: true,
                    children: (
                      <div style={{ padding: 24 }}>
                        <Empty description={`${label}为企业版能力`} />
                      </div>
                    ),
                  }),
                ),
                // 插件注入的企业扩展 Tab（覆盖同 key 锁定占位，社区版无注入则不展示）
                ...pluginDetailTabs.map((tab) => ({
                  key: tab.key,
                  label: tab.label,
                  children: tab.render(selectedAgent.typeCode),
                })),
              ]}
            />
          </Card>
        ) : (
          <Card style={{ height: '100%' }}>
            <Empty description="请选择左侧 Agent 查看详情，或新建 Agent" />
          </Card>
        )}
      </div>

      {/* 新建/编辑 Drawer（四段式） */}
      <Drawer
        title={editingAgent ? `编辑 Agent：${editingAgent.typeName}` : '新建 Agent'}
        placement="right"
        width={680}
        open={drawerOpen}
        onClose={() => {
          setDrawerOpen(false);
          setEditingAgent(null);
        }}
        destroyOnClose
        extra={
          <Button type="primary" loading={saveAgentMutation.isPending} onClick={() => form.submit()}>
            保存
          </Button>
        }
      >
        <Form form={form} layout="vertical" onFinish={(values) => saveAgentMutation.mutate(values)}>
          <Typography.Title level={5}>① 基础属性</Typography.Title>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                name="agentCode"
                label="Agent 编码"
                rules={[
                  { required: true, message: '请输入编码' },
                  { pattern: /^[a-zA-Z0-9_-]+$/, message: '仅允许字母数字下划线中划线' },
                ]}
                extra="创建后不可修改"
              >
                <Input placeholder="如 mineral-advisor" disabled={!!editingAgent} />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                name="agentName"
                label="名称"
                rules={[{ required: true, message: '请输入名称' }]}
              >
                <Input placeholder="请输入名称" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={2} placeholder="请输入描述" />
          </Form.Item>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item name="category" label="分类">
                <Select placeholder="选择分类" options={CATEGORY_OPTIONS} allowClear />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="directoryCode" label="所属目录" extra="清空表示移至未分类">
                <TreeSelect
                  allowClear
                  placeholder="选择所属目录"
                  treeDefaultExpandAll
                  loading={directoryTreeQuery.isLoading}
                  treeData={toDirectoryTreeData(directoryTreeQuery.data?.nodes ?? [])}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item name="sessionType" label="会话类型">
                <Select placeholder="选择会话类型" options={SESSION_TYPE_OPTIONS} allowClear />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item name="icon" label="图标">
                <AgentIconPicker />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="sortOrder" label="排序号">
                <InputNumber min={0} style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="status" label="启用状态" valuePropName="checked">
                <Switch checkedChildren="启用" unCheckedChildren="禁用" />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="请输入备注" />
          </Form.Item>

          <Divider />
          <Typography.Title level={5}>② 运行参数</Typography.Title>
          <AgentConfigFormItems options={resourceOptions} part="runtime" prefix={['config']} />

          <Divider />
          <Typography.Title level={5}>③ 能力挂载（正向清单）</Typography.Title>
          <AgentConfigFormItems options={resourceOptions} part="mount" prefix={['config']} />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            保存即写入 agentConfig 并生效。
          </Typography.Text>
        </Form>
      </Drawer>

      {/* 配置 JSON 只读弹窗 */}
      <Modal
        title="配置快照"
        open={configModalOpen}
        footer={null}
        onCancel={() => setConfigModalOpen(false)}
        width={720}
      >
        <Input.TextArea rows={18} readOnly style={{ fontFamily: 'monospace' }} value={configText} />
      </Modal>
    </div>
  );
};
