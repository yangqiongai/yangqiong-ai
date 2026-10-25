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

export { default as WorkflowEditor } from './WorkflowEditor.vue'
export { default as WorkflowTraceViewer } from './WorkflowTraceViewer.vue'

export type {
  NodeType,
  EdgeType,
  ExecutionStatus,
  ErrorStrategy,
  JoinType,
  TransformType,
  ScriptType,
  HttpMethod,
  NodePosition,
  WorkflowNode,
  WorkflowEdge,
  StateConfig,
  WorkflowDefinition,
  NodeApprovalConfig,
  TimeControlConfig,
  NodeExecutionStatus,
  WorkflowState,
  WorkflowStreamEvent,
  WorkflowExecuteResult,
  NodeSummary,
  WorkflowExecuteRequest,
  WorkflowNodeTraceItem,
  WorkflowNodeExecStatus,
  WorkflowNodeExecData,
  WorkflowEditorExecutionApi,
  AgentPresetField,
  AgentPresetAction,
  AgentPresetActionPayload,
  WorkflowAgentPreset,
} from './types/workflow'

export {
  toVueFlowNodes,
  toVueFlowEdges,
  fromVueFlowNodes,
  fromVueFlowEdges,
  toWorkflowDefinition,
  fromWorkflowDefinition,
} from './utils/schema-adapter'

export {
  validateDefinition,
  detectCycle,
  validateNodeCompleteness,
  validateConnectivity,
} from './utils/validator'

export type { ValidationResult } from './utils/validator'
