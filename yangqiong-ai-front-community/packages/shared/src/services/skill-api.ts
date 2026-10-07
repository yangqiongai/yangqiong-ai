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
 * 技能定义与技能用量接口
 */
import type { HttpRequest } from './http';
import type {
  CategorySaveRequest,
  CategoryTreeNode,
  PageQuery,
  PageResult,
  SkillDefinition,
  SkillDraft,
  SkillEvalResult,
  SkillUsageInfo,
  SkillVersionDetail,
  SkillVersionSummary,
} from '../types';

/**
 * 对未分页的后端全量技能列表做客户端过滤与分页，包装为统一 PageResult
 * @param items
 * @param params
 * @return
 */
function toSkillPageResult(items: SkillDefinition[], params?: PageQuery): PageResult<SkillDefinition> {
  const keyword = (params?.keyword ?? '').trim().toLowerCase();
  const filtered = keyword
    ? items.filter((item) =>
        item.skillId?.toLowerCase().includes(keyword) || item.skillName?.toLowerCase().includes(keyword))
    : items;
  const page = params?.page ?? 1;
  const size = params?.size ?? (filtered.length || 10);
  const start = (page - 1) * size;
  return {
    list: filtered.slice(start, start + size),
    total: filtered.length,
    page,
    size,
  };
}

export const createSkillApi = (http: HttpRequest) => ({
  definition: {
    list: async (params?: PageQuery) => {
      const items = await http.get<SkillDefinition[]>('/api/agent/skill');
      return toSkillPageResult(items ?? [], params);
    },
    // 后端按来源分页实查(source=db数据库技能含禁用/source=builtin内置技能)；okPage的total在metaData(拦截器丢弃)，满页时总数+1支持翻页
    page: async (params?: PageQuery & { source?: string; categoryCode?: string }): Promise<PageResult<SkillDefinition>> => {
      const page = params?.page ?? 1;
      const size = params?.size ?? 10;
      const records = (await http.get<SkillDefinition[]>('/api/agent/skill/page', {
        params: {
          source: params?.source ?? 'db',
          keyword: params?.keyword || undefined,
          categoryCode: params?.categoryCode || undefined,
          pageNum: page,
          pageSize: size,
        },
      })) ?? [];
      const total = records.length === size ? page * size + 1 : (page - 1) * size + records.length;
      return { list: records, total, page, size };
    },
    get: (skillId: string) => http.get<SkillDefinition>(`/api/agent/skill/${skillId}`),
    create: (data: Omit<SkillDefinition, 'skillId'> & { skillId?: string }) =>
      http.post<SkillDefinition>('/api/agent/skill', data),
    update: (skillId: string, data: Partial<SkillDefinition> & { remark?: string }) =>
      http.put<SkillDefinition>(`/api/agent/skill/${skillId}`, data),
    delete: (skillId: string) => http.delete<void>(`/api/agent/skill/${skillId}`),
    updateTrustLevel: (skillId: string, trustLevel: string) =>
      http.put<void>(`/api/agent/skill/${skillId}/trust-level`, { trustLevel }),
    generate: (data: SkillDraft) =>
      http
        // 后端生成接口入参为 {skillName, description}，草稿内容以markdown返回
        .post<{ draftId?: string; skillName?: string; markdown?: string; spec?: string; status?: string }>(
          '/api/agent/skill/generation/generate',
          { skillName: data.skillName, description: data.skillContent || data.skillDescription || '' })
        .then((draft) => ({
          skillId: draft?.draftId,
          skillName: draft?.skillName ?? data.skillName,
          skillDescription: data.skillDescription,
          skillContent: draft?.markdown ?? '',
          skillVersion: data.skillVersion,
          boundTools: data.boundTools,
        }) as SkillDraft),
  },
  category: {
    tree: () =>
      http.get<{ nodes: CategoryTreeNode[]; ungroupedCount: number }>(
        '/api/agent/skill/category/tree'
      ),
    create: (data: CategorySaveRequest) => http.post<unknown>('/api/agent/skill/category', data),
    update: (id: number, data: CategorySaveRequest) =>
      http.put<unknown>(`/api/agent/skill/category/${id}`, data),
    delete: (id: number) => http.delete<void>(`/api/agent/skill/category/${id}`),
  },
  version: {
    // 后端 okPage 的 data 为数组（total 在 metaData，拦截器丢弃），满页时总数+1 支持翻页
    list: async (skillId: string, params?: PageQuery): Promise<PageResult<SkillVersionSummary>> => {
      const page = params?.page ?? 1;
      const size = params?.size ?? 10;
      const records = (await http.get<SkillVersionSummary[]>(
        `/api/agent/skill/${skillId}/versions`,
        { params: { pageNum: page, pageSize: size } },
      )) ?? [];
      const total = records.length === size ? page * size + 1 : (page - 1) * size + records.length;
      return { list: records, total, page, size };
    },
    get: (skillId: string, version: number) =>
      http.get<SkillVersionDetail>(`/api/agent/skill/${skillId}/versions/${version}`),
    rollback: (skillId: string, targetVersion: number, remark?: string) =>
      http.post<void>(`/api/agent/skill/${skillId}/rollback`, { targetVersion, remark }),
    evaluate: (skillId: string) =>
      http.post<SkillEvalResult>(`/api/agent/skill/${skillId}/evaluate`),
  },
  usage: {
    top: async (params?: PageQuery) => {
      const size = params?.size ?? 20;
      const items = await http.get<SkillUsageInfo[]>('/api/agent/skill/usage/top', { params: { limit: size } });
      const keyword = (params?.keyword ?? '').trim().toLowerCase();
      if (!keyword) return items ?? [];
      return (items ?? []).filter((item) =>
        item.skillId?.toLowerCase().includes(keyword));
    },
    state: (skillId: string) =>
      http.get<SkillUsageInfo>(`/api/agent/skill/usage/${skillId}`),
    pinned: (skillId: string, pinned: boolean) =>
      http.put<void>(`/api/agent/skill/usage/${skillId}/pinned`, { pinned }),
  },
});
