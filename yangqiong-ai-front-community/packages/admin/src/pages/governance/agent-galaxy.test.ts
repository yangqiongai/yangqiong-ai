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
import { describe, expect, it } from 'vitest';
import { buildGraphData } from '@yangqiong/shared';
import type { AgentRuntimeSummary } from '@yangqiong/shared';

/**
 * 构建Agent运行摘要
 * @param agentCode
 * @param agentName
 * @param signalFlags
 * @return
 */
const buildAgent = (agentCode: string, agentName: string,
                    signalFlags: Record<string, number> = {}): AgentRuntimeSummary => ({
  agentCode,
  agentName,
  status: 1,
  runs7d: 10,
  failures7d: 1,
  cost30d: 5,
  signalFlags,
});

const NO_OPTIONS = { cluster: false, focusSignal: null, orbit: false };

describe('buildGraphData同码副本去重', () => {
  it('星系模式下重复agentCode仅保留首个节点，不产生重复id', () => {
    const agents = [
      buildAgent('dup-agent', '首个副本', { driftDetected: 2 }),
      buildAgent('dup-agent', '同码副本'),
      buildAgent('normal-agent', '正常节点'),
    ];
    const graph = buildGraphData(agents, new Map(), NO_OPTIONS);

    const dupNodes = graph.nodes.filter((node) => node.id === 'agent:dup-agent');
    expect(dupNodes).toHaveLength(1);
    expect(dupNodes[0].name).toBe('首个副本');
    // 连线同样只保留首份，且全部指向存在的信号节点
    const dupLinks = graph.links.filter((link) => link.source === 'agent:dup-agent');
    expect(dupLinks).toHaveLength(1);
  });

  it('聚类模式下同码副本仅计入一个团簇成员', () => {
    const agents = [
      buildAgent('dup-agent', '首个副本', { driftDetected: 2 }),
      buildAgent('dup-agent', '同码副本'),
    ];
    const graph = buildGraphData(agents, new Map(), { ...NO_OPTIONS, cluster: true });

    const clusterNodes = graph.nodes.filter((node) => node.id === 'cluster:driftDetected');
    expect(clusterNodes).toHaveLength(1);
    expect(String(clusterNodes[0].name)).toContain('(1)');
  });

  it('轨道模式下同码副本去重后按唯一清单计算轨道位置', () => {
    const agents = [
      buildAgent('dup-agent', '首个副本'),
      buildAgent('dup-agent', '同码副本'),
      buildAgent('other-agent', '其他节点'),
    ];
    const graph = buildGraphData(agents, new Map(), { ...NO_OPTIONS, orbit: true });

    const agentNodes = graph.nodes.filter((node) => String(node.id).startsWith('agent:'));
    expect(agentNodes).toHaveLength(2);
    // 同一轨道半径上同码副本不再重复占位
    const positions = new Set(agentNodes.map((node) => `${node.x},${node.y}`));
    expect(positions.size).toBe(2);
  });
});
