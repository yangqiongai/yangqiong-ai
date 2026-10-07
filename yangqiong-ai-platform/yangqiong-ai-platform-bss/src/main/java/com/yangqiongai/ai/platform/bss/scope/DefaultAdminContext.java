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
package com.yangqiongai.ai.platform.bss.scope;

/**
 * 默认管理员上下文
 * @author yangqiong
 */
public class DefaultAdminContext implements AdminContext {

    /**
     * 默认始终返回非管理员
     * @return
     */
    @Override
    public boolean isAdmin() {
        return false;
    }

    /**
     * 默认空实现
     * @param admin
     * @return
     */
    @Override
    public void setAdmin(boolean admin) {
    }

    /**
     * 默认空实现
     * @return
     */
    @Override
    public void clear() {
    }
}
