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

import java.util.ArrayList;
import java.util.List;

/**
 * Token估算工具
 * @author yangqiong
 */
public final class TokenEstimator {

    private TokenEstimator() {
    }

    /**
     * 默认中文字符的token系数，约1.5个token每字符
     */
    private static final double DEFAULT_CHINESE_TOKEN_RATIO = 1.5;

    /**
     * 默认英文单词的token系数，约1.3个token每单词
     */
    private static final double DEFAULT_ENGLISH_TOKEN_RATIO = 1.3;

    /**
     * 中文字符的token系数，运行时可通过 setRatios 覆盖
     */
    private static volatile double chineseTokenRatio = DEFAULT_CHINESE_TOKEN_RATIO;

    /**
     * 英文单词的token系数，运行时可通过 setRatios 覆盖
     */
    private static volatile double englishTokenRatio = DEFAULT_ENGLISH_TOKEN_RATIO;

    /**
     * 配置中英文Token估算系数，覆盖默认值
     * @param chinese
     * @param english
     */
    public static void setRatios(double chinese, double english) {
        if (chinese > 0) {
            chineseTokenRatio = chinese;
        }
        if (english > 0) {
            englishTokenRatio = english;
        }
    }

    /**
     * 恢复默认Token估算系数
     */
    public static void resetRatios() {
        chineseTokenRatio = DEFAULT_CHINESE_TOKEN_RATIO;
        englishTokenRatio = DEFAULT_ENGLISH_TOKEN_RATIO;
    }

    /**
     * 估算文本的token数量，区分中英文
     * @param text
     * @return
     */
    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int chineseCount = 0;
        int englishWordCount = 0;
        StringBuilder englishBuffer = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isChinese(c)) {
                // 遇到中文字符，先结算之前积累的英文
                englishWordCount += countEnglishWords(englishBuffer.toString());
                englishBuffer.setLength(0);
                chineseCount++;
            } else {
                englishBuffer.append(c);
            }
        }
        // 结算剩余英文
        englishWordCount += countEnglishWords(englishBuffer.toString());

        return (int) Math.ceil(chineseCount * chineseTokenRatio + englishWordCount * englishTokenRatio);
    }

    /**
     * 批量估算文本的token数量
     * @param texts
     * @return
     */
    public static List<Integer> estimateTokens(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return new ArrayList<>();
        }
        List<Integer> results = new ArrayList<>(texts.size());
        for (String text : texts) {
            results.add(estimateTokens(text));
        }
        return results;
    }

    /**
     * 判断文本token数是否在预算内
     * @param text
     * @param maxTokens
     * @return
     */
    public static boolean isWithinBudget(String text, int maxTokens) {
        return estimateTokens(text) <= maxTokens;
    }

    /**
     * 判断字符是否为中文字符
     * @param c
     * @return
     */
    private static boolean isChinese(char c) {
        Character.UnicodeBlock ub = Character.UnicodeBlock.of(c);
        return ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || ub == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || ub == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT
                || ub == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
                || ub == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }

    /**
     * 统计英文字符串中的单词数
     * @param text
     * @return
     */
    private static int countEnglishWords(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0;
        }
        String trimmed = text.trim();
        // 按空白分割统计单词数
        String[] words = trimmed.split("\\s+");
        int count = 0;
        for (String word : words) {
            if (!word.isEmpty()) {
                count++;
            }
        }
        return count;
    }
}
