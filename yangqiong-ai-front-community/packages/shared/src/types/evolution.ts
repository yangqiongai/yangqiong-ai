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
 * 自我进化相关类型
 * 字段与后端实体对齐：ImprovementProposal / FeedbackRecord / AuditTrailEntry (ai-evolution)
 * ExperienceEntry / FailurePattern (ai-evolution/experience)
 * EvolutionMetrics (ai-evolution/orchestrator)
 * EvolutionController / FeedbackController / GovernanceController (ai-evolution/api)
 */

/**
 * 提案状态
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.proposal.ProposalStatus
 */
export type ProposalStatus =
  | 'PENDING_EVALUATION'
  | 'EVALUATING'
  | 'EVALUATION_PASSED'
  | 'EVALUATION_FAILED'
  | 'APPROVING'
  | 'ADOPTED'
  | 'ADOPT_FAILED'
  | 'REJECTED'
  | 'ROLLED_BACK';

/**
 * 提案类型
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.proposal.ProposalType
 */
export type ProposalType = 'PROMPT' | 'WORKFLOW' | 'TOOL_ROUTING' | 'MODEL' | 'SKILL' | 'GUARDRAIL';

/**
 * 改进提案
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.proposal.ImprovementProposal
 */
export interface ImprovementProposal {
  proposalId: string;
  agentCode?: string;
  type?: ProposalType;
  status?: ProposalStatus;
  capabilityNodeId?: string;
  title: string;
  rationale?: string;
  currentValue?: string;
  proposedValue?: string;
  sourceExperienceIds?: string[];
  sourceFeedbackIds?: string[];
  evaluationReportId?: string;
  expectedImpact?: string;
  createTime?: string;
  statusUpdateTime?: string;
}

/**
 * 反馈类型
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.feedback.FeedbackType
 */
export type FeedbackType = 'CORRECTION' | 'ACCEPTANCE' | 'RATING' | 'FAILURE_ANALYSIS' | 'IMPLICIT_SIGNAL';

/**
 * 反馈记录
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.feedback.FeedbackRecord
 */
export interface FeedbackRecord {
  feedbackId?: string;
  taskId?: string;
  userId?: string;
  agentCode?: string;
  type?: FeedbackType;
  correctedOutput?: string;
  acceptanceDecision?: string;
  rating?: number;
  failureRootCause?: string;
  comment?: string;
  originalInput?: string;
  originalOutput?: string;
  createTime?: string;
}

/**
 * 审计轨迹条目
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.governance.AuditTrailEntry
 */
export interface AuditTrailEntry {
  entryId?: string;
  proposalId?: string;
  action?: string;
  operator?: string;
  comment?: string;
  actionTime?: string;
}

/**
 * 经验类型
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.experience.ExperienceType
 */
export type ExperienceType = 'SUCCESS' | 'FAILURE';

/**
 * 经验作用域
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.experience.ExperienceScope
 */
export type ExperienceScope = 'AGENT' | 'TASK' | 'GLOBAL';

/**
 * 经验条目
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.experience.ExperienceEntry
 */
export interface ExperienceEntry {
  experienceId?: string;
  agentCode?: string;
  type?: ExperienceType;
  scope?: ExperienceScope;
  userId?: string;
  taskPattern?: string;
  strategy?: string;
  outcome?: string;
  rootCause?: string;
  relatedTaskIds?: string[];
  occurrenceCount?: number;
  effectivenessScore?: number;
  content?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 失败模式
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.experience.FailurePattern
 */
export interface FailurePattern {
  patternId?: string;
  name?: string;
  agentCode?: string;
  description?: string;
  rootCauseCategory?: string;
  experienceIds?: string[];
  occurrenceCount?: number;
  suggestedFix?: string;
  taskPattern?: string;
}

/**
 * 自进化度量
 * 对应后端 com.yangqiongai.ai.evolution.enterprise.orchestrator.EvolutionMetrics
 */
export interface EvolutionMetrics {
  [key: string]: unknown;
}
