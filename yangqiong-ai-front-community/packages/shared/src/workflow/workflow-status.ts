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
 * 工作流执行状态语义分组（视图层由各端映射为 Tag 颜色/Badge 变体等展示形态）
 */
export type WorkflowStatusGroup =
  | 'success'
  | 'failed'
  | 'running'
  | 'paused'
  | 'pending'
  | 'cancelled'
  | 'skipped'
  | 'other';

/**
 * 执行状态中文标签映射（各后端状态命名的并集）
 */
const EXECUTION_STATUS_LABELS: Record<string, string> = {
  PENDING: '待执行',
  RUNNING: '运行中',
  PAUSED: '已暂停',
  COMPLETED: '已完成',
  SUCCEEDED: '成功',
  SUCCESS: '成功',
  FAILED: '已失败',
  FAILURE: '失败',
  CANCELLED: '已取消',
  SKIPPED: '已跳过',
};

/**
 * 执行状态中文标签（未知状态原样返回）
 * @param status
 * @return
 */
export const workflowExecutionStatusLabel = (status?: string): string => {
  if (!status) {
    return '-';
  }
  const s = status.toUpperCase();
  return EXECUTION_STATUS_LABELS[s] ?? status;
};

/**
 * 执行状态语义分组（成功/失败/进行中等，未知状态归入 other）
 * @param status
 * @return
 */
export const workflowStatusGroup = (status?: string): WorkflowStatusGroup => {
  const s = status?.toUpperCase();
  if (s === 'SUCCESS' || s === 'SUCCEEDED' || s === 'COMPLETED') {
    return 'success';
  }
  if (s === 'FAILED' || s === 'FAILURE') {
    return 'failed';
  }
  if (s === 'RUNNING') {
    return 'running';
  }
  if (s === 'PAUSED') {
    return 'paused';
  }
  if (s === 'PENDING') {
    return 'pending';
  }
  if (s === 'CANCELLED') {
    return 'cancelled';
  }
  if (s === 'SKIPPED') {
    return 'skipped';
  }
  return 'other';
};
