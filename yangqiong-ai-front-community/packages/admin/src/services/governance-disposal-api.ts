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
import { request } from './api-client';

/**
 * 治理信号处置记录
 */
export interface DisposalRecord {
  id: string;

  signalType: string;

  agentCode: string | null;

  action: string;

  actionParams: string | null;

  resultStatus: string;

  resultDetail: string | null;

  operator: string | null;

  createTime: string | null;
}

/**
 * 一键处置执行结果
 */
export interface DisposalExecuteResult {
  status: 'SUCCESS' | 'PARTIAL' | 'FAILED';

  detail: string;

  extra: Record<string, unknown> | null;
}

/**
 * 治理信号处置接口(一键处置/已读下沉/处置记录)
 */
export const governanceDisposalApi = {
  /**
   * 执行一键处置动作
   * @param signalType 信号键
   * @param agentCode Agent编码
   * @return
   */
  execute: (signalType: string, agentCode: string | null | undefined): Promise<DisposalExecuteResult> =>
    request.post<DisposalExecuteResult>('/api/agent/governance/disposal/execute', { signalType, agentCode }),

  /**
   * 信号已读确认(已读状态下沉后端)
   * @param signalType 信号键
   * @param agentCode Agent编码
   * @param itemId 信号事件条目ID
   * @return
   */
  acknowledge: (signalType: string, agentCode: string | null | undefined, itemId: string): Promise<void> =>
    request.post<void>(
      `/api/agent/governance/disposal/acknowledge?signalType=${encodeURIComponent(signalType)}`
      + `&agentCode=${encodeURIComponent(agentCode ?? '')}&itemId=${encodeURIComponent(itemId)}`,
    ),

  /**
   * 分页查询处置记录
   * @param params 查询参数(信号键/Agent/动作/分页)
   * @return
   */
  records: (params: {
    signalType?: string;

    agentCode?: string;

    action?: string;

    pageNum?: number;

    pageSize?: number;
  }): Promise<DisposalRecord[]> =>
    request.get<DisposalRecord[]>('/api/agent/governance/disposal/records', { params }),
};
