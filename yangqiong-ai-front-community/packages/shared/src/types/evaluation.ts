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
 * 评测相关类型
 * 字段与后端实体对齐：EvalDatasetEntity / EvalDatasetCaseEntity / EvalRunEntity / EvalRunCaseEntity (agent-data eval)
 * 接口契约：/api/agent/evaluation/* (AgentEvalDatasetController / AgentEvalRunController)
 */

/**
 * 评测运行状态
 */
export type EvalRunStatus = 'RUNNING' | 'PASSED' | 'FAILED' | 'ERROR' | 'CANCELLED';

/**
 * 评测数据集
 */
export interface EvalDataset {
  id?: number;
  datasetCode: string;
  name: string;
  description?: string;
  status?: 'DRAFT' | 'ENABLED' | 'DISABLED';
  caseCount?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 数据集用例
 */
export interface EvalDatasetCase {
  id?: number;
  datasetId?: number;
  caseNo: string;
  title?: string;
  queryText: string;
  expectedOutput?: string;
  bodyJson?: string;
  scoringCriteria?: string;
}

/**
 * 评测运行(分值为0-100域)
 */
export interface EvalRun {
  id?: number;
  datasetId?: number;
  datasetCode?: string;
  agentCode?: string;
  judgeModelCode?: string;
  status: EvalRunStatus;
  totalCases?: number;
  passedCases?: number;
  failedCases?: number;
  avgScore?: number;
  threshold?: number;
  reportJson?: string;
  errorMessage?: string;
  startedAt?: string;
  finishedAt?: string;
  createTime?: string;
}

/**
 * 运行用例明细(分值为0-100域)
 */
export interface EvalRunCase {
  id?: number;
  runId?: number;
  caseId?: number;
  caseNo?: string;
  actualOutput?: string;
  expectedOutput?: string;
  score?: number;
  passed?: number;
  strategyName?: string;
  errorMessage?: string;
  durationMs?: number;
}

/**
 * 触发评测请求
 */
export interface EvalRunTriggerRequest {
  datasetId: number;
  agentCode?: string;
  threshold?: number;
}

/**
 * 数据集保存请求(cases非空时全量替换用例)
 */
export interface EvalDatasetSaveRequest {
  datasetCode?: string;
  name?: string;
  description?: string;
  status?: string;
  cases?: EvalDatasetCase[];
}

/**
 * 用例级对比行(右侧减左侧为正=改善)
 */
export interface EvalCompareCaseRow {
  caseNo: string;
  presentLeft?: boolean;
  presentRight?: boolean;
  expectedOutput?: string | null;
  scoreLeft?: number | null;
  scoreRight?: number | null;
  scoreDelta?: number | null;
  passedLeft?: number | null;
  passedRight?: number | null;
  passChanged?: boolean | null;
}

/**
 * 运行对比结果
 */
export interface EvalCompareResult {
  left: EvalRun;
  right: EvalRun;
  summaryDiff: {
    avgScoreLeft?: number;
    avgScoreRight?: number;
    avgScoreDelta?: number;
    passedCasesLeft?: number;
    passedCasesRight?: number;
    passedCasesDelta?: number;
    failedCasesLeft?: number;
    failedCasesRight?: number;
    failedCasesDelta?: number;
    totalCasesLeft?: number;
    totalCasesRight?: number;
    statusLeft?: string;
    statusRight?: string;
  };
  caseMatrix: EvalCompareCaseRow[];
}

/**
 * 手动评测单用例结果
 * 对齐后端 CaseResult (yangqiong-ai-evaluation report)
 */
export interface EvalCaseResult {
  caseId?: string;
  query?: string;
  actualOutput?: string;
  expectedOutput?: string;
  score?: number;
  passed?: boolean;
  strategyName?: string;
  errorMessage?: string;
  durationMs?: number;
}

/**
 * 手动评测报告(同步返回)
 * 对齐后端 EvaluationReport (yangqiong-ai-evaluation report)
 */
export interface EvaluationReport {
  datasetId?: string;
  datasetName?: string;
  totalCases?: number;
  passedCases?: number;
  failedCases?: number;
  averageScore?: number;
  overallPassed?: boolean;
  results?: EvalCaseResult[];
  evaluatedAt?: string;
  durationMs?: number;
}
