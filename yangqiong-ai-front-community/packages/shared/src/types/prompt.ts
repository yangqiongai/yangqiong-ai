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
 * Prompt 相关类型
 * 字段与后端实体对齐：PromptVersion / ABTestConfig / ABTestResult (ai-platform-prompt/entity，继承 ScopeEntity)
 */

/**
 * Prompt 版本
 * 对应后端 com.maizi.ai.platform.prompt.entity.PromptVersion（继承 ScopeEntity）
 */
export interface PromptVersion {
  id?: number;
  promptCode: string;
  category?: string;
  content?: string;
  version?: number;
  changeLog?: string;
  status?: string;
  operationType?: string;
  fingerprint?: string;
  readonly?: boolean;
  visible?: boolean;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * AB 测试配置
 * 对应后端 com.maizi.ai.platform.prompt.entity.ABTestConfig（继承 ScopeEntity）
 */
export interface ABTestConfig {
  id?: number;
  testName: string;
  promptCode: string;
  versionA?: number;
  versionB?: number;
  trafficPercentA?: number;
  startTime?: string;
  endTime?: string;
  status?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}

/**
 * AB 测试结果
 * 对应后端 com.maizi.ai.platform.prompt.entity.ABTestResult（继承 ScopeEntity）
 */
export interface ABTestResult {
  id?: number;
  testId?: number;
  promptCode?: string;
  version?: number;
  invokeCount?: number;
  successCount?: number;
  avgLatencyMs?: number;
  avgScore?: number;
  recordDate?: string;
  scopeId?: string;
  createUser?: string;
  createTime?: string;
  updateUser?: string;
  updateTime?: string;
}
