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
package com.yangqiongai.ai.agent.core.stream;

import com.yangqiongai.ai.common.sse.AiStreamConstants;

/**
 * Thinking标签分割器，剥离&lt;think&gt;标签内容，支持HTML实体兼容
 * @author yangqiong
 */
public class ThinkTagSplitter {

    private String pending = "";
    private boolean inThink = false;

    /**
     * 处理文本块，分离thinking和visible内容
     * @param chunk
     * @return
     */
    public ThinkSplitResult splitChunk(String chunk) {
        String source = pending + normalizeHtmlEntities(chunk);
        pending = "";
        if (source == null || source.isEmpty()) {
            return new ThinkSplitResult("", "");
        }
        int hold = partialTagSuffixLength(source);
        if (hold > 0) {
            pending = source.substring(source.length() - hold);
            source = source.substring(0, source.length() - hold);
        }
        return splitCompleteSource(source);
    }

    /**
     * 刷新剩余内容
     * @return
     */
    public ThinkSplitResult flush() {
        String source = pending;
        pending = "";
        return splitCompleteSource(source);
    }

    /**
     * 静态分割方法，一次性分割不含残留的文本
     * @param chunk
     * @return
     */
    public static ThinkSplitResult split(String chunk) {
        return new ThinkTagSplitter().splitChunk(chunk);
    }

    /**
     * 处理文本块，仅返回非thinking部分（兼容旧接口）
     * @param chunk
     * @return
     */
    public String process(String chunk) {
        ThinkSplitResult result = splitChunk(chunk);
        return result.visible();
    }

    private ThinkSplitResult splitCompleteSource(String source) {
        if (source == null || source.isEmpty()) {
            return new ThinkSplitResult("", "");
        }
        StringBuilder visible = new StringBuilder(source.length());
        StringBuilder reasoning = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            if (!inThink && source.startsWith(AiStreamConstants.THINK_START, index)) {
                inThink = true;
                index += AiStreamConstants.THINK_START.length();
                continue;
            }
            if (inThink && source.startsWith(AiStreamConstants.THINK_END, index)) {
                inThink = false;
                index += AiStreamConstants.THINK_END.length();
                continue;
            }
            if (inThink) {
                reasoning.append(source.charAt(index));
            } else {
                visible.append(source.charAt(index));
            }
            index++;
        }
        return new ThinkSplitResult(visible.toString(), reasoning.toString());
    }

    private int partialTagSuffixLength(String source) {
        int max = Math.min(source.length(),
                Math.max(AiStreamConstants.THINK_START.length(), AiStreamConstants.THINK_END.length()) - 1);
        for (int length = max; length > 0; length--) {
            String suffix = source.substring(source.length() - length);
            if (AiStreamConstants.THINK_START.startsWith(suffix)
                    || AiStreamConstants.THINK_END.startsWith(suffix)) {
                return length;
            }
        }
        return 0;
    }

    /**
     * 归一化HTML实体，将&lt;think&gt;转换为<think>
     * @param chunk
     * @return
     */
    private static String normalizeHtmlEntities(String chunk) {
        if (chunk == null) {
            return "";
        }
        return chunk
                .replace("&lt;think&gt;", AiStreamConstants.THINK_START)
                .replace("&lt;/think&gt;", AiStreamConstants.THINK_END);
    }

    /**
     * 分割结果
     */
    public static class ThinkSplitResult {

        private final String visible;
        private final String reasoning;

        public ThinkSplitResult(String visible, String reasoning) {
            this.visible = visible == null ? "" : visible;
            this.reasoning = reasoning == null ? "" : reasoning;
        }

        /**
         * 获取可见文本
         * @return
         */
        public String visible() {
            return visible;
        }

        /**
         * 获取推理内容
         * @return
         */
        public String reasoning() {
            return reasoning;
        }
    }
}
