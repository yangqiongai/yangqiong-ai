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
import type { AxiosError } from 'axios';

/**
 * 判断错误是否为模块未启用的 404 错误
 * opt-in 模块（prompt / integration）未启用时后端返回 404
 * @param error
 * @return
 */
export function isModuleNotEnabledError(error: unknown): boolean {
  if (!error || typeof error !== 'object') return false;
  const status = (error as AxiosError).response?.status;
  return status === 404;
}
