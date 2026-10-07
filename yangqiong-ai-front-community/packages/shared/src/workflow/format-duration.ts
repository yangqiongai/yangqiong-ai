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
 * 耗时格式化（毫秒入参：1秒内显示毫秒，1分钟内显示秒，超过1分钟显示分秒，空值显示 -）
 * @param v
 * @return
 */
export const formatDuration = (v?: number | null): string => {
  if (v == null || Number.isNaN(v)) {
    return '-';
  }
  if (v < 1000) {
    return `${v}ms`;
  }
  if (v < 60000) {
    return `${(v / 1000).toFixed(1)}秒`;
  }
  return `${Math.floor(v / 60000)}分${Math.round((v % 60000) / 1000)}秒`;
};
