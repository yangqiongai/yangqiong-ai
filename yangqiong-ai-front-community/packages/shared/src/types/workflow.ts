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
 * 工作流相关类型
 * 字段与后端实体对齐：WorkflowDefinitionEntity / WorkflowExecutionHistoryEntity (ai-data/workflow/entity，继承 ScopeEntity)
 * WorkflowDefinitionSaveRequest / WorkflowDefinitionUpdateRequest / WorkflowExecuteRequest / WorkflowExecuteResult (ai-workflow/api/dto)
 */

/**
 * 工作流节点
 * 对应后端 com.maizi.ai.workflow.model.WorkflowNode
 */
export interface WorkflowNode {
  nodeId: string;
  nodeType?: string;
  nodeName?: string;
  config?: Record<string, unknown>;
  [key: string]: unknown;
}

/**
 * 工作流连接
 * 对应后端 com.maizi.ai.workflow.model.WorkflowEdge
 */
export interface WorkflowEdge {
  edgeId?: string;
  sourceNodeId: string;
  targetNodeId: string;
  condition?: string;
  [key: string]: unknown;
}

/**
 * 工作流定义（运行时模型）
 * 对应后端 com.maizi.ai.workflow.model.WorkflowDefinition
 */
export interface WorkflowDefinition {
  name: string;
  description?: string;
  nodes?: WorkflowNode[];
  edges?: WorkflowEdge[];
  stateConfig?: Record<string, unknown>;
  errorStrategy?: string;
  maxRetries?: number;
  nodeTimeoutSeconds?: number;
}

/**
 * 工作流定义实体
 * 对应后端 com.maizi.ai.data.workflow.entity.WorkflowDefinitionEntity（继承 ScopeEntity）
 */
export interface WorkflowDefinitionEntity {
  id?: number;
  definitionName: string;
  displayName?: string;
  description?: string;
  category?: string;
  version?: number;
  definitionJson?: string;
  status?: number;
  remark?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 工作流执行历史实体
 * 对应后端 com.maizi.ai.data.workflow.entity.WorkflowExecutionHistoryEntity（继承 ScopeEntity）
 */
export interface WorkflowExecutionHistoryEntity {
  id?: number;
  instanceId: string;
  definitionName?: string;
  definitionVersion?: number;
  status?: string;
  userId?: string;
  inputSummary?: string;
  outputSummary?: string;
  errorMessage?: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 工作流定义保存请求
 * 对应后端 com.maizi.ai.workflow.api.dto.WorkflowDefinitionSaveRequest
 */
export interface WorkflowDefinitionSaveRequest {
  definitionName: string;
  displayName?: string;
  description?: string;
  category?: string;
  definition?: Record<string, unknown>;
  remark?: string;
}

/**
 * 工作流定义更新请求
 * 对应后端 com.maizi.ai.workflow.api.dto.WorkflowDefinitionUpdateRequest
 */
export interface WorkflowDefinitionUpdateRequest {
  displayName?: string;
  description?: string;
  category?: string;
  definition?: Record<string, unknown>;
  remark?: string;
}

/**
 * 工作流执行请求
 * 对应后端 com.maizi.ai.workflow.api.dto.WorkflowExecuteRequest
 */
export interface WorkflowExecuteRequest {
  definition?: Record<string, unknown>;
  definitionName?: string;
  version?: number;
  userId?: string;
  sessionId?: string;
  input?: string;
  params?: Record<string, unknown>;
  initialVariables?: Record<string, unknown>;
}

/**
 * 工作流节点执行状态
 * 对应后端 com.yangqiongai.ai.workflow.model.NodeExecutionStatus
 */
export interface WorkflowNodeExecutionStatus {
  nodeId?: string;
  nodeName?: string;
  status?: string;
  startTime?: number;
  endTime?: number;
  errorMessage?: string;
  retryCount?: number;
  iterationCount?: number;
}

/**
 * 工作流实例状态
 * 对应后端 com.yangqiongai.ai.workflow.model.WorkflowState
 */
export interface WorkflowInstanceStatus {
  instanceId?: string;
  definitionName?: string;
  status?: string;
  nodeStates?: Record<string, WorkflowNodeExecutionStatus>;
  variables?: Record<string, unknown>;
  createTime?: number;
  updateTime?: number;
  lastHeartbeatTime?: number;
  definitionVersion?: number;
  cancelRequested?: boolean;
  pausedNodeId?: string;
  pendingRequestId?: string;
}

/**
 * 工作流定义校验结果
 * 对应后端 WorkflowInstanceController.validate 返回结构
 */
export interface WorkflowValidateResult {
  valid: boolean;
  message?: string;
}

/**
 * 后端 MyBatis-Plus 分页返回结构
 * 对应 com.baomidou.mybatisplus.extension.plugins.pagination.Page
 */
export interface MybatisPageResult<T = unknown> {
  records?: T[];
  total?: number;
  size?: number;
  current?: number;
  pages?: number;
}

/**
 * 工作流节点执行轨迹实体
 * 对应后端 com.yangqiongai.ai.data.workflow.entity.WorkflowNodeExecutionEntity（继承 ScopeEntity）
 */
export interface WorkflowNodeTraceEntity {
  id?: number;
  instanceId: string;
  nodeId: string;
  nodeName?: string;
  nodeType?: string;
  executionOrder?: number;
  status?: string;
  /**
   * 节点输入(JSON字符串,超4KB截断)
   */
  inputData?: string;
  /**
   * 节点输出(JSON字符串,超4KB截断)
   */
  outputData?: string;
  errorMessage?: string;
  retryCount?: number;
  iterationCount?: number;
  branchTaken?: string;
  startTime?: string;
  endTime?: string;
  durationMs?: number;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * 工作流执行历史查询参数
 * 对应后端 WorkflowHistoryController.listHistory 入参
 */
export interface WorkflowHistoryQuery {
  pageNum?: number;
  pageSize?: number;
  definitionName?: string;
  status?: string;
}

/**
 * 工作流执行结果
 * 对应后端 com.maizi.ai.workflow.api.dto.WorkflowExecuteResult
 */
export interface WorkflowExecuteResult {
  success: boolean;
  instanceId?: string;
  definitionName?: string;
  status?: string;
  output?: unknown[];
  errorMessage?: string;
  variables?: Record<string, unknown>;
  nodeSummaries?: unknown[];
  totalDurationMs?: number;
  paused?: boolean;
  pendingRequestId?: string;
}

/**
 * 工作流暂停/恢复控制请求
 * 对应后端 WorkflowControlRequest，暂停与恢复均可记录操作人与原因
 */
export interface WorkflowControlRequest {
  /**
   * 操作人
   */
  operator?: string;

  /**
   * 操作原因
   */
  reason?: string;
}

/**
 * 工作流暂停恢复流水记录
 * 对应后端 com.yangqiongai.ai.workflow.model.WorkflowPauseHistory
 */
export interface WorkflowPauseHistory {
  id?: string;
  scopeId?: string;
  instanceId?: string;
  definitionName?: string;

  /**
   * 暂停时所在节点（外部手动暂停时为空）
   */
  pausedNodeId?: string | null;

  /**
   * 动作：PAUSE=暂停，RESUME=恢复
   */
  action?: 'PAUSE' | 'RESUME';

  /**
   * 操作原因
   */
  reason?: string | null;

  /**
   * 操作人（时间控制自动恢复时为 system）
   */
  operator?: string | null;

  /**
   * 操作时间（毫秒时间戳）
   */
  operatorTime?: number | null;

  /**
   * 审批类暂停的待办请求ID
   */
  pendingRequestId?: string | null;

  /**
   * 暂停等待时长毫秒（仅恢复记录有值）
   */
  waitDurationMs?: number | null;

  createTime?: number | null;
}

