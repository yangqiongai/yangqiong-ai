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
 * 技能相关类型
 * 字段与后端实体对齐：SkillDefinition / SkillUsageInfo (ai-agent-skill)
 */

/**
 * 技能定义
 * 对应后端 com.maizi.ai.agent.skill.model.SkillDefinition
 */
export interface SkillDefinition {
  skillId: string;
  skillName: string;
  skillDescription?: string;
  skillType?: string;
  skillContent?: string;
  boundTools?: string[];
  resources?: Record<string, string>;
  execution?: Record<string, unknown>;
  dependencies?: Record<string, unknown>;
  presetParameters?: Record<string, unknown>;
  skillVersion?: number;
  conditions?: Record<string, unknown>;
  trustLevel?: string;
  qualityScore?: number;
  evaluatedTime?: string;
  /**
   * 所属分类编码（技能分类树节点code，空为未分类）
   */
  category?: string;
}

/**
 * 技能使用情况
 * 对应后端 com.maizi.ai.agent.skill.model.SkillUsageInfo
 */
export interface SkillUsageInfo {
  skillId: string;
  viewCount?: number;
  useCount?: number;
  patchCount?: number;
  state?: string;
  pinned?: boolean;
  lastViewedAt?: string;
  lastUsedAt?: string;
  lastPatchedAt?: string;
}

/**
 * 技能草稿（前端表单用，无直接后端对应）
 */
export interface SkillDraft {
  skillId?: string;
  skillName: string;
  skillDescription?: string;
  skillType?: string;
  skillContent: string;
  boundTools?: string[];
  skillVersion?: number;
  trustLevel?: string;
}

/**
 * 技能版本摘要
 */
export interface SkillVersionSummary {
  version: number;
  changeLog?: string;
  fingerprint?: string;
  createUser?: string;
  createTime?: string;
  qualityScore?: number;
  evalDimensions?: string;
  evalModel?: string;
  evaluatedTime?: string;
}

/**
 * 技能质量评测结果
 */
export interface SkillEvalResult {
  skillId?: string;
  version?: number;
  qualityScore?: number;
  dimensions?: Record<string, number>;
  suggestions?: string;
  evalModel?: string;
  evaluatedTime?: string;
}

/**
 * 技能版本详情（含技能内容全文）
 */
export interface SkillVersionDetail extends SkillVersionSummary {
  skillId?: string;
  skillName?: string;
  skillContent?: string;
}
