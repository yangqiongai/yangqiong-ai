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

/**
 * 工作流Agent节点预设模板契约（与 @yq/workflow 包 WorkflowAgentPreset 结构对齐，
 * 此处独立声明避免共享包反向依赖社区编辑器包）
 */

/**
 * Agent节点专属字段定义
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
  default?: unknown
  /** number类型最小值 */
  min?: number
  /** number类型最大值 */
  max?: number
  /** textarea行数 */
  rows?: number
}

/**
 * Agent节点预设动作按钮定义（与 @yq/workflow 包 AgentPresetAction 结构对齐）
 */
export interface AgentPresetAction {
  /** 动作编码，点击时随事件回传宿主 */
  key: string
  /** 按钮文案 */
  label: string
}

/**
 * 预设动作事件负载（与 @yq/workflow 包 AgentPresetActionPayload 结构对齐）
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
 * Agent节点预设模板
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
