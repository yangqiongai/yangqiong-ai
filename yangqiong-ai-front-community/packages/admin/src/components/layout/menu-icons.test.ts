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
import { describe, expect, it } from 'vitest';
import { AppstoreOutlined, DashboardOutlined, ToolOutlined } from '@ant-design/icons';
import { FALLBACK_MENU_ICON, resolveMenuIcon } from './menu-icons';

describe('resolveMenuIcon', () => {
  it('解析已注册的图标名', () => {
    expect(resolveMenuIcon('DashboardOutlined')).toBe(DashboardOutlined);
    expect(resolveMenuIcon('ToolOutlined')).toBe(ToolOutlined);
  });

  it('未注册图标名回落默认图标', () => {
    expect(resolveMenuIcon('NotExistsOutlined')).toBe(AppstoreOutlined);
    expect(resolveMenuIcon('NotExistsOutlined')).toBe(FALLBACK_MENU_ICON);
  });

  it('空图标名回落默认图标', () => {
    expect(resolveMenuIcon(undefined)).toBe(AppstoreOutlined);
    expect(resolveMenuIcon(null)).toBe(AppstoreOutlined);
    expect(resolveMenuIcon('')).toBe(AppstoreOutlined);
  });
});
