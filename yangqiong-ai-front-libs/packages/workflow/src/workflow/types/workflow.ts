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

export type NodeType = 'AGENT' | 'CONDITION' | 'PARALLEL' | 'LOOP' | 'SUBGRAPH' | 'TRANSFORM' | 'SCRIPT' | 'HTTP' | 'ASSIGN' | 'APPROVAL' | 'NOTIFY' | 'TIME_CONTROL' | 'START' | 'END'
export type EdgeType = 'NORMAL' | 'CONDITIONAL' | 'PARALLEL'
export type ExecutionStatus = 'PENDING' | 'RUNNING' | 'PAUSED' | 'COMPLETED' | 'FAILED' | 'CANCELLED'
export type ErrorStrategy = 'STOP' | 'SKIP' | 'RETRY'
export type JoinType = 'ALL' | 'ANY'
export type TransformType = 'SUBSTRING' | 'REVERSE' | 'UPPER' | 'LOWER' | 'TRIM' | 'REPLACE' | 'CONCAT' | 'TEMPLATE' | 'LENGTH' | 'MATH'
export type ScriptType = 'SPEL' | 'JS' | 'GROOVY'
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE'
export type RejectBehavior = 'FAIL' | 'SKIP' | 'RETRY'

export interface NodePosition {
  x: number
  y: number
}

export interface NodeApprovalConfig {
  reason?: string
  options?: string[]
  inputFields?: string[]
  timeoutSeconds?: number
  rejectBehavior?: RejectBehavior
}

/**
 * 时间控制配置（TIME_CONTROL节点主配置）
 */
export interface TimeControlConfig {
  /**
   * 时间模式：DELAY延迟秒数 / COUNTDOWN倒计时 / SPECIFIC具体时间 / PERIODIC周期性时间 / CRON cron表达式
   */
  timeType?: 'DELAY' | 'COUNTDOWN' | 'SPECIFIC' | 'PERIODIC' | 'CRON'

  /**
   * 延迟秒数（DELAY模式）
   */
  delaySeconds?: number

  /**
   * 倒计时分钟数（COUNTDOWN模式）
   */
  countdownMinutes?: number

  /**
   * 倒计时秒数（COUNTDOWN模式）
   */
  countdownSeconds?: number

  /**
   * 具体时间，格式yyyy-MM-dd HH:mm:ss（SPECIFIC模式）
   */
  specificTime?: string

  /**
   * cron表达式，5段"分 时 日 月 周"或6段"秒 分 时 日 月 周"（CRON模式）
   */
  cronExpression?: string

  /**
   * 周期类型：DAILY每天 / WEEKLY每周 / MONTHLY每月（PERIODIC模式）
   */
  periodType?: 'DAILY' | 'WEEKLY' | 'MONTHLY'

  /**
   * 周期执行时间点，格式HH:mm（PERIODIC模式）
   */
  periodTime?: string

  /**
   * 每周执行日，1=周一至7=周日（WEEKLY模式）
   */
  periodWeekdays?: number[]

  /**
   * 每月几号执行，1-31（MONTHLY模式）
   */
  periodDayOfMonth?: number

  /**
   * 是否仅在工作日（周一至周五）执行，仅PERIODIC模式生效
   */
  workdayOnly?: boolean
}

export interface WorkflowNode {
  id: string
  name: string
  type: NodeType
  config: Record<string, any>
  inputMappings?: Record<string, string>
  outputMappings?: Record<string, string>
  approvalConfig?: NodeApprovalConfig
  timeControlConfig?: TimeControlConfig
  /**
   * 节点级超时秒数，空值时继承定义级 nodeTimeoutSeconds
   */
  timeoutSeconds?: number

  /**
   * 节点级最大重试次数，空值时继承定义级 maxRetries
   */
  maxRetries?: number
  position?: NodePosition
}

export interface WorkflowEdge {
  id: string
  sourceId: string
  targetId: string
  type: EdgeType
  conditionExpression?: string
  conditionLabel?: string
}

export interface StateConfig {
  persistEnabled: boolean
  ttlHours?: number
}

export interface WorkflowDefinition {
  name: string
  description?: string
  nodes: WorkflowNode[]
  edges: WorkflowEdge[]
  stateConfig?: StateConfig
  errorStrategy?: ErrorStrategy
  maxRetries?: number
  nodeTimeoutSeconds?: number
  version?: number
}

export interface NodeExecutionStatus {
  nodeId: string
  nodeName?: string
  status: ExecutionStatus
  input?: Record<string, any>
  outputData?: Record<string, any>
  output?: any
  startTime?: string
  endTime?: string
  errorMessage?: string
  retryCount?: number
  iterationCount?: number
}

export interface WorkflowState {
  instanceId: string
  definitionName: string
  status: ExecutionStatus
  nodeStates: Record<string, NodeExecutionStatus>
  variables: Record<string, any>
  createTime?: string
  updateTime?: string
  definitionVersion?: number
}

export interface WorkflowStreamEvent {
  nodeId: string
  eventType: string
  payload: string
  timestamp?: string
  instanceId?: string
}

/**
 * 编辑器执行进度节点状态
 */
export type WorkflowNodeExecStatus = 'running' | 'completed' | 'failed' | 'skipped' | 'paused'

/**
 * 节点执行详情（来自最近一次工作流执行结果）
 */
export interface WorkflowNodeExecData {
  status?: string
  input?: Record<string, any>
  output?: Record<string, any>
  durationMs?: number
  errorMessage?: string
}

/**
 * 编辑器执行进度控制API（经registerApi暴露给宿主框架实时驱动画布）
 */
export interface WorkflowEditorExecutionApi {
  /**
   * 重置所有节点与边的执行状态样式
   */
  resetExecution: () => void
  /**
   * 更新节点执行状态并联动入边样式
   * @param nodeId
   * @param status
   */
  setNodeStatus: (nodeId: string, status: WorkflowNodeExecStatus) => void
  /**
   * 设置执行中标志（控制工具栏执行按钮loading）
   * @param executing
   */
  setExecuting: (executing: boolean) => void
  /**
   * 设置暂停标志（控制工具栏暂停/继续按钮切换）
   * @param paused
   */
  setPaused: (paused: boolean) => void
  /**
   * 更新各节点本次执行的输入/输出详情（键为节点ID），供节点面板执行详情展示
   * @param data
   */
  setNodeExecData: (data: Record<string, WorkflowNodeExecData>) => void
}

export interface WorkflowExecuteResult {
  success: boolean
  instanceId: string
  definitionName: string
  status: ExecutionStatus
  outputText: string
  errorMessage?: string
  variables: Record<string, any>
  nodeSummaries: NodeSummary[]
  totalDurationMs: number
}

export interface NodeSummary {
  nodeId: string
  nodeName: string
  nodeType: NodeType
  status: ExecutionStatus
  input?: Record<string, any>
  output?: Record<string, any>
  durationMs: number
  errorMessage?: string
  retryCount?: number
  iterationCount?: number
}

export interface WorkflowExecuteRequest {
  definition?: WorkflowDefinition
  definitionName?: string
  version?: number
  userId?: string
  sessionId?: string
  input?: string
  params?: Record<string, any>
  initialVariables?: Record<string, any>
}

/**
 * Agent节点专属字段定义（预设模板的参数表单项，schema驱动渲染）
 */
export interface AgentPresetField {
  /** 配置键（写入节点config，下划线前缀键执行时透传为请求私有参数） */
  key: string
  /** 表单标签 */
  label: string
  /** 表单类型 */
  type: 'select' | 'text' | 'textarea' | 'number' | 'switch'
  /** select类型的选项列表 */
  options?: Array<{ label: string; value: string }>
  /** 占位提示 */
  placeholder?: string
  /** 表单下方说明 */
  hint?: string
  /** 默认值（应用预设时写入config） */
  default?: any
  /** number类型最小值 */
  min?: number
  /** number类型最大值 */
  max?: number
  /** textarea行数 */
  rows?: number
}

/**
 * 预设动作按钮定义（预设表单下方的扩展操作入口，语义由宿主实现）
 */
export interface AgentPresetAction {
  /** 动作编码，点击时随事件回传宿主 */
  key: string
  /** 按钮文案 */
  label: string
}

/**
 * 预设动作事件负载（点击动作按钮时回传，config为当前表单值快照）
 */
export interface AgentPresetActionPayload {
  /** 动作编码 */
  actionKey: string
  /** 命中的预设编码 */
  presetCode: string
  /** 当前节点config快照 */
  config: Record<string, any>
}

/**
 * Agent节点预设模板（宿主按需注入，未注入时不渲染预设UI，如企业版专属Agent预设）
 */
export interface WorkflowAgentPreset {
  /** 预设编码，应用后写入config.agentCode */
  code: string
  /** 预设名称 */
  name: string
  /** 预设描述 */
  description?: string
  /** 预设图标（emoji） */
  icon?: string
  /** 预设主题色（画布节点左边框与标签着色） */
  color?: string
  /** 预设标签文案（画布节点与配置面板展示，缺省用name） */
  tag?: string
  /** 固定Agent类型（true时配置面板锁定Agent选择与预设切换，用于浏览器节点等定制节点） */
  fixedAgent?: boolean
  /** 渲染工作区选择下拉（本地节点等按工作区执行的定制节点），选项经workspaceOptions注入，选中值写入config._workspaceId */
  workspaceSelect?: boolean
  /** 应用预设时写入节点config的默认值 */
  defaults?: Record<string, any>
  /** 专属参数表单（agentCode命中该预设时渲染） */
  fields?: AgentPresetField[]
  /** 动作按钮（agentCode命中该预设时渲染于参数表单下方，点击经presetAction事件回传宿主） */
  actions?: AgentPresetAction[]
}

export interface WorkflowNodeTraceItem {
  instanceId?: string
  nodeId: string
  nodeName?: string
  nodeType?: NodeType
  /**
   * 执行顺序(从1开始)
   */
  executionOrder?: number
  status?: ExecutionStatus
  /**
   * 节点输入(JSON,超4KB截断)
   */
  inputData?: Record<string, any>
  /**
   * 节点输出(JSON,超4KB截断)
   */
  outputData?: Record<string, any>
  errorMessage?: string
  retryCount?: number
  iterationCount?: number
  /**
   * 条件分支命中值(CONDITION节点)
   */
  branchTaken?: string
  /**
   * 开始时间(毫秒时间戳)
   */
  startTime?: number
  /**
   * 结束时间(毫秒时间戳)
   */
  endTime?: number
  durationMs?: number
  scopeId?: string
}
