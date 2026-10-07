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
package com.yangqiongai.ai.platform.system.spi;

import com.yangqiongai.ai.platform.bss.scope.auth.AuthContext;
import com.yangqiongai.ai.platform.system.entity.SystemMenu;

import java.util.List;

/**
 * 菜单访问过滤
 * <p>
 * 运行端菜单拼装管线的最后一步扩展点：社区默认全放行，
 * 企业版按角色权限点过滤（permission_code 非空的菜单需用户持有）。
 * </p>
 * @author yangqiong
 */
public interface MenuAccessFilter {

    /**
     * 过滤当前用户可见的菜单
     * @param menus
     * @param context
     * @return
     */
    List<SystemMenu> filter(List<SystemMenu> menus, AuthContext context);
}
