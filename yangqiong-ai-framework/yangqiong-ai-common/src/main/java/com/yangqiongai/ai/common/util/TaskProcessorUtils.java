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

import java.util.List;
import java.util.Map;

/**
 * 任务处理器公共工具
 * @author yangqiong
 */
public final class TaskProcessorUtils {

    private TaskProcessorUtils() {
    }

    /**
     * 判断字符串是否为空白
     * @param str
     * @return
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    /**
     * 截断RAG上下文至最大字符数
     * @param context
     * @param maxChars
     * @return
     */
    public static String clampRagContext(String context, int maxChars) {
        String source = StringUtils.getOrDefault(context, "");
        int limit = maxChars > 0 ? maxChars : 16000;
        if (source.length() <= limit) {
            return source;
        }
        return source.substring(0, limit);
    }

    /**
     * 从请求数据体中解析列表
     * @param body
     * @param key
     * @return
     */
    @SuppressWarnings("unchecked")
    public static List<String> resolveListMetadata(Map<String, Object> body, String key) {
        if (body == null) {
            return List.of();
        }
        Object value = body.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return List.of();
    }

    /**
     * 从请求数据体中解析字符串
     * @param body
     * @param key
     * @return
     */
    public static String resolveMetadataString(Map<String, Object> body, String key) {
        if (body == null) {
            return "";
        }
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }

    /**
     * 从模型回答文本中提取JSON对象字符串
     * <p>优先整体解析，失败时提取首个'{'到最后一个'}'之间的内容，兼容markdown代码块包裹的场景</p>
     * @param text
     * @return
     */
    public static String extractJsonObject(String text) {
        String source = StringUtils.getOrDefault(text, "").trim();
        if (isBlank(source)) {
            return "";
        }
        int start = source.indexOf('{');
        int end = source.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return "";
        }
        return source.substring(start, end + 1);
    }
}
