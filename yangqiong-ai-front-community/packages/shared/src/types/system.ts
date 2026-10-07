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
 * 系统管理相关类型
 * 字段与后端实体对齐：AiUser (ai-platform-system/entity)
 */

/**
 * 用户
 * 对应后端 com.maizi.ai.platform.system.entity.AiUser（未继承基类，自带字段）
 */
export interface AiUser {
  id?: number;
  username: string;
  password?: string;
  displayName?: string;
  email?: string;
  phone?: string;
  status?: number;
  scopeId?: string;
  createTime?: string;
  updateTime?: string;
  deleted?: number;
}
