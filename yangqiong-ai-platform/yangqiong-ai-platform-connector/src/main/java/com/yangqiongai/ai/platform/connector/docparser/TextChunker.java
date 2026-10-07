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
package com.yangqiongai.ai.platform.connector.docparser;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本分块
 * <p>
 * 按行优先聚合至目标尺寸，超长单行硬切；相邻分块按重叠字符衔接，保证语义连续。
 * </p>
 * @author yangqiong
 */
public final class TextChunker {

    private TextChunker() {
    }

    /**
     * 将文本按尺寸与重叠切分
     * @param text 原始文本
     * @param size 分块尺寸（字符）
     * @param overlap 相邻分块重叠（字符）
     * @return 分块清单，文本为空返回空清单
     */
    public static List<String> chunk(String text, int size, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        int effectiveSize = size < 1 ? Integer.MAX_VALUE : size;
        int effectiveOverlap = Math.min(Math.max(0, overlap), effectiveSize - 1);

        int start = 0;
        int length = text.length();
        while (start < length) {
            int end = Math.min(start + effectiveSize, length);
            if (end < length) {
                int breakPoint = lastBreakBefore(text, start, end);
                if (breakPoint > start) {
                    end = breakPoint;
                }
            }
            chunks.add(text.substring(start, end).trim());
            if (end >= length) {
                break;
            }
            int nextStart = Math.max(end - effectiveOverlap, start + 1);
            nextStart = nextBreakAfter(text, nextStart, end);
            start = nextStart;
        }
        return chunks.stream().filter(part -> !part.isEmpty()).toList();
    }

    /**
     * 在窗口内寻找最后一个行边界，找不到返回-1
     * @param text 原文
     * @param start 窗口起点
     * @param end 窗口终点
     * @return 边界位置（不含），无则-1
     */
    private static int lastBreakBefore(String text, int start, int end) {
        for (int i = end; i > start; i--) {
            if (text.charAt(i - 1) == '\n') {
                return i;
            }
        }
        return -1;
    }

    /**
     * 重叠起点跳过行首残余半行，从下一完整行开始
     * @param text 原文
     * @param candidate 候选起点
     * @param previousEnd 上一分块终点
     * @return 实际起点
     */
    private static int nextBreakAfter(String text, int candidate, int previousEnd) {
        for (int i = candidate; i < previousEnd; i++) {
            if (text.charAt(i) == '\n') {
                return Math.min(i + 1, previousEnd);
            }
        }
        return candidate;
    }
}
