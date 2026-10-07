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
 * 审批相关类型
 * 字段与后端实体对齐：PendingRequestInfo (ai-approval/model)
 * ApprovalStatus (ai-approval 枚举)
 */

/**
 * 审批状态
 * 对应后端 com.maizi.ai.approval.ApprovalStatus
 */
export type ApprovalStatus = 'APPROVED' | 'PENDING' | 'REJECTED' | 'TIMEOUT';

/**
 * 待审批请求信息
 * 对应后端 com.maizi.ai.approval.model.PendingRequestInfo
 */
export interface PendingRequestInfo {
  id?: number;
  requestId: string;
  approvalToken?: string;
  sessionId?: string;
  userId?: string;
  resourceType?: string;
  targetName?: string;
  reason?: string;
  targetParams?: string;
  inputSchema?: string;
  responsePayload?: string;
  status?: ApprovalStatus;
  expireTime?: string;
  resolvedTime?: string;
  resolvedBy?: string;
  rejectReason?: string;
  createTime?: string;
  updateTime?: string;
  delFlag?: number;
}
