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

import java.util.UUID;

/**
 * 字符串工具
 * @author yangqiong
 */
public final class StringUtils {

    private StringUtils() {
    }

    /**
     * 获取字符串默认值，null时返回空串
     * @param value
     * @return
     */
    public static String getOrDefault(String value) {
        return value == null ? "" : value;
    }

    /**
     * 获取字符串默认值，null或空串时返回指定默认值
     * @param value
     * @param defaultValue
     * @return
     */
    public static String getOrDefault(String value, String defaultValue) {
        return (value == null || value.isEmpty()) ? defaultValue : value;
    }

    /**
     * 获取对象字符串默认值，null时返回空串
     * @param value
     * @return
     */
    public static String getOrDefault(Object value) {
        return value == null ? "" : value.toString();
    }

    /**
     * 判断字符串是否为null或空
     * @param value
     * @return
     */
    public static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    /**
     * 判断字符串是否非null且非空
     * @param value
     * @return
     */
    public static boolean isNotEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    /**
     * 判断字符串是否为空白（null、空串或纯空白字符）
     * @param value
     * @return
     */
    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * 判断字符串是否非空白（非null、非空串且非纯空白字符）
     * @param value
     * @return
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 截断字符串，超出部分用省略号代替，null返回空串
     * @param text
     * @param maxLength
     * @return
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (maxLength <= 0) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        if (maxLength <= 3) {
            return text.substring(0, maxLength);
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    /**
     * 生成紧凑ID（UUID去横线）
     * @return
     */
    public static String generateCompactId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 去除首尾空白并规范化内部空白
     * @param str
     * @return
     */
    public static String normalize(String str) {
        if (str == null) {
            return null;
        }
        String trimmed = str.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return trimmed.replaceAll("\\s+", " ");
    }

    /**
     * null安全的toString
     * @param obj
     * @return
     */
    public static String safeToString(Object obj) {
        if (obj == null) {
            return null;
        }
        return obj.toString();
    }
}
