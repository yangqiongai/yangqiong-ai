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
 * 租户相关类型
 * 字段与后端实体对齐：Tenant / TenantMember / TenantPlan / TenantQuota / TenantUsage (ai-platform-tenant)
 * TenantCreateDTO / LoginDTO / LoginResultVO (ai-platform-tenant/dto)
 */

/**
 * 租户
 * 对应后端 com.maizi.ai.platform.tenant.entity.Tenant（未继承基类，自带审计字段）
 */
export interface Tenant {
  id?: number;
  tenantId: string;
  tenantName: string;
  tenantCode?: string;
  tenantType?: string;
  status?: number;
  contactEmail?: string;
  modelConfig?: string;
  planId?: number;
  planCode?: string;
  planExpireTime?: string;
  planAutoRenew?: number;
  createTime?: string;
  updateTime?: string;
  createUser?: string;
  updateUser?: string;
  deleted?: number;
}

/**
 * 租户套餐
 * 对应后端 com.maizi.ai.platform.tenant.entity.TenantPlan（未继承基类）
 */
export interface TenantPlan {
  id?: number;
  planId?: number;
  planCode: string;
  planName: string;
  planType?: string;
  planTier?: number;
  maxUsers?: number;
  maxKnowledgeBases?: number;
  maxStorageBytes?: number;
  monthlyTokenQuota?: number;
  maxConcurrentSessions?: number;
  allowedModels?: string;
  allowedSkills?: string;
  allowedTools?: string;
  enableMcp?: number;
  enableWorkflow?: number;
  enableText2sql?: number;
  enableRagGraph?: number;
  priceMonthly?: number;
  priceYearly?: number;
  trialDays?: number;
  status?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户成员
 * 对应后端 com.maizi.ai.platform.tenant.entity.TenantMember（未继承基类）
 */
export interface TenantMember {
  id?: number;
  tenantId: string;
  userId: string;
  username?: string;
  role?: string;
  status?: number;
  joinTime?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户配额
 * 对应后端 com.maizi.ai.platform.tenant.entity.TenantQuota（未继承基类）
 * Long 大数配额字段（maxStorageBytes/monthlyTokenQuota）后端序列化为字符串
 */
export interface TenantQuota {
  id?: number;
  tenantId: string;
  maxUsers?: number;
  maxKnowledgeBases?: number;
  maxStorageBytes?: string | number;
  monthlyTokenQuota?: string | number;
  maxConcurrentSessions?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 租户用量
 * 对应后端 com.maizi.ai.platform.tenant.entity.TenantUsage（未继承基类）
 */
export interface TenantUsage {
  id?: number;
  tenantId: string;
  usageDate?: string;
  tokenCount?: number;
  storageBytes?: number;
  apiCallCount?: number;
  createTime?: string;
  updateTime?: string;
}

/**
 * 登录参数
 * 对应后端 com.maizi.ai.platform.tenant.dto.LoginDTO
 */
export interface LoginDTO {
  username: string;
  password: string;
  tenantId?: string;
}

/**
 * 登录返回结果
 * 对应后端 com.maizi.ai.platform.tenant.dto.LoginResultVO
 */
export interface LoginResultVO {
  token: string;
  user: {
    id: string;
    name: string;
  };
  tenantId?: string;
  platformAdmin?: boolean;
  tenantRole?: string;
}
