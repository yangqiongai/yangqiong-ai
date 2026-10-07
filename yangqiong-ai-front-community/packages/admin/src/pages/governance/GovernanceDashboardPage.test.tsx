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
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { GovernanceDashboardPage } from './GovernanceDashboardPage';
import type { GovernanceDashboardSummary } from '@yangqiong/shared';

const { summaryMock, onboardingMock } = vi.hoisted(() => ({
  summaryMock: { impl: vi.fn() },
  onboardingMock: { impl: vi.fn() },
}));

vi.mock('@/services/governance-dashboard-api', () => ({
  governanceDashboardApi: {
    summary: (...args: unknown[]) => summaryMock.impl(...args),
  },
}));

vi.mock('@/services/governance-onboarding-api', () => ({
  governanceOnboardingApi: {
    status: (...args: unknown[]) => onboardingMock.impl(...args),
  },
}));

vi.mock('@/services', () => ({
  api: {
    trace: {
      runs: vi.fn().mockResolvedValue([]),
    },
  },
}));

const summaryFixture: GovernanceDashboardSummary = {
  signals: { driftDetected: 2, slaDegraded: 1, costOverrun: 1 },
  signalTrend7d: { driftDetected: [0, 1, 1, 0, 2, 1, 0] },
  agents: [
    {
      agentCode: 'agent_support',
      agentName: '客服助手',
      status: 1,
      runs7d: 42,
      failures7d: 3,
      cost30d: 128.5,
      budgetAmount: 150,
      signalFlags: { driftDetected: 2 },
    },
  ],
  recent: [
    {
      type: 'driftDetected',
      agentCode: 'agent_support',
      agentName: '客服助手',
      summary: '模型配置与基线不一致',
      occurredAt: '2026-09-14 09:12:00',
    },
  ],
  trend: { runs7d: [1, 2, 3, 4, 5, 6, 7], cost7d: [1, 1, 2, 2, 3, 3, 4] },
  generatedAt: '2026-09-14 10:00:00',
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
 * 渲染驾驶舱页（QueryClient + MemoryRouter 包裹，Inbox/360° 打桩验证跳转）
 * @return
 */
const renderPage = (): HTMLElement => {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={['/governance-dashboard']}>
          <Routes>
            <Route path="/governance-dashboard" element={<GovernanceDashboardPage />} />
            <Route path="/governance-inbox" element={<div data-testid="inbox-marker" />} />
            <Route path="/governance-agents/:agentCode" element={<div data-testid="agent360-marker" />} />
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
  onboardingMock.impl.mockReset();
  // 默认已有Agent, 走驾驶舱主画布分支
  onboardingMock.impl.mockResolvedValue({
    onboarded: true,
    hasAgent: true,
    hasModel: true,
    hasRunData: true,
    hasSignal: true,
    demoSeeded: false,
  });
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
});

afterEach(() => {
  while (cleanups.length) {
    cleanups.pop()?.();
  }
  vi.restoreAllMocks();
});

describe('GovernanceDashboardPage（社区裁剪版）', () => {
  it('加载中渲染加载态', () => {
    summaryMock.impl.mockReturnValue(new Promise(() => undefined));
    const container = renderPage();
    expect(container.textContent).toContain('驾驶舱数据加载中');
  });

  it('加载失败渲染错误态', async () => {
    summaryMock.impl.mockRejectedValue(new Error('boom'));
    const container = renderPage();
    await flush();
    expect(container.textContent).toContain('驾驶舱数据加载失败');
  });

  it('单请求驱动四区渲染（健康环/蜂巢/星系/雷达），社区版无趋势地平线', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    expect(container.querySelector('[data-testid="governance-dashboard"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="health-ring"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="signal-hex-band"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="agent-galaxy"]')).not.toBeNull();
    expect(container.querySelector('[data-testid="signal-radar-stream"]')).not.toBeNull();
    // 社区版不交付趋势地平线
    expect(container.querySelector('[data-testid="trend-horizon"]')).toBeNull();
    // 顶部渲染企业版能力引导Alert
    expect(container.textContent).toContain('治理信号处置为企业版能力，社区版仅展示信号概览');
    // 雷达流直出recent条目
    expect(container.textContent).toContain('模型配置与基线不一致');
  });

  it('社区端仅渲染3张运行信号蜂巢卡，处置入口置灰并显示锁定标识', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const driftCell = container.querySelector('button[aria-label="配置漂移 2 项待处置"]') as HTMLButtonElement;
    const slaCell = container.querySelector('button[aria-label="SLA退化 1 项待处置"]') as HTMLButtonElement;
    const costCell = container.querySelector('button[aria-label="成本超限 1 项待处置"]') as HTMLButtonElement;
    expect(driftCell).not.toBeNull();
    expect(slaCell).not.toBeNull();
    expect(costCell).not.toBeNull();
    // 处置能力收回企业版：蜂巢卡置灰禁用且以实心锁图标标识(signal-hex为内联实心SVG+title)
    expect(driftCell.disabled).toBe(true);
    expect(slaCell.disabled).toBe(true);
    expect(costCell.disabled).toBe(true);
    expect(container.querySelector('svg.gov-lock-filled')).not.toBeNull();
    // 企业专属信号连占位都无
    expect(container.textContent).not.toContain('触发器失败');
    expect(container.textContent).not.toContain('身份轮换到期');
    expect(container.textContent).not.toContain('审计异常');
    expect(container.textContent).not.toContain('进化提案');
    expect(container.textContent).not.toContain('记忆隔离');
    expect(container.textContent).not.toContain('记忆过期');
  });

  it('蜂巢卡处置入口置灰不再跳转Inbox', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const driftCell = container.querySelector('button[aria-label="配置漂移 2 项待处置"]') as HTMLButtonElement;
    expect(driftCell).not.toBeNull();
    expect(driftCell.disabled).toBe(true);
    await act(async () => {
      driftCell.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });
    await flush();
    expect(container.querySelector('[data-testid="inbox-marker"]')).toBeNull();
  });

  it('雷达流社区端过滤企业专属信号条目', async () => {
    summaryMock.impl.mockResolvedValue({
      ...summaryFixture,
      recent: [
        ...summaryFixture.recent,
        {
          type: 'auditAnomalies',
          agentCode: 'agent_support',
          agentName: '客服助手',
          summary: '哈希链断裂',
          occurredAt: '2026-09-14 09:30:00',
        },
      ],
    });
    const container = renderPage();
    await flush();
    expect(container.textContent).toContain('模型配置与基线不一致');
    expect(container.textContent).not.toContain('哈希链断裂');
  });

  it('全部信号清零时空态正反馈呈现', async () => {
    summaryMock.impl.mockResolvedValue({
      ...summaryFixture,
      signals: {},
      recent: [],
    });
    const container = renderPage();
    await flush();
    expect(container.textContent).toContain('全域平稳');
    expect(container.textContent).toContain('暂无待处置信号');
  });

  it('无Agent时渲染社区裁剪版接入空状态(只读状态清单, 无示例导入)', async () => {
    summaryMock.impl.mockReturnValue(new Promise(() => undefined));
    onboardingMock.impl.mockResolvedValue({
      onboarded: false,
      hasAgent: false,
      hasModel: false,
      hasRunData: false,
      hasSignal: false,
      demoSeeded: false,
    });
    const container = renderPage();
    await flush();
    expect(container.querySelector('[data-testid="onboarding-empty-state"]')).not.toBeNull();
    expect(container.textContent).toContain('等待 Agent 接入');
    // 社区裁剪模式：只读状态清单
    expect(container.querySelector('[data-testid="onboarding-status-chips"]')).not.toBeNull();
    expect(container.textContent).toContain('Agent 定义');
    expect(container.textContent).toContain('治理信号');
    // 无向导入口与示例导入按钮
    expect(container.querySelector('[data-testid="onboarding-wizard-open"]')).toBeNull();
    expect(container.querySelector('[data-testid="onboarding-seed"]')).toBeNull();
    expect(container.querySelector('[data-testid="onboarding-clean"]')).toBeNull();
  });
});
