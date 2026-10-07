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
import { create } from 'zustand';
import {
  persist,
  type PersistOptions,
  type PersistStorage,
  type StorageValue,
} from 'zustand/middleware';
import type {
  LoginResult,
  LoginUserBrief,
  TenantRole,
} from '@yangqiong/shared';
import { STORAGE_KEYS } from '@yangqiong/shared';

interface AuthState {
  token: string | null;
  user: LoginUserBrief | null;
  tenantId?: string;
  platformAdmin: boolean;
  tenantRole?: TenantRole;
  setAuth: (result: LoginResult) => void;
  switchTenant: (token: string, tenantId: string) => void;
  logout: () => void;
}

/**
 * 需要持久化的字段（不含 actions）
 */
type AuthPersist = Pick<
  AuthState,
  'token' | 'user' | 'tenantId' | 'platformAdmin' | 'tenantRole'
>;

/**
 * 扁平化持久化存储
 * 直接以 { token, user, ... } 结构写入 localStorage，保持与 shared getToken('admin') 的扁平 { token } 格式兼容
 */
const flatStorage: PersistStorage<AuthPersist> = {
  getItem: (name): StorageValue<AuthPersist> | null => {
    const raw = localStorage.getItem(name);
    if (!raw) {
      return null;
    }
    try {
      const parsed = JSON.parse(raw);
      // 兼容历史带 state/version 的格式与扁平结构
      if (parsed && typeof parsed === 'object' && 'state' in parsed) {
        return parsed as StorageValue<AuthPersist>;
      }
      return { state: parsed as AuthPersist, version: 0 };
    } catch {
      return null;
    }
  },
  setItem: (name, newValue: StorageValue<AuthPersist>) => {
    localStorage.setItem(name, JSON.stringify(newValue.state));
  },
  removeItem: (name) => {
    localStorage.removeItem(name);
  },
};

const persistOptions: PersistOptions<AuthState, AuthPersist> = {
  name: STORAGE_KEYS.ADMIN_AUTH_STORAGE,
  storage: flatStorage,
  partialize: (state): AuthPersist => ({
    token: state.token,
    user: state.user,
    tenantId: state.tenantId,
    platformAdmin: state.platformAdmin,
    tenantRole: state.tenantRole,
  }),
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      tenantId: undefined,
      platformAdmin: false,
      tenantRole: undefined,
      setAuth: (result) => {
        set({
          token: result.token,
          user: result.user,
          tenantId: result.tenantId,
          platformAdmin: result.platformAdmin,
          tenantRole: result.tenantRole,
        });
      },
      switchTenant: (token, tenantId) => {
        // 仅切换租户上下文，保留平台管理员身份与用户信息
        set({ token, tenantId });
      },
      logout: () => {
        set({
          token: null,
          user: null,
          tenantId: undefined,
          platformAdmin: false,
          tenantRole: undefined,
        });
      },
    }),
    persistOptions
  )
);
