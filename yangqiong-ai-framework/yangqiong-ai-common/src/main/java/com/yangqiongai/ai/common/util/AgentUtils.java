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
package com.yangqiongai.ai.common.util;

/**
 * Agent通用工具
 * @author yangqiong
 * @deprecated 使用 {@link StringUtils} 替代
 */
@Deprecated
public final class AgentUtils {

    private AgentUtils() {
    }

    /**
     * 判断字符串是否为null或空
     * @param str
     * @return
     * @deprecated 使用 {@link StringUtils#isEmpty(String)} 替代
     */
    @Deprecated
    public static boolean isEmpty(String str) {
        return StringUtils.isEmpty(str);
    }

    /**
     * 去除首尾空白并规范化内部空白
     * @param str
     * @return
     * @deprecated 使用 {@link StringUtils#normalize(String)} 替代
     */
    @Deprecated
    public static String normalize(String str) {
        return StringUtils.normalize(str);
    }

    /**
     * 截断字符串，超出部分用省略号代替
     * @param str
     * @param maxLength
     * @return
     * @deprecated 使用 {@link StringUtils#truncate(String, int)} 替代，注意null时StringUtils返回空串而非null
     */
    @Deprecated
    public static String truncate(String str, int maxLength) {
        if (str == null) {
            return null;
        }
        return StringUtils.truncate(str, maxLength);
    }

    /**
     * null安全的toString
     * @param obj
     * @return
     * @deprecated 使用 {@link StringUtils#safeToString(Object)} 替代
     */
    @Deprecated
    public static String safeToString(Object obj) {
        return StringUtils.safeToString(obj);
    }
}
