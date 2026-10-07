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
import { useCallback, useEffect, useRef, useState } from 'react';
import type { App } from 'vue';
import '@yq/workflow/style.css';
import type { WorkflowDefinition, WorkflowEditorExecutionApi } from '@yq/workflow';
/**
 * 编辑器桥接属性
 */
export interface WorkflowEditorBridgeProps {
  definition: WorkflowDefinition;
  readonly?: boolean;
  /** 流程显示名称（中文名），导出文件命名优先使用 */
  displayName?: string;
  /** 是否启用企业版专属能力（审批节点等），社区版传 false 时编辑器置灰 */
  enterpriseFeatures?: boolean;
  /** Agent 下拉选项（已注册的 Agent 列表），供画布 Agent 节点配置选择 */
  agentOptions?: Array<{ code: string; name: string }>;
  /** 集成渠道下拉选项，供通知节点配置选择 */
  channelOptions?: Array<{ id: string; name: string; type?: string }>;
  /** 编辑器挂载后回传执行进度控制API，供宿主实时驱动画布节点与路径样式 */
  registerApi?: (api: WorkflowEditorExecutionApi) => void;
  onSave?: (definition: WorkflowDefinition) => void;
  onExecute?: (definition: WorkflowDefinition) => void;
  onUpdate?: (definition: WorkflowDefinition) => void;
  /** 暂停执行（企业版专属能力） */
  onPause?: () => void;
  /** 从暂停位置恢复执行 */
  onResume?: () => void;
  /** 终止执行 */
  onCancel?: () => void;
}

/**
 * 工作流编辑器桥接
 */
export const WorkflowEditorBridge: React.FC<WorkflowEditorBridgeProps> = ({
  definition,
  readonly,
  displayName,
  enterpriseFeatures,
  agentOptions,
  channelOptions,
  registerApi,
  onSave,
  onExecute,
  onUpdate,
  onPause,
  onResume,
  onCancel,
}) => {
  const containerRef = useRef<HTMLDivElement>(null);
  const appRef = useRef<App | null>(null);
  const [mounted, setMounted] = useState(false);
  // registerApi 用ref透传，避免挂载参数随渲染变化重建Vue实例
  const registerApiRef = useRef(registerApi);
  registerApiRef.current = registerApi;
  // 已渲染到画布的定义（整包替换时据此识别，触发Vue实例重建）
  const renderedDefinitionRef = useRef<WorkflowDefinition | null>(null);
  // 最新宿主属性，重建Vue实例时使用最新值
  const latestPropsRef = useRef({ definition, displayName, agentOptions, channelOptions });

  /**
   * 创建Vue编辑器实例并挂载（首次挂载与外部整包替换定义时均调用）
   * @return
   */
  const mountVueApp = useCallback(async () => {
    const container = containerRef.current;
    if (!container) return;
    appRef.current?.unmount();
    appRef.current = null;
    setMounted(false);
    const [workflow, vue] = await Promise.all([import('@yq/workflow'), import('vue')]);
    const target = containerRef.current;
    if (!target) return;
    const latest = latestPropsRef.current;
    const appProps = vue.reactive({
      modelValue: latest.definition,
      readonly: readonly ?? false,
      displayName: latest.displayName ?? '',
      enterpriseFeatures: enterpriseFeatures !== false,
      agentOptions: latest.agentOptions ?? [],
      channelOptions: latest.channelOptions ?? [],
      registerApi: (api: WorkflowEditorExecutionApi) => registerApiRef.current?.(api),
      onSave: (value: WorkflowDefinition) => onSave?.(value),
      onExecute: (value: WorkflowDefinition) => onExecute?.(value),
      'onUpdate:modelValue': (value: WorkflowDefinition) => onUpdate?.(value),
      onPause: () => onPause?.(),
      onResume: () => onResume?.(),
      onCancel: () => onCancel?.(),
    });
    const app = vue.createApp(workflow.WorkflowEditor, appProps);
    app.mount(target);
    appRef.current = app;
    renderedDefinitionRef.current = latest.definition;
    setMounted(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 首次挂载，卸载时销毁实例
  useEffect(() => {
    void mountVueApp();
    return () => {
      appRef.current?.unmount();
      appRef.current = null;
      renderedDefinitionRef.current = null;
      setMounted(false);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 外部definition被整包替换（如AI生成定义应用到画布）时，直接修改根组件props不生效，重建Vue实例刷新画布
  useEffect(() => {
    latestPropsRef.current = { definition, displayName, agentOptions, channelOptions };
    if (!appRef.current) return;
    if (renderedDefinitionRef.current === definition) return;
    void mountVueApp();
  }, [definition, displayName, agentOptions, channelOptions]);

  return (
    <div
      ref={containerRef}
      style={{ width: '100%', height: '100%', visibility: mounted ? 'visible' : 'hidden' }}
    />
  );
};
