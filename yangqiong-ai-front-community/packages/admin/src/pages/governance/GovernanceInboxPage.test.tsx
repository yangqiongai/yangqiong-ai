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
import { act } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GovernanceInboxPage } from './GovernanceInboxPage';
import type { GovernanceDashboardSummary } from '@yangqiong/shared';

const { summaryMock, executeMock, acknowledgeMock, recordsMock } = vi.hoisted(() => ({
  summaryMock: { impl: vi.fn() },
  executeMock: { impl: vi.fn() },
  acknowledgeMock: { impl: vi.fn() },
  recordsMock: { impl: vi.fn() },
}));

vi.mock('@/services/governance-dashboard-api', () => ({
  governanceDashboardApi: {
    summary: (...args: unknown[]) => summaryMock.impl(...args),
  },
}));

vi.mock('@/services/governance-disposal-api', () => ({
  governanceDisposalApi: {
    execute: (...args: unknown[]) => executeMock.impl(...args),
    acknowledge: (...args: unknown[]) => acknowledgeMock.impl(...args),
    records: (...args: unknown[]) => recordsMock.impl(...args),
  },
}));

const summaryFixture: GovernanceDashboardSummary = {
  signals: { driftDetected: 1, slaDegraded: 2, costOverrun: 1 },
  signalTrend7d: {},
  agents: [],
  recent: [
    {
      type: 'slaDegraded',
      agentCode: 'agent_support',
      agentName: '客服助手',
      summary: '失败率超过阈值',
      occurredAt: '2026-09-14 09:00:00',
    },
    {
      type: 'driftDetected',
      agentCode: 'agent_sales',
      agentName: '销售助手',
      summary: '提示词与基线不一致',
      occurredAt: '2026-09-14 08:00:00',
    },
    {
      type: 'costOverrun',
      agentCode: 'agent_sales',
      agentName: '销售助手',
      summary: '月度成本超过预算80%',
      occurredAt: '2026-09-14 07:00:00',
    },
  ],
  trend: { runs7d: [], cost7d: [] },
  generatedAt: '2026-09-14 10:00:00',
};

/**
 * SLA信号已读ACK记录fixture
 */
const slaAckRecord = {
  id: '9001',
  signalType: 'slaDegraded',
  agentCode: 'agent_support',
  action: 'ACK',
  actionParams: JSON.stringify({ itemId: 'slaDegraded|agent_support|2026-09-14 09:00:00|失败率超过阈值' }),
  resultStatus: 'SUCCESS',
  resultDetail: '已读',
  operator: 'admin',
  createTime: '2026-09-14 10:00:00',
};

/**
 * 漂移自动修复处置记录fixture
 */
const driftRepairRecord = {
  id: '9002',
  signalType: 'driftDetected',
  agentCode: 'agent_sales',
  action: 'AUTO_REPAIR',
  actionParams: null,
  resultStatus: 'SUCCESS',
  resultDetail: '已回滚1条漂移至最近发布版本',
  operator: 'admin',
  createTime: '2026-09-14 11:00:00',
};

/**
 * 位置探针(验证路由跳转目标)
 */
const LocationMarker: React.FC = () => {
  const location = useLocation();
  return <div data-testid="location-marker" data-path={location.pathname + location.search} />;
};

const cleanups: Array<() => void> = [];

/**
 * 等待react-query解析完成(其通知调度走setTimeout宏任务)
 */
const flush = async () => {
  for (let round = 0; round < 10; round++) {
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
  }
};

/**
 * 渲染Inbox页（QueryClient + MemoryRouter 包裹，处置目标页打桩）
 * @param initialEntry 初始路由(可携带类型过滤参数)
 * @return
 */
const renderPage = (initialEntry = '/governance-inbox'): HTMLElement => {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/governance-inbox" element={<GovernanceInboxPage />} />
            <Route path="/trace-runs" element={<LocationMarker />} />
            <Route path="/agents" element={<LocationMarker />} />
            <Route path="/governance-agents/:agentCode" element={<LocationMarker />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    );
  });
  cleanups.push(() => {
    act(() => root.unmount());
    container.remove();
  });
  return container;
};

beforeEach(() => {
  summaryMock.impl.mockReset();
  executeMock.impl.mockReset();
  acknowledgeMock.impl.mockReset();
  recordsMock.impl.mockReset();
  recordsMock.impl.mockResolvedValue([]);
  localStorage.clear();
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
});

afterEach(() => {
  while (cleanups.length) {
    cleanups.pop()?.();
  }
  vi.restoreAllMocks();
});

describe('GovernanceInboxPage（社区裁剪版）', () => {
  it('仅渲染3类运行信号过滤chip，企业专属信号chip不渲染', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    expect(container.querySelector('[data-testid="inbox-type-chip-driftDetected"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="inbox-type-chip-slaDegraded"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="inbox-type-chip-costOverrun"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="inbox-type-chip-triggerFailures"]')).toBeNull();
    expect(container.querySelector('[data-testid="inbox-type-chip-auditAnomalies"]')).toBeNull();
    expect(container.querySelector('[data-testid="inbox-type-chip-rotationDue"]')).toBeNull();
  });

  it('按严重级排序渲染信号行(P1优先)', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const rows = container.querySelectorAll('[data-testid="inbox-row"]');
    expect(rows).toHaveLength(3);
    // slaDegraded 为P1应排在最前
    expect(rows[0].textContent).toContain('SLA退化');
    expect(rows[0].textContent).toContain('失败率超过阈值');
  });

  it('企业专属信号行被防御性过滤不渲染', async () => {
    summaryMock.impl.mockResolvedValue({
      ...summaryFixture,
      recent: [
        ...summaryFixture.recent,
        {
          type: 'auditAnomalies',
          agentCode: 'agent_support',
          agentName: '客服助手',
          summary: '哈希链断裂',
          occurredAt: '2026-09-14 06:00:00',
        },
      ],
    });
    const container = renderPage();
    await flush();
    const rows = container.querySelectorAll('[data-testid="inbox-row"]');
    expect(rows).toHaveLength(3);
    expect(container.textContent).not.toContain('哈希链断裂');
  });

  it('点击类型chip同步URL过滤参数并二次点击取消', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const chip = container.querySelector('[data-testid="inbox-type-chip-driftDetected"]');
    await act(async () => {
      chip?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await flush();
    // MemoryRouter下行过滤生效(仅剩drift行)
    const rows = container.querySelectorAll('[data-testid="inbox-row"]');
    expect(rows).toHaveLength(1);
    expect(rows[0].textContent).toContain('提示词与基线不一致');
    // 二次点击取消过滤恢复全量
    const chipAgain = container.querySelector('[data-testid="inbox-type-chip-driftDetected"]');
    await act(async () => {
      chipAgain?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await flush();
    expect(container.querySelectorAll('[data-testid="inbox-row"]')).toHaveLength(3);
  });

  it('页面顶部渲染企业版能力引导Alert', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    expect(container.textContent).toContain('治理信号处置为企业版能力，社区版仅展示信号概览');
  });

  it('SLA退化处置入口置灰并显示锁定标识', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const dispose = container.querySelector('[data-testid="inbox-dispose-slaDegraded"]') as HTMLButtonElement;
    expect(dispose?.textContent).toContain('查看运行');
    expect(dispose?.disabled).toBe(true);
    const auto = container.querySelector('[data-testid="inbox-auto-dispose-slaDegraded"]') as HTMLButtonElement;
    expect(auto?.disabled).toBe(true);
    // 企业版标识改为锁图标+Tooltip提示
    expect(container.querySelector('svg.gov-lock-filled')).not.toBeNull();
  });

  it('成本超限处置入口置灰并显示锁定标识', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const dispose = container.querySelector('[data-testid="inbox-dispose-costOverrun"]') as HTMLButtonElement;
    expect(dispose?.textContent).toContain('查看账单');
    expect(dispose?.disabled).toBe(true);
    const auto = container.querySelector('[data-testid="inbox-auto-dispose-costOverrun"]') as HTMLButtonElement;
    expect(auto?.disabled).toBe(true);
    // 企业版标识改为锁图标+Tooltip提示
    expect(container.querySelector('svg.gov-lock-filled')).not.toBeNull();
  });

  it('配置漂移跳转导航已删除且一键修复置灰', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    // 跳转目标(Agent管理漂移Tab)已下线, 导航入口不再渲染
    expect(container.querySelector('[data-testid="inbox-dispose-driftDetected"]')).toBeNull();
    const auto = container.querySelector('[data-testid="inbox-auto-dispose-driftDetected"]') as HTMLButtonElement;
    expect(auto?.textContent).toContain('一键修复');
    expect(auto?.disabled).toBe(true);
  });

  it('三类信号一键处置入口均置灰且保留动作字样', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    for (const key of ['slaDegraded', 'driftDetected', 'costOverrun']) {
      const autoButton = container.querySelector(`[data-testid="inbox-auto-dispose-${key}"]`) as HTMLButtonElement;
      expect(autoButton).not.toBeNull();
      expect(autoButton.disabled).toBe(true);
    }
    expect(container.querySelector('[data-testid="inbox-auto-dispose-slaDegraded"]')?.textContent).toContain('回归评测');
  });

  it('一键处置入口置灰后不再调用处置接口', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    executeMock.impl.mockResolvedValue({ status: 'SUCCESS', detail: 'SLA已恢复达标', extra: null });
    acknowledgeMock.impl.mockResolvedValue(undefined);
    const container = renderPage();
    await flush();
    // 处置API已收回企业版，社区端入口置灰且无反馈条
    expect(executeMock.impl).not.toHaveBeenCalled();
    expect(acknowledgeMock.impl).not.toHaveBeenCalled();
    expect(container.querySelector('[data-testid="inbox-disposal-feedback"]')).toBeNull();
  });

  it('勾选后批量处置入口置灰且不触发处置', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    executeMock.impl.mockResolvedValue({ status: 'SUCCESS', detail: '处置成功', extra: null });
    acknowledgeMock.impl.mockResolvedValue(undefined);
    const container = renderPage();
    await flush();
    // 社区三类信号均支持勾选，勾选全部
    const checkboxes = Array.from(container.querySelectorAll('tbody .ant-table-selection-column input[type="checkbox"]'));
    await act(async () => {
      (checkboxes[0] as HTMLInputElement).click();
      (checkboxes[1] as HTMLInputElement).click();
    });
    await flush();
    const batchButton = container.querySelector('[data-testid="inbox-batch-dispose"]') as HTMLButtonElement;
    expect(batchButton).not.toBeNull();
    expect(batchButton.textContent).toContain('批量处置（2）');
    expect(batchButton.disabled).toBe(true);
    expect(executeMock.impl).not.toHaveBeenCalled();
  });

  it('后端ACK记录恢复已读状态（默认隐藏+显示已忽略还原）', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    recordsMock.impl.mockResolvedValue([slaAckRecord]);
    const container = renderPage();
    await flush();
    let rows = container.querySelectorAll('[data-testid="inbox-row"]');
    expect(rows).toHaveLength(2);
    expect(container.textContent).not.toContain('失败率超过阈值');
    // 打开显示已忽略恢复呈现(带已读徽标)
    const ignoredToggle = container.querySelector('input[data-testid="inbox-show-ignored"]');
    await act(async () => {
      (ignoredToggle as HTMLInputElement).click();
    });
    await flush();
    rows = container.querySelectorAll('[data-testid="inbox-row"]');
    expect(rows).toHaveLength(3);
    expect(rows[0].textContent).toContain('已读');
    expect(localStorage.getItem('governance-inbox-ignored')).toBeNull();
  });

  it('localStorage遗留已读只读迁移（隐藏且不再写回）', async () => {
    localStorage.setItem('governance-inbox-ignored', JSON.stringify(['slaDegraded|agent_support|2026-09-14 09:00:00|失败率超过阈值']));
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    expect(container.querySelectorAll('[data-testid="inbox-row"]')).toHaveLength(2);
    expect(container.textContent).not.toContain('失败率超过阈值');
  });

  it('勾选标记已读入口置灰且不再调用ACK接口', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    acknowledgeMock.impl.mockResolvedValue(undefined);
    const container = renderPage();
    await flush();
    // 勾选第一行(行内勾选框，跳过表头全选框)
    const checkbox = container.querySelectorAll('tbody .ant-table-selection-column input[type="checkbox"]')[0];
    await act(async () => {
      checkbox?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await flush();
    const markRead = container.querySelector('[data-testid="inbox-mark-read"]') as HTMLButtonElement;
    expect(markRead).not.toBeNull();
    expect(markRead.textContent).toContain('标记已读（1）');
    // 已读确认已收回企业版，入口置灰且不写localStorage
    expect(markRead.disabled).toBe(true);
    expect(acknowledgeMock.impl).not.toHaveBeenCalled();
    expect(localStorage.getItem('governance-inbox-ignored')).toBeNull();
  });

  it('处置记录弹窗展示最近处置动作与结果', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    recordsMock.impl.mockResolvedValue([slaAckRecord, driftRepairRecord]);
    const container = renderPage();
    await flush();
    const historyButton = container.querySelector('[data-testid="inbox-history"]');
    expect(historyButton).not.toBeNull();
    await act(async () => {
      historyButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await flush();
    const dialog = document.body.querySelector('[data-testid="inbox-history-dialog"]');
    expect(dialog).not.toBeNull();
    // 弹窗标题渲染在Modal头部
    expect(document.body.textContent).toContain('最近100条');
    expect(dialog?.textContent).toContain('已读确认');
    expect(dialog?.textContent).toContain('自动修复');
    expect(dialog?.textContent).toContain('已回滚1条漂移至最近发布版本');
  });

  it('空数据渲染空态文案', async () => {
    summaryMock.impl.mockResolvedValue({ ...summaryFixture, recent: [], signals: {} });
    const container = renderPage();
    await flush();
    expect(container.textContent).toContain('暂无待处置信号');
  });
});
