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
import dayjs from 'dayjs';
import type { ApiResult } from '../types';
import { TOKEN_KEYS, type AppScope } from '../constants';

/**
 * 读取 Token
 * @param app
 * @return
 */
export function getToken(app: AppScope): string | null {
  const key = TOKEN_KEYS[app];
  const raw = localStorage.getItem(key);
  if (!raw) {
    return null;
  }
  try {
    // 兼容两种存储格式：{token}直存 与 zustand persist包装 {state:{token}}
    const parsed = JSON.parse(raw) as { token?: string; state?: { token?: string } } | string;
    if (typeof parsed === 'string') {
      return parsed;
    }
    if (parsed.token) {
      return parsed.token;
    }
    return parsed.state?.token ?? null;
  } catch {
    return raw;
  }
}

/**
 * 写入 Token
 * @param app
 * @param token
 * @return
 */
export function setToken(app: AppScope, token: string): void {
  const key = TOKEN_KEYS[app];
  const payload = JSON.stringify({ token });
  localStorage.setItem(key, payload);
}

/**
 * 移除 Token
 * @param app
 * @return
 */
export function removeToken(app: AppScope): void {
  const key = TOKEN_KEYS[app];
  localStorage.removeItem(key);
}

/**
 * 构建携带 Token 的认证请求头
 * 裸 fetch 流式请求无法复用 axios 拦截器，统一由此按当前宿主应用注入 Authorization
 * @return
 */
export function getAuthHeaders(): Record<string, string> {
  for (const app of Object.keys(TOKEN_KEYS) as AppScope[]) {
    const token = getToken(app);
    if (token) {
      return { Authorization: `Bearer ${token}` };
    }
  }
  return {};
}

/**
 * 格式化日期
 * @param date
 * @param fmt
 * @return
 */
export function formatDate(
  date?: string | number | Date | dayjs.Dayjs | null,
  fmt: string = 'YYYY-MM-DD HH:mm:ss',
): string {
  if (date === null || date === undefined || date === '') {
    return '';
  }
  return dayjs(date).format(fmt);
}

/**
 * 格式化配额数值，超大数（Long 型"不限"语义）显示为"不限"
 * @param value
 * @return
 */
export function formatQuota(value?: string | number | null): string {
  if (value === null || value === undefined || value === '') {
    return '-';
  }
  const num = typeof value === 'string' ? Number(value) : value;
  if (!Number.isFinite(num) || num >= Number.MAX_SAFE_INTEGER) {
    return '不限';
  }
  return num.toLocaleString();
}

/**
 * 判断配额是否为"不限"（超大数）
 * @param value
 * @return
 */
export function isUnlimitedQuota(value?: string | number | null): boolean {
  if (value === null || value === undefined || value === '') {
    return false;
  }
  const num = typeof value === 'string' ? Number(value) : value;
  return !Number.isFinite(num) || num >= Number.MAX_SAFE_INTEGER;
}

/**
 * 解包 ApiResult，失败时抛错
 * @param res
 * @return
 */
export function handleApiResult<T>(res: ApiResult<T>): T {
  if (!res || !res.success) {
    const message = res?.message || '接口请求失败';
    throw new Error(message);
  }
  return res.data;
}

/**
 * 美化 JSON 文本，非法或空返回 null
 * @param text
 * @return
 */
export function tryPrettyJson(text?: string | null): string | null {
  if (!text) {
    return null;
  }
  try {
    return JSON.stringify(JSON.parse(text), null, 2);
  } catch {
    return null;
  }
}
