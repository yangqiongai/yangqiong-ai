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
import { useMemo, useRef, useState } from 'react';
import { useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { App, Button, Card, Drawer, Space, Spin, Tag, Tooltip, Typography } from 'antd';
import { ArrowLeftOutlined, ClockCircleOutlined, RobotOutlined } from '@ant-design/icons';
import { EnterpriseLockIcon } from '@yangqiong/shared';
import { formatDuration } from '@yangqiong/shared/workflow';
import { api } from '@/services';
import { WorkflowEditorBridge } from '@/components/workflow/WorkflowEditorBridge';
import { WorkflowDesignPanel } from './WorkflowDesignPanel';
import type {
  WorkflowDefinition as EditorWorkflowDefinition,
  WorkflowEditorExecutionApi,
} from '@yq/workflow';

const { Text } = Typography;

/**
 * 流程变量展示键名友好化：节点ID前缀替换为节点名称，自定义变量归属到写入它的节点
 * 仅影响展示，数据层变量名不变，下游 ${var} 引用不受影响
 * @param variables
 * @param definition
 * @return
 */
function friendlyVariableKeys(
  variables: Record<string, unknown>,
  definition: EditorWorkflowDefinition | null,
): Record<string, unknown> {
  if (!variables || !definition) return variables;
  const nameById = new Map<string, string>();
  for (const node of definition.nodes ?? []) {
    if (node?.id) nameById.set(node.id, node.name || node.id);
  }
  // 自定义输出变量归属：outputMappings右值与transform的outputVar -> 写入节点
  const ownerByVar = new Map<string, string>();
  for (const node of definition.nodes ?? []) {
    if (!node?.id) continue;
    const record = node as unknown as Record<string, unknown>;
    const mappings = record.outputMappings as Record<string, string> | undefined;
    for (const varName of Object.values(mappings ?? {})) {
      if (varName && !varName.includes('.')) ownerByVar.set(varName, node.id);
    }
    const outputVar = (record.config as Record<string, unknown> | undefined)?.outputVar;
    if (typeof outputVar === 'string' && outputVar && !outputVar.includes('.')) {
      ownerByVar.set(outputVar, node.id);
    }
  }
  // 最长ID优先匹配，避免ID间前缀互相误替换
  const ids = [...nameById.keys()].sort((a, b) => b.length - a.length);
  const renamed: Record<string, unknown> = {};
  for (const [key, value] of Object.entries(variables)) {
    let displayKey = key;
    const hitId = ids.find((id) => key === id || key.startsWith(`${id}.`));
    if (hitId) {
      const rest = key === hitId ? '' : key.slice(hitId.length);
      displayKey = `${nameById.get(hitId)}${rest}`;
    } else if (ownerByVar.has(key)) {
      displayKey = `${nameById.get(ownerByVar.get(key)!)}.${key}`;
    }
    renamed[displayKey] = value;
  }
  return renamed;
}

/**
 * 节点执行摘要（后端 WorkflowExecuteResult.NodeSummary）
 */
interface NodeSummaryLike {
  nodeId?: string;

  /**
   * 节点名称
   */
  nodeName?: string;

  /**
   * 节点类型
   */
  nodeType?: string;

  /**
   * 执行状态
   */
  status?: string;

  /**
   * 节点耗时
   */
  durationMs?: number;

  /**
   * 错误信息
   */
  errorMessage?: string;

  /**
   * 节点输入（结构化）
   */
  input?: Record<string, unknown>;

  /**
   * 节点输出（结构化）
   */
  output?: Record<string, unknown>;
}

/**
 * 工作流设计
 */
export const WorkflowEditPage: React.FC = () => {
  const { name } = useParams<{ name: string }>();
  const [searchParams] = useSearchParams();
  // 列表页带入的显示名（中文名称），未携带时回退为定义编码
  const displayName = searchParams.get('displayName') ?? '';

  // URL 未带显示名时，从定义列表接口兜底查询显示名（名称跨版本一致）
  const { data: definitionMeta, isFetching: isMetaFetching } = useQuery({
    queryKey: ['workflow-definition-meta', name],
    queryFn: async () => {
      const res = await api.workflow.definition.list({ page: 1, size: 500 });
      return res.list.find((r) => r.definitionName === name);
    },
    enabled: !!name && !displayName,
  });
  const resolvedDisplayName = displayName || definitionMeta?.displayName || '';
  const navigate = useNavigate();
  const { message, modal } = App.useApp();
  const queryClient = useQueryClient();

  // 产品版本标识（社区版构建注入 community，企业版注入 enterprise），控制审批等企业版专属能力
  const isEnterprise = import.meta.env.VITE_EDITION === 'enterprise';

  const latestDefinitionRef = useRef<EditorWorkflowDefinition | null>(null);
  // 编辑器执行进度控制API（挂载后经registerApi回传），流式执行时实时驱动画布
  const execApiRef = useRef<WorkflowEditorExecutionApi | null>(null);
  // 流式执行互斥标志，防止执行中重复点击
  const executingRef = useRef(false);
  // 当前流式执行的实例ID（WORKFLOW_STARTED事件回传），供暂停/终止/恢复控制接口使用
  const activeInstanceIdRef = useRef<string | null>(null);
  // AI 对话式流程设计抽屉开关与应用的画布定义（应用后整包替换编辑器画布）
  const [aiDrawerOpen, setAiDrawerOpen] = useState(false);
  const [appliedDefinition, setAppliedDefinition] = useState<EditorWorkflowDefinition | null>(null);

  const { data, isLoading, isError, error } = useQuery({
    queryKey: ['workflow-definition', name],
    queryFn: () => api.workflow.definition.get(name as string),
    enabled: !!name,
  });

  // Agent 下拉选项（已注册 Agent 列表），供画布 Agent 节点配置选择
  const { data: agentTypePage, isLoading: isAgentsLoading } = useQuery({
    queryKey: ['agent-type-options'],
    queryFn: () => api.agent.type.list({ page: 1, size: 999 }),
    staleTime: 5 * 60 * 1000,
  });

  const agentOptions = useMemo(
    () =>
      (agentTypePage?.list ?? []).map((item) => ({
        code: item.typeCode,
        name: item.typeName || item.typeCode,
      })),
    [agentTypePage],
  );

  // 集成渠道下拉选项，供通知节点配置选择（企业版专属能力）
  const { data: channelPage } = useQuery({
    queryKey: ['integration-channel-options'],
    queryFn: () => api.integration.channels.page({ pageNum: 1, pageSize: 999 }),
    enabled: isEnterprise,
    staleTime: 5 * 60 * 1000,
  });

  const channelOptions = useMemo(
    () =>
      (channelPage?.records ?? [])
        .filter((item) => item.enabled === 1)
        .map((item) => ({
          id: String(item.id),
          name: `#${item.id}`,
          type: item.channelType,
        })),
    [channelPage],
  );

  // 后端运行时定义适配编辑器入参（nodes/edges 必填兜底）
  const initialDefinition = useMemo<EditorWorkflowDefinition | undefined>(() => {
    if (!data) return undefined;
    const adapted = {
      ...data,
      nodes: data.nodes ?? [],
      edges: data.edges ?? [],
    } as unknown as EditorWorkflowDefinition;
    latestDefinitionRef.current = adapted;
    return adapted;
  }, [data]);

  // 编辑器当前渲染的定义：AI 应用的定义优先，否则为后端加载的定义
  const editorDefinition = appliedDefinition ?? initialDefinition;

  const saveMutation = useMutation({
    mutationFn: async (definition: EditorWorkflowDefinition) => {
      // 保存前先走后端校验，与前端校验形成双保险
      const validateResult = await api.workflow.validateWorkflowDefinition(
        definition as unknown as Record<string, unknown>,
      );
      if (!validateResult.valid) {
        throw new Error(validateResult.message || '工作流定义校验未通过');
      }
      return api.workflow.definition.update(name as string, {
        definition: definition as unknown as Record<string, unknown>,
      });
    },
    onSuccess: () => {
      message.success('保存成功');
      queryClient.invalidateQueries({ queryKey: ['workflow-definition-list'] });
      queryClient.invalidateQueries({ queryKey: ['workflow-definition', name] });
    },
    onError: (err) => {
      modal.error({
        title: '保存失败',
        content: err instanceof Error ? err.message : '保存失败',
      });
    },
  });

  // 展示执行结果（节点轨迹 + 流程变量）
  const showExecuteResult = (result: {
    success?: boolean;
    status?: string;
    totalDurationMs?: number;
    errorMessage?: string;
    nodeSummaries?: NodeSummaryLike[];
    variables?: Record<string, unknown>;
  }) => {
    if (result.success) {
      const summaries = (result.nodeSummaries ?? []) as NodeSummaryLike[];
      modal.success({
        title: `执行成功：${result.status ?? 'COMPLETED'}，总耗时 ${formatDuration(result.totalDurationMs)}`,
        width: 680,
        content: (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {summaries.length > 0 && (
              <div>
                <div style={{ fontWeight: 600, marginBottom: 6 }}>节点执行轨迹</div>
                {summaries.map((s, index) => (
                  <div
                    key={s.nodeId ?? index}
                    style={{ display: 'flex', gap: 8, alignItems: 'baseline' }}
                  >
                    <Tag
                      color={
                        s.status === 'COMPLETED'
                          ? 'success'
                          : s.status === 'SKIPPED'
                            ? 'default'
                            : 'warning'
                      }
                    >
                      {s.status ?? 'UNKNOWN'}
                    </Tag>
                    <span style={{ fontWeight: 500 }}>{s.nodeName ?? s.nodeId}</span>
                    <Text type="secondary">{s.nodeType}</Text>
                    <Text type="secondary">{formatDuration(s.durationMs)}</Text>
                    {s.errorMessage && <Text type="danger">{s.errorMessage}</Text>}
                  </div>
                ))}
              </div>
            )}
            {result.variables && Object.keys(result.variables).length > 0 && (
              <div>
                <div style={{ fontWeight: 600, marginBottom: 6 }}>流程变量</div>
                <pre
                  style={{
                    margin: 0,
                    maxHeight: 200,
                    overflow: 'auto',
                    background: '#f6f8fa',
                    padding: 8,
                    borderRadius: 6,
                    fontSize: 12,
                  }}
                >
                  {JSON.stringify(
                    friendlyVariableKeys(
                      result.variables as Record<string, unknown>,
                      latestDefinitionRef.current ?? (initialDefinition as EditorWorkflowDefinition | null),
                    ),
                    null,
                    2,
                  )}
                </pre>
              </div>
            )}
          </div>
        ),
      });
    } else {
      modal.error({
        title: `执行失败：${result.status ?? 'FAILED'}`,
        width: 560,
        content: result.errorMessage ?? '未知错误',
      });
    }
  };

  // 流式执行：SSE 事件实时驱动画布节点与路径样式，结束事件展示完整结果
  const handleExecute = async () => {
    const definition = latestDefinitionRef.current ?? initialDefinition;
    if (!definition || executingRef.current) return;
    executingRef.current = true;
    const editorApi = execApiRef.current;
    editorApi?.resetExecution();
    editorApi?.setExecuting(true);
    const hide = message.loading('正在执行工作流，可在画布查看执行进度…', 0);
    try {
      await api.workflow.executeStream(
        { definition: definition as unknown as Record<string, unknown> },
        (eventType, event) => {
          const nodeId = event.nodeId;
          if (eventType === 'WORKFLOW_STARTED' && event.instanceId) {
            activeInstanceIdRef.current = event.instanceId;
          } else if (eventType === 'NODE_START' && nodeId) {
            editorApi?.setNodeStatus(nodeId, 'running');
          } else if (eventType === 'NODE_COMPLETE' && nodeId) {
            editorApi?.setNodeStatus(nodeId, 'completed');
          } else if (eventType === 'NODE_ERROR' && nodeId) {
            editorApi?.setNodeStatus(nodeId, 'failed');
          } else if (eventType === 'NODE_PAUSED' && nodeId) {
            editorApi?.setNodeStatus(nodeId, 'paused');
          } else if (eventType === 'NODE_SKIP' && nodeId) {
            editorApi?.setNodeStatus(nodeId, 'skipped');
          } else if (eventType === 'WORKFLOW_COMPLETE') {
            try {
              const result = event.payload
                ? (JSON.parse(event.payload) as Parameters<typeof showExecuteResult>[0])
                : null;
              // 暂停/终止为受控结束，定制提示并同步工具栏按钮状态，不按失败弹窗展示
              if (result?.status === 'PAUSED') {
                editorApi?.setPaused(true);
                message.info('流程已暂停，可点击「继续」从暂停位置恢复执行');
              } else if (result?.status === 'CANCELLED') {
                message.warning('流程已终止，已执行节点结果已保留');
              } else if (result) {
                // 回填各节点本次执行的输入/输出，供画布节点面板执行详情展示
                const execData: Record<
                  string,
                  { status?: string; input?: Record<string, unknown>; output?: Record<string, unknown>; durationMs?: number; errorMessage?: string }
                > = {};
                for (const s of result.nodeSummaries ?? []) {
                  if (s?.nodeId) {
                    execData[s.nodeId] = {
                      status: s.status,
                      input: s.input,
                      output: s.output,
                      durationMs: s.durationMs,
                      errorMessage: s.errorMessage,
                    };
                  }
                }
                editorApi?.setNodeExecData(execData);
                showExecuteResult(result);
              } else {
                modal.success({ title: '执行完成' });
              }
            } catch {
              modal.success({ title: '执行完成', content: '执行结果解析失败' });
            }
          }
        },
      );
    } catch (err) {
      modal.error({
        title: '执行失败',
        content: err instanceof Error ? err.message : '执行请求失败',
      });
    } finally {
      hide();
      editorApi?.setExecuting(false);
      executingRef.current = false;
    }
  };

  // 暂停执行：后端在当前节点执行完后于下一节点边界暂停（企业版专属）
  const handlePause = async () => {
    const instanceId = activeInstanceIdRef.current;
    if (!instanceId) {
      message.warning('未获取到执行实例，无法暂停');
      return;
    }
    try {
      const ok = await api.workflow.pauseWorkflow(instanceId);
      if (!ok) message.warning('暂停请求未生效，流程可能已结束');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '暂停请求失败');
    }
  };

  // 从暂停位置恢复执行：已完成节点不重复执行，被中断节点重新执行（企业版专属）
  const handleResume = async () => {
    const instanceId = activeInstanceIdRef.current;
    if (!instanceId) return;
    const editorApi = execApiRef.current;
    try {
      editorApi?.setPaused(false);
      editorApi?.resetExecution();
      editorApi?.setExecuting(true);
      const result = await api.workflow.resumeWorkflow(instanceId);
      const success = (result as { success?: boolean })?.success !== false;
      if (success) {
        modal.success({ title: '恢复执行完成', content: '工作流已从暂停位置继续并执行完毕' });
      } else {
        modal.error({
          title: '恢复执行失败',
          content: (result as { errorMessage?: string })?.errorMessage ?? '未知错误',
        });
      }
    } catch (err) {
      modal.error({
        title: '恢复执行失败',
        content: err instanceof Error ? err.message : '恢复请求失败',
      });
    } finally {
      editorApi?.setExecuting(false);
    }
  };

  // 终止执行：在当前节点执行完后停止，保留已执行节点结果
  const handleCancel = () => {
    const instanceId = activeInstanceIdRef.current;
    if (!instanceId) {
      message.warning('未获取到执行实例，无法终止');
      return;
    }
    modal.confirm({
      title: '确认终止执行',
      content: '终止后当前节点执行完毕即停止，已执行节点结果将保留。是否继续？',
      okText: '终止',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        try {
          const ok = await api.workflow.cancelWorkflow(instanceId);
          if (!ok) message.warning('终止请求未生效，流程可能已结束');
        } catch (err) {
          message.error(err instanceof Error ? err.message : '终止请求失败');
        }
      },
    });
  };

  // 编辑器实例挂载后不会响应 props 更新，需等 Agent 列表及显示名兜底查询完成再挂载
  if (isLoading || isAgentsLoading || isMetaFetching) {
    return (
      <Card>
        <div style={{ display: 'flex', justifyContent: 'center', padding: 80 }}>
          <Spin size="large" />
        </div>
      </Card>
    );
  }

  if (isError || !initialDefinition) {
    return (
      <Card
        title="工作流设计"
        extra={
          <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workflows')}>
            返回
          </Button>
        }
      >
        <Text type="danger">
          {error instanceof Error ? error.message : '工作流定义加载失败'}
        </Text>
      </Card>
    );
  }

  return (
    <div style={{ height: '100%' }}>
      <Card
        style={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}
        title={
          <Space>
            <span>流程设计：{resolvedDisplayName || initialDefinition.name}</span>
            <Tag>{name}</Tag>
          </Space>
        }
        extra={
          <Space>
            {/* AI 设计为企业版专属：社区版入口禁用置灰仅作展示，企业版经fork页面承载启用态 */}
            <Tooltip title={isEnterprise ? undefined : 'AI 设计为企业版专属功能，当前版本不可用'}>
              <span>
                <Button
                  icon={<RobotOutlined />}
                  disabled={!isEnterprise}
                  onClick={() => setAiDrawerOpen(true)}
                >
                  AI 设计 <EnterpriseLockIcon style={{ color: '#d48806' }} />
                </Button>
              </span>
            </Tooltip>
            {/* 定时任务为企业版专属：社区版入口禁用置灰，企业版经fork页面承载启用态 */}
            <Tooltip title="定时任务为企业版专属功能，当前版本不可用">
              <span>
                <Button icon={<ClockCircleOutlined />} disabled={!isEnterprise}>
                  定时设置 <EnterpriseLockIcon style={{ color: '#d48806' }} />
                </Button>
              </span>
            </Tooltip>
            <Button icon={<ArrowLeftOutlined />} onClick={() => navigate('/workflows')}>
              返回
            </Button>
          </Space>
        }
        styles={{ body: { padding: 0, flex: 1, minHeight: 0 } }}
      >
        <div style={{ height: '100%' }}>
          {editorDefinition && (
            <WorkflowEditorBridge
              key={name}
              definition={editorDefinition}
              displayName={resolvedDisplayName}
              enterpriseFeatures={isEnterprise}
              agentOptions={agentOptions}
              channelOptions={channelOptions}
              registerApi={(api) => {
                execApiRef.current = api;
              }}
              onUpdate={(value) => {
                latestDefinitionRef.current = value;
              }}
              onSave={(value) => saveMutation.mutate(value)}
              onExecute={() => handleExecute()}
              onPause={() => void handlePause()}
              onResume={() => void handleResume()}
              onCancel={handleCancel}
            />
          )}
        </div>
      </Card>
      <Drawer
        title="AI 流程设计"
        width={520}
        open={aiDrawerOpen}
        onClose={() => setAiDrawerOpen(false)}
      >
        <div style={{ height: '100%' }}>
          <WorkflowDesignPanel
            definitionName={name}
            onApply={(definition) => {
              setAppliedDefinition(definition);
              setAiDrawerOpen(false);
              message.success('已应用 AI 生成的流程到画布，可继续手动微调或保存');
            }}
          />
        </div>
      </Drawer>
    </div>
  );
};
