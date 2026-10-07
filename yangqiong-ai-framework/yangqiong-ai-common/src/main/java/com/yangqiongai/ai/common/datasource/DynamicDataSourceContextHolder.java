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
package com.yangqiongai.ai.common.datasource;

/**
 * 动态数据源上下文
 * @author yangqiong
 */
public class DynamicDataSourceContextHolder {

    /**
     * 使用ThreadLocal维护变量
     */
    private static final ThreadLocal<String> CONTEXT_HOLDER = new ThreadLocal<>();

    /**
     * 设置数据源
     * @param dataSource
     */
    public static void setDateSource(String dataSource) {
        CONTEXT_HOLDER.set(dataSource);
    }

    /**
     * 获得数据源
     * @return
     */
    public static String getDateSource() {
        return CONTEXT_HOLDER.get();
    }

    /**
     * 清空数据源
     */
    public static void clearDateSource() {
        CONTEXT_HOLDER.remove();
    }
}
