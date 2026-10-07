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
 * Agent 注册中心类型
 * 字段与后端实体对齐：AgentDefinition / AgentVersion / AgentGrayRule / AgentRelease (agent-data registry)
 */

/**
 * 版本状态机
 */
export type VersionStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'PUBLISHED'
  | 'REJECTED'
  | 'DEPRECATED';

/**
 * 灰度规则类型
 */
export type GrayRuleType = 'USER_PERCENT' | 'USER_WHITELIST' | 'SCOPE_LIST';

/**
 * MyBatis-Plus 分页返回
 */
export interface MpPage<T> {
  records: T[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

/**
 * Agent 定义（治理层聚合根）
 */
export interface AgentDefinition {
  id?: number;
  agentCode: string;
  agentName: string;
  description?: string;
  category?: string;
  currentVersionId?: number;
  status?: string;
  /**
   * 发布是否需要审批(1=是)
   */
  requireApproval?: number;
  /**
   * 是否启用评测门禁(1=是)
   */
  evalEnabled?: number;
  /**
   * 评测通过阈值(0-100)
   */
  evalPassThreshold?: number;
  /**
   * A2A卡片对外启用(0-禁用 1-启用)
   */
  cardEnabled?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * Agent 版本快照
 */
export interface AgentVersion {
  id?: number;
  agentCode: string;
  versionNo: string;
  configJson?: string;
  configHash?: string;
  changelog?: string;
  status?: VersionStatus;
  evalDatasetLocations?: string;
  evalReportId?: string;
  evalPassed?: number;
  /**
   * 发布引用的离线评测运行ID
   */
  evalRunId?: number;
  submitUser?: string;
  submitTime?: string;
  publishTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 灰度规则
 */
export interface AgentGrayRule {
  id?: number;
  agentCode: string;
  ruleType: GrayRuleType;
  ruleValue?: string;
  grayPercent?: number;
  targetVersionId?: number;
  status?: string;
  startTime?: string;
  endTime?: string;
  createTime?: string;
}

/**
 * 发布记录
 */
export interface AgentRelease {
  id?: number;
  releaseNo?: string;
  agentCode: string;
  releaseType?: string;
  fromVersionId?: number;
  toVersionId?: number;
  grayRuleId?: number;
  status?: string;
  gateResult?: string;
  reason?: string;
  operator?: string;
  createTime?: string;
}

/**
 * 灰度命中预览结果
 */
export interface GrayPreviewResult {
  hit: boolean;
  detail?: string;
  [key: string]: unknown;
}

/**
 * Agent 环境配置档
 * 字段与后端实体对齐：AgentConfigProfile (agent-data registry)
 */
export interface AgentConfigProfile {
  id?: number;
  agentCode: string;
  /**
   * 环境档编码(dev/test/prod/default)
   */
  profileCode: string;
  displayName?: string;
  /**
   * 差异覆盖JSON(仅存差异项:model/temperature/maxIterations/tools子集/知识库开关)
   */
  overrideJson?: string;
  /**
   * 状态(ACTIVE/DISABLED)
   */
  status?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * Agent 配置漂移记录
 * 字段与后端实体对齐：AgentConfigDrift (agent-data registry)
 */
export interface AgentConfigDrift {
  id?: number;
  agentCode: string;
  profileCode?: string;
  /**
   * 期望哈希(PUBLISHED快照按env合成后)
   */
  expectedHash?: string;
  /**
   * 实际哈希(ai_agent.agent_config同白名单投影)
   */
  actualHash?: string;
  /**
   * 字段级差异JSON
   */
  diffJson?: string;
  /**
   * 状态(DETECTED/REPAIRED/IGNORED)
   */
  status?: string;
  repairedBy?: string;
  repairedTime?: string;
  createTime?: string;
}

/**
 * 配置包导入结果
 */
export interface ConfigPackageImportResult {
  imported?: number;
  skipped?: number;
  [key: string]: unknown;
}
