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
import type { HttpRequest } from './http';
import { createAgentApi } from './agent-api';
import { createApprovalApi } from './approval-api';
import { createConversationApi } from './conversation-api';
import { createConnectorApi } from './connector-api';
import { createEvaluationApi } from './evaluation-api';
import { createEvolutionApi } from './evolution-api';
import { createGraphApi } from './graph-api';
import { createIntegrationApi } from './integration-api';
import { createKnowledgeApi } from './knowledge-api';
import { createMemoryApi } from './memory-api';
import { createModelApi } from './model-api';
import { createOpenCapabilityApi } from './open-capability-api';
import { createPromptApi } from './prompt-api';
import { createRegistryApi } from './registry-api';
import { createSkillApi } from './skill-api';
import { createSystemApi } from './system-api';
import { createTenantApi } from './tenant-api';
import { createToolApi } from './tool-api';
import { createTraceApi } from './trace-api';
import { createToolPolicyApi } from './tool-policy-api';
import { createWebhookApi } from './webhook-api';
import { createWorkflowApi } from './workflow-api';
import { createWorkspaceApi } from './workspace-api';

export type { HttpRequest } from './http';
export { createAgentApi } from './agent-api';
export { createApprovalApi } from './approval-api';
export { createConversationApi } from './conversation-api';
export { createConnectorApi } from './connector-api';
export { createEvaluationApi } from './evaluation-api';
export { createEvolutionApi } from './evolution-api';
export { createGraphApi } from './graph-api';
export { createIntegrationApi } from './integration-api';
export { createKnowledgeApi } from './knowledge-api';
export { createMemoryApi } from './memory-api';
export { createModelApi } from './model-api';
export { createOpenCapabilityApi } from './open-capability-api';
export { createPromptApi } from './prompt-api';
export { createRegistryApi } from './registry-api';
export { createSkillApi } from './skill-api';
export { createSystemApi } from './system-api';
export { createTenantApi } from './tenant-api';
export { createToolApi } from './tool-api';
export { createTraceApi } from './trace-api';
export { createToolPolicyApi } from './tool-policy-api';
export { createWorkflowApi } from './workflow-api';
export { createWorkspaceApi } from './workspace-api';
export type {
  WorkspaceDocxParagraph,
  WorkspaceDocxTable,
  WorkspaceExcelSheet,
  WorkspaceDirectoryItem,
  WorkspaceFilePreview,
  WorkspaceItem,
  WorkspaceListEnvelope,
  NormalizedWorkspaceList,
  WorkspaceSaveParams,
  WorkspaceTrashItem,
  WorkspaceTreeNode,
  WorkspaceType,
  WorkspaceUpdateParams,
  WorkspaceZipEntry,
} from './workspace-api';
export { normalizeWorkspaceList } from './workspace-api';

/**
 * 创建全部 domain API 服务
 * @param http 各 app 注入的统一 HTTP 请求实例（已由拦截器解包业务数据）
 * @return
 */
export const createApiServices = (http: HttpRequest) => ({
  tenant: createTenantApi(http),
  agent: createAgentApi(http),
  tool: createToolApi(http),
  skill: createSkillApi(http),
  model: createModelApi(http),
  knowledge: createKnowledgeApi(http),
  graph: createGraphApi(http),
  workflow: createWorkflowApi(http),
  conversation: createConversationApi(http),
  connector: createConnectorApi(http),
  evaluation: createEvaluationApi(http),
  evolution: createEvolutionApi(http),
  approval: createApprovalApi(http),
  openCapability: createOpenCapabilityApi(http),
  system: createSystemApi(http),
  memory: createMemoryApi(http),
  prompt: createPromptApi(http),
  integration: createIntegrationApi(http),
  registry: createRegistryApi(http),
  trace: createTraceApi(http),
  toolPolicy: createToolPolicyApi(http),
  webhook: createWebhookApi(http),
  workspace: createWorkspaceApi(http),
});

export type ApiServices = ReturnType<typeof createApiServices>;
