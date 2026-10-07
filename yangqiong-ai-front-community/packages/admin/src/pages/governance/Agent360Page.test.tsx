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
import { Agent360Page } from './Agent360Page';
import type { GovernanceDashboardSummary } from '@yangqiong/shared';

const { summaryMock } = vi.hoisted(() => ({
  summaryMock: { impl: vi.fn() },
}));

vi.mock('@/services/governance-dashboard-api', () => ({
  governanceDashboardApi: {
    summary: (...args: unknown[]) => summaryMock.impl(...args),
  },
}));

const traceRunsMock = vi.fn().mockResolvedValue([]);
const releasesMock = vi.fn().mockResolvedValue([]);
const typeListMock = vi.fn().mockResolvedValue({ list: [], total: 0 });
const memoryListMock = vi.fn().mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });
const triggerListMock = vi.fn().mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });

vi.mock('@/services', () => ({
  api: {
    trace: {
      runs: (...args: unknown[]) => traceRunsMock(...args),
    },
    registry: {
      releases: (...args: unknown[]) => releasesMock(...args),
    },
    agent: {
      type: {
        list: (...args: unknown[]) => typeListMock(...args),
      },
    },
  },
}));

vi.mock('@/services/mcp-ecosystem-api', () => ({
  mcpEcosystemApi: {
    agentMemory: {
      list: (...args: unknown[]) => memoryListMock(...args),
    },
    trigger: {
      list: (...args: unknown[]) => triggerListMock(...args),
    },
  },
}));

const summaryFixture: GovernanceDashboardSummary = {
  signals: { driftDetected: 2 },
  signalTrend7d: {},
  agents: [
    {
      agentCode: 'agent_support',
      agentName: '客服助手',
      status: 1,
      runs7d: 42,
      failures7d: 3,
      cost30d: 128.5,
      budgetAmount: 150,
      latestReleaseNo: 'REL-20260901-0001',
      latestPublishTime: '2026-09-01 10:00:00',
      signalFlags: { driftDetected: 2, costOverrun: 1 },
    },
  ],
  recent: [],
  trend: { runs7d: [], cost7d: [] },
  generatedAt: '2026-09-14 10:00:00',
};

/**
 * 位置探针(验证完整页跳转目标)
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
 * 渲染360°页（QueryClient + MemoryRouter 包裹，完整页跳转打桩）
 * @param initialEntry 初始路由(可携带tab直达参数)
 * @return
 */
const renderPage = (initialEntry = '/governance-agents/agent_support'): HTMLElement => {
  const container = document.createElement('div');
  document.body.appendChild(container);
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const root = createRoot(container);
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/governance-agents/:agentCode" element={<Agent360Page />} />
            <Route path="/trace-runs" element={<LocationMarker />} />
            <Route path="/agent-memory" element={<LocationMarker />} />
            <Route path="/triggers" element={<LocationMarker />} />
            <Route path="/agents" element={<LocationMarker />} />
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
  traceRunsMock.mockClear().mockResolvedValue([]);
  releasesMock.mockClear().mockResolvedValue([]);
  typeListMock.mockClear().mockResolvedValue({ list: [], total: 0 });
  memoryListMock.mockClear().mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });
  triggerListMock.mockClear().mockResolvedValue({ records: [], total: 0, page: 1, size: 10 });
  vi.spyOn(console, 'warn').mockImplementation(() => undefined);
  vi.spyOn(console, 'error').mockImplementation(() => undefined);
});

afterEach(() => {
  while (cleanups.length) {
    cleanups.pop()?.();
  }
  vi.restoreAllMocks();
});

describe('Agent360Page（社区裁剪版）', () => {
  it('渲染6个Tab且企业专属Tab不渲染不留位', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const tabs = Array.from(container.querySelectorAll('.ant-tabs-tab'));
    const labels = tabs.map((tab) => tab.textContent);
    expect(labels).toEqual(['概要', '运行', '成本', '记忆', '触发规则', '配置与版本']);
    expect(labels).not.toContain('质量评测');
    expect(labels).not.toContain('身份与凭证');
    expect(labels).not.toContain('审计');
    expect(labels).not.toContain('权限画像');
  });

  it('头部摘要卡渲染身份与健康分(社区健康分算法)', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage();
    await flush();
    const card = container.querySelector('[data-testid="agent360-summary"]');
    expect(card).not.toBeNull();
    expect(card?.textContent).toContain('客服助手');
    expect(card?.textContent).toContain('agent_support');
    expect(card?.textContent).toContain('健康分');
    expect(card?.textContent).toContain('REL-20260901-0');
    // 社区端无轮换按钮与身份凭证区
    expect(card?.textContent).not.toContain('立即轮换');
    expect(card?.textContent).not.toContain('身份UID');
    expect(card?.textContent).not.toContain('凭证指纹');
    // 信号徽标组渲染
    expect(card?.textContent).toContain('配置漂移');
    expect(card?.textContent).toContain('成本超限');
    expect(card?.querySelector('svg.gov-lock-filled')).not.toBeNull();
    const flag = card?.querySelector('[data-testid="agent360-signal-flag"]');
    expect(flag).not.toBeNull();
    expect(flag?.className).toContain('cursor-not-allowed');
  });

  it('概要Tab渲染关键指标并按agentCode拉取最近运行', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    traceRunsMock.mockResolvedValue([
      {
        traceId: 'trace-001',
        status: 'OK',
        durationMs: 1200,
        totalTokens: 358,
        startTime: '2026-09-14 09:00:00',
      },
    ]);
    const container = renderPage();
    await flush();
    expect(container.textContent).toContain('近7天运行');
    expect(container.textContent).toContain('42');
    expect(container.textContent).toContain('$128.50');
    expect(container.textContent).toContain('$150.00');
    expect(traceRunsMock).toHaveBeenCalledWith(expect.objectContaining({ agentCode: 'agent_support' }));
    expect(container.textContent).toContain('trace-001');
  });

  it('成本Tab渲染预算水位与超限状态', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage('/governance-agents/agent_support?tab=cost');
    await flush();
    const cost = container.querySelector('[data-testid="agent360-cost"]');
    expect(cost).not.toBeNull();
    expect(cost?.textContent).toContain('$128.50');
    expect(cost?.textContent).toContain('$150.00');
    // 超预算(128.5 < 150，使用率约86%)但signalFlags.costOverrun>0显示超限
    expect(cost?.textContent).toContain('成本超限');
    expect(container.querySelector('[data-testid="agent360-budget-progress"]')).not.toBeNull();
  });

  it('成本Tab未配置预算时提示不参与超限计算', async () => {
    summaryMock.impl.mockResolvedValue({
      ...summaryFixture,
      agents: [{ ...summaryFixture.agents[0], budgetAmount: null, signalFlags: { driftDetected: 2 } }],
    });
    const container = renderPage('/governance-agents/agent_support?tab=cost');
    await flush();
    expect(container.textContent).toContain('未配置预算，不参与成本超限计算');
  });

  it('记忆Tab按agentCode拉取记忆条目', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    memoryListMock.mockResolvedValue({
      records: [
        {
          id: 1,
          memoryType: 'EPISODIC',
          content: '用户偏好简洁回复',
          status: 'ACTIVE',
          createTime: '2026-09-14 08:00:00',
        },
      ],
      total: 1,
      page: 1,
      size: 10,
    });
    const container = renderPage('/governance-agents/agent_support?tab=memory');
    await flush();
    expect(memoryListMock).toHaveBeenCalledWith(expect.objectContaining({ agentCode: 'agent_support' }));
    expect(container.textContent).toContain('用户偏好简洁回复');
  });

  it('触发器Tab按agentCode拉取触发规则', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    triggerListMock.mockResolvedValue({
      records: [
        {
          id: 1,
          triggerCode: 'TRG-001',
          name: '每日晨报',
          triggerType: 'CRON',
          enabled: 1,
          lastFireTime: '2026-09-14 08:00:00',
        },
      ],
      total: 1,
      page: 1,
      size: 10,
    });
    const container = renderPage('/governance-agents/agent_support?tab=trigger');
    await flush();
    expect(triggerListMock).toHaveBeenCalledWith(expect.objectContaining({ agentCode: 'agent_support' }));
    expect(container.textContent).toContain('每日晨报');
  });

  it('配置Tab渲染能力挂载摘要与发布记录，编辑跳转Agent管理', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    typeListMock.mockResolvedValue({
      list: [
        {
          typeCode: 'agent_support',
          typeName: '客服助手',
          agentConfig: '{"model":"gpt-4o","systemPrompt":"你是客服"}',
        },
      ],
      total: 1,
    });
    releasesMock.mockResolvedValue([
      {
        id: 9,
        releaseNo: 'REL-20260901-0001',
        status: 'SUCCESS',
        operator: 'admin',
        createTime: '2026-09-01 10:00:00',
      },
    ]);
    const container = renderPage('/governance-agents/agent_support?tab=config');
    await flush();
    expect(typeListMock).toHaveBeenCalled();
    expect(container.textContent).toContain('model');
    expect(container.textContent).toContain('REL-20260901-0001');
    // 编辑跳转链接指向社区Agent管理页
    const editLink = Array.from(container.querySelectorAll('a')).find((a) => a.textContent?.includes('在「Agent 管理」中编辑'));
    expect(editLink?.getAttribute('href')).toBe('/agents?agentCode=agent_support');
  });

  it('未知agentCode渲染未纳管提示', async () => {
    summaryMock.impl.mockResolvedValue(summaryFixture);
    const container = renderPage('/governance-agents/agent_unknown');
    await flush();
    expect(container.textContent).toContain('未在治理清单中找到 Agent');
  });
});
