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
import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import type { CategoryTreeNode, ModelInfo, PageQuery } from '../types';

/**
 * 多选选项（与各端多选控件解耦的最小结构）
 */
export interface AgentSelectOption {
  label: string;
  value: string;
}

/**
 * 能力挂载资源选项
 */
export interface AgentResourceOptions {
  models: AgentSelectOption[];

  modelItems: ModelInfo[];

  customTools: (AgentSelectOption & { category?: string })[];

  mcpServers: (AgentSelectOption & { category?: string })[];

  skills: (AgentSelectOption & { category?: string })[];

  knowledgeBases: (AgentSelectOption & { name?: string })[];

  /**
   * 已注册处理器选项（内存注册来源，后端 /api/agent/processors）
   */
  processors: AgentSelectOption[];

  toolCategoryNodes?: CategoryTreeNode[];

  mcpCategoryNodes?: CategoryTreeNode[];

  skillCategoryNodes?: CategoryTreeNode[];
}

/**
 * 资源查询 API 最小结构（各端 api 实例的子集，展示层无关；category 为可选能力，缺省时不查分类树）
 */
export interface AgentResourceApi {
  model: {
    list: (params?: PageQuery) => Promise<{ list?: Array<{
      modelCode: string;
      modelName: string;
      modelStatus?: number;
    }> }>;
  };
  tool: {
    config: {
      list: (params?: PageQuery) => Promise<{ list?: Array<{
        toolCode: string;
        toolName: string;
        toolCategory?: string;
        category?: string;
      }> }>;
      category?: {
        tree: () => Promise<{ nodes?: CategoryTreeNode[] }>;
      };
    };
    mcp: {
      list: (params?: PageQuery) => Promise<{ list?: Array<{
        serverCode: string;
        serverName: string;
        serverStatus?: number;
        category?: string;
      }> }>;
      category?: {
        tree: () => Promise<{ nodes?: CategoryTreeNode[] }>;
      };
    };
  };
  skill: {
    definition: {
      list: (params?: PageQuery) => Promise<{ list?: Array<{
        skillId: string;
        skillName: string;
        trustLevel?: string;
        category?: string;
      }> }>;
    };
    category?: {
      tree: () => Promise<{ nodes?: CategoryTreeNode[] }>;
    };
  };
  knowledge: {
    kb: {
      list: () => Promise<Array<{ kbId: string; kbName: string }>>;
    };
  };
  agent?: {
    processors: () => Promise<{ list?: Array<{ code: string; name: string }> }>;
  };
}

/**
 * 拉取能力挂载资源选项（模型/CUSTOM工具/启用MCP/非内置技能/知识库，三方共用逻辑）
 * @param api
 * @param enabled
 * @return
 */
export const useAgentResourceOptions = (api: AgentResourceApi, enabled: boolean): AgentResourceOptions => {
  const modelsQuery = useQuery({
    queryKey: ['agent-res-models'],
    queryFn: () => api.model.list({ page: 1, size: 500 }),
    enabled,
  });
  const toolsQuery = useQuery({
    queryKey: ['agent-res-tools'],
    queryFn: () => api.tool.config.list({ page: 1, size: 500 }),
    enabled,
  });
  const mcpQuery = useQuery({
    queryKey: ['agent-res-mcp'],
    queryFn: () => api.tool.mcp.list({ page: 1, size: 500 }),
    enabled,
  });
  const skillsQuery = useQuery({
    queryKey: ['agent-res-skills'],
    queryFn: () => api.skill.definition.list({ page: 1, size: 500 }),
    enabled,
  });
  const kbQuery = useQuery({
    queryKey: ['agent-res-kb'],
    queryFn: () => api.knowledge.kb.list(),
    enabled,
  });
  const processorsQuery = useQuery({
    queryKey: ['agent-res-processors'],
    queryFn: () => api.agent!.processors(),
    enabled: enabled && Boolean(api.agent?.processors),
  });
  const toolCatsQuery = useQuery({
    queryKey: ['agent-res-tool-cats'],
    queryFn: () => api.tool.config.category!.tree(),
    enabled: enabled && Boolean(api.tool.config.category?.tree),
  });
  const mcpCatsQuery = useQuery({
    queryKey: ['agent-res-mcp-cats'],
    queryFn: () => api.tool.mcp.category!.tree(),
    enabled: enabled && Boolean(api.tool.mcp.category?.tree),
  });
  const skillCatsQuery = useQuery({
    queryKey: ['agent-res-skills-cats'],
    queryFn: () => api.skill.category!.tree(),
    enabled: enabled && Boolean(api.skill.category?.tree),
  });

  return useMemo(
    () => ({
      models: (modelsQuery.data?.list ?? [])
        .filter((m) => m.modelStatus === 1 || m.modelStatus == null)
        .map((m) => ({ label: `${m.modelName} (${m.modelCode})`, value: m.modelCode })),
      modelItems: (modelsQuery.data?.list ?? []) as ModelInfo[],
      // 仅列 CUSTOM 分类工具，BUILTIN 内置工具自动加载无需勾选
      customTools: (toolsQuery.data?.list ?? [])
        .filter((t) => (t.toolCategory ?? 'CUSTOM') === 'CUSTOM')
        .map((t) => ({ label: `${t.toolName} (${t.toolCode})`, value: t.toolCode, category: t.category })),
      // 仅列启用中的 MCP server
      mcpServers: (mcpQuery.data?.list ?? [])
        .filter((m) => m.serverStatus === 1)
        .map((m) => ({ label: `${m.serverName} (${m.serverCode})`, value: m.serverCode, category: m.category })),
      // 仅列非 BUILTIN 信任等级技能，内置技能自动加载无需勾选
      skills: (skillsQuery.data?.list ?? [])
        .filter((s) => s.trustLevel !== 'BUILTIN')
        .map((s) => ({
          label: `${s.skillName} (${s.skillId})${s.trustLevel ? ` · ${s.trustLevel}` : ''}`,
          value: s.skillId,
          category: s.category,
        })),
      knowledgeBases: (kbQuery.data ?? []).map((k) => ({
        label: `${k.kbName} (${k.kbId})`,
        value: k.kbId,
        name: k.kbName,
      })),
      processors: (processorsQuery.data?.list ?? []).map((p) => ({
        label: `${p.name} (${p.code})`,
        value: p.code,
      })),
      toolCategoryNodes: toolCatsQuery.data?.nodes,
      mcpCategoryNodes: mcpCatsQuery.data?.nodes,
      skillCategoryNodes: skillCatsQuery.data?.nodes,
    }),
    [
      modelsQuery.data,
      toolsQuery.data,
      mcpQuery.data,
      skillsQuery.data,
      kbQuery.data,
      processorsQuery.data,
      toolCatsQuery.data,
      mcpCatsQuery.data,
      skillCatsQuery.data,
    ],
  );
};
