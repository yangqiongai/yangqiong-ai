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
import axios, { type AxiosRequestConfig } from 'axios';
import type { ApiResult } from '@yangqiong/shared';
import { getToken, removeToken } from '@yangqiong/shared';
import { useAuthStore } from '@/store/auth-store';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/',
  timeout: 15000,
  headers: {
    'Content-Type': 'application/json',
  },
});

// 请求拦截：注入 Authorization
apiClient.interceptors.request.use(
  (config) => {
    const token = useAuthStore.getState().token ?? getToken('admin');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截：兼容 ApiResult / 裸对象 / 裸数组 / ResponseEntity
apiClient.interceptors.response.use(
  (response) => {
    const data = response.data;
    if (
      data &&
      typeof data === 'object' &&
      !Array.isArray(data) &&
      typeof (data as ApiResult).success === 'boolean'
    ) {
      const result = data as ApiResult;
      if (!result.success) {
        const err = new Error(result.message || '接口请求失败');
        // 透传业务错误码，供页面识别转审批等特殊场景
        (err as Error & { code?: number }).code = result.code;
        return Promise.reject(err);
      }
      return result.data;
    }
    // 裸对象 / 裸数组 / ResponseEntity 直接返回
    return data;
  },
  (error) => {
    const status = error?.response?.status;
    if (status === 401) {
      removeToken('admin');
      if (typeof window !== 'undefined' && window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    return Promise.reject(error);
  }
);

export default apiClient;

export const request = {
  get: <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    apiClient.get(url, config) as unknown as Promise<T>,
  post: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    apiClient.post(url, data, config) as unknown as Promise<T>,
  put: <T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> =>
    apiClient.put(url, data, config) as unknown as Promise<T>,
  delete: <T>(url: string, config?: AxiosRequestConfig): Promise<T> =>
    apiClient.delete(url, config) as unknown as Promise<T>,
};
