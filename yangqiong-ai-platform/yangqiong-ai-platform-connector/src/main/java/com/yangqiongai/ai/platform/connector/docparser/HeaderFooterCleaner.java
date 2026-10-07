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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 页眉页脚清洗
 * <p>
 * 针对PDF按页提取的文本：在多页中重复出现且长度受限的首/尾行视为页眉/页脚并移除，
 * 单页文档无法判定重复性时不做处理。
 * </p>
 * @author yangqiong
 */
public final class HeaderFooterCleaner {

    /**
     * 判定页眉/页脚的最少重复页数
     */
    private static final int MIN_REPEAT_PAGES = 2;

    /**
     * 判定页眉/页脚的重复页占比阈值
     */
    private static final double REPEAT_RATIO = 0.6;

    /**
     * 参与判定的行最大长度（超长行视为正文）
     */
    private static final int MAX_LINE_LENGTH = 100;

    private HeaderFooterCleaner() {
    }

    /**
     * 清洗按页文本中的页眉页脚
     * @param pageTexts 按页顺序的文本清单
     * @return 清洗后的文本清单（与入参一一对应）
     */
    public static List<String> clean(List<String> pageTexts) {
        if (pageTexts == null || pageTexts.size() < MIN_REPEAT_PAGES) {
            return pageTexts;
        }
        int threshold = Math.max(MIN_REPEAT_PAGES, (int) Math.ceil(pageTexts.size() * REPEAT_RATIO));
        List<String> headers = findRepeatedLines(pageTexts, true, threshold);
        List<String> footers = findRepeatedLines(pageTexts, false, threshold);

        List<String> cleaned = new ArrayList<>(pageTexts.size());
        for (String text : pageTexts) {
            cleaned.add(removeLines(text, headers, true, footers, true));
        }
        return cleaned;
    }

    /**
     * 统计并找出重复出现的首行/尾行
     * @param pageTexts 按页文本
     * @param header true找首行，false找尾行
     * @param threshold 判定阈值页数
     * @return 重复行清单
     */
    private static List<String> findRepeatedLines(List<String> pageTexts, boolean header, int threshold) {
        Map<String, Integer> counter = new HashMap<>();
        for (String text : pageTexts) {
            String line = boundaryLine(text, header);
            if (line != null) {
                counter.merge(line, 1, Integer::sum);
            }
        }
        List<String> repeated = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : counter.entrySet()) {
            if (entry.getValue() >= threshold) {
                repeated.add(entry.getKey());
            }
        }
        return repeated;
    }

    /**
     * 取文本的首行或尾行
     * @param text 页文本
     * @param header true取首行，false取尾行
     * @return 非空且不超长的行，否则null
     */
    private static String boundaryLine(String text, boolean header) {
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            int index = header ? i : lines.length - 1 - i;
            String line = lines[index].trim();
            if (!line.isEmpty()) {
                return line.length() <= MAX_LINE_LENGTH ? line : null;
            }
        }
        return null;
    }

    /**
     * 从文本中移除匹配的页眉/页脚行
     * @param text 页文本
     * @param headers 页眉行清单
     * @param fromHeader 页眉从首部起扫
     * @param footers 页脚行清单
     * @param fromFooter 页脚从尾部起扫
     * @return 移除后的文本
     */
    private static String removeLines(String text, List<String> headers, boolean fromHeader,
                                      List<String> footers, boolean fromFooter) {
        String[] lines = text.split("\n", -1);
        int start = 0;
        int end = lines.length;
        if (fromHeader) {
            while (start < end && matchesAny(lines[start], headers)) {
                start++;
            }
        }
        if (fromFooter) {
            while (end > start && matchesAny(lines[end - 1], footers)) {
                end--;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines[i]);
            if (i < end - 1) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * 行是否命中清单（trim后精确匹配）
     * @param line 行
     * @param candidates 候选清单
     * @return
     */
    private static boolean matchesAny(String line, List<String> candidates) {
        String trimmed = line.trim();
        for (String candidate : candidates) {
            if (candidate.equals(trimmed)) {
                return true;
            }
        }
        return false;
    }
}
