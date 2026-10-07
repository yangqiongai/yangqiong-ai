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
package com.yangqiongai.ai.rag.cleaner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 通用文档清洗器
 * @author yangqiong
 */
@Service
public class UniversalDocumentCleaner {

    private static final Logger log = LoggerFactory.getLogger(UniversalDocumentCleaner.class);

    private static final Pattern MULTIPLE_WHITESPACE = Pattern.compile("[ \\t]+");

    private static final Pattern EXCESSIVE_NEWLINES = Pattern.compile("\\n{4,}");

    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");

    private static final Pattern PAGE_NUMBER_LINE = Pattern.compile(
            "^\\s*(第\\s*\\d+\\s*页|Page\\s*\\d+|\\-+\\s*\\d+\\s*\\-+)\\s*$", Pattern.MULTILINE);

    private static final Pattern WATERMARK_PATTERN = Pattern.compile(
            "^\\s*(水印|WATERMARK|CONFIDENTIAL|内部资料|仅供参考|草稿|DRAFT|样本|SAMPLE|机密|SECRET|严禁复制|DO NOT COPY)\\s*$",
            Pattern.MULTILINE | Pattern.CASE_INSENSITIVE);

    /**
     * 允许的标点和符号白名单（中文标点 + 数学符号 + 常用符号）
     */
    private static final Set<Character> ALLOWED_PUNCTUATION = buildAllowedPunctuation();

    private static Set<Character> buildAllowedPunctuation() {
        Set<Character> set = new HashSet<>();
        // 中文标点
        addChars(set, "，。、；：？！（）【】《》—…·～");
        set.add('\u201C'); // "
        set.add('\u201D'); // "
        set.add('\u2018'); // '
        set.add('\u2019'); // '
        // 数学符号
        addChars(set, "±×÷∈∉⊂⊃∪∩∅∀∃¬∧∨≈≠≤≥∞∑∏∫∂∇√∝");
        set.add('\u21D2'); // ⇒
        set.add('\u21D4'); // ⇔
        // 常用符号
        addChars(set, "℃℉°‰％￥＄€£¥©®™§№※☆★○●△▲◇◆□■♩♪♫♬");
        return set;
    }

    private static void addChars(Set<Character> set, String chars) {
        for (char c : chars.toCharArray()) {
            set.add(c);
        }
    }

    /**
     * 清洗文档内容
     * @param content
     * @return
     */
    public String clean(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        // 归一化Unicode字符（将组合字符统一为预组合形式，避免编码不一致）
        String cleaned = Normalizer.normalize(content, Normalizer.Form.NFC);

        // 去除控制字符
        cleaned = CONTROL_CHARS.matcher(cleaned).replaceAll("");

        // 去除页码行
        cleaned = PAGE_NUMBER_LINE.matcher(cleaned).replaceAll("");

        // 去除水印行
        cleaned = WATERMARK_PATTERN.matcher(cleaned).replaceAll("");

        // 过滤垃圾行
        cleaned = filterGarbageLines(cleaned);

        // 修复断行段落
        cleaned = stitchBrokenParagraphs(cleaned);

        // 去除多余空白（仅处理行内空白，保留换行）
        cleaned = cleanExcessiveWhitespace(cleaned);

        // 压缩过多空行
        cleaned = EXCESSIVE_NEWLINES.matcher(cleaned).replaceAll("\n\n");

        return cleaned.trim();
    }

    /**
     * 判断是否为垃圾行
     * @param line
     * @return
     */
    public boolean isGarbageLine(String line) {
        if (line == null || line.isBlank()) {
            return false;
        }

        String trimmed = line.trim();

        // 空行不是垃圾行
        if (trimmed.isEmpty()) {
            return false;
        }

        // 纯符号行（排除白名单中的标点）
        long meaningfulCharCount = trimmed.chars()
                .filter(ch -> {
                    if (Character.isLetterOrDigit(ch)) {
                        return true;
                    }
                    // CJK统一汉字范围
                    if (ch >= 0x4E00 && ch <= 0x9FFF) {
                        return true;
                    }
                    // 白名单标点
                    if (ALLOWED_PUNCTUATION.contains((char) ch)) {
                        return true;
                    }
                    // ASCII常用标点
                    if (".,;:!?\"'()-[]{}/<>@#$%^&*_+=|\\~`".indexOf(ch) >= 0) {
                        return true;
                    }
                    return false;
                })
                .count();

        // 如果有意义的字符占比低于20%，视为垃圾行
        if (trimmed.length() > 3 && meaningfulCharCount < trimmed.length() * 0.2) {
            return true;
        }

        // 重复字符行（如 "====", "----", "...."）
        if (trimmed.length() > 5) {
            long distinctChars = trimmed.chars().distinct().count();
            if (distinctChars <= 2) {
                return true;
            }
        }

        return false;
    }

    /**
     * 修复断行段落
     * @param content
     * @return
     */
    public String stitchBrokenParagraphs(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }

        String[] lines = content.split("\\n");
        StringBuilder result = new StringBuilder();
        String previousLine = null;

        for (String line : lines) {
            if (previousLine == null) {
                previousLine = line;
                continue;
            }

            String trimmedPrev = previousLine.trim();
            String trimmedCurr = line.trim();

            // 判断是否需要合并：前一行非空、不以句末标点结尾、当前行非空、不以段落标记开头
            if (shouldStitch(trimmedPrev, trimmedCurr)) {
                previousLine = previousLine.trim() + trimmedCurr;
            } else {
                result.append(previousLine).append("\n");
                previousLine = line;
            }
        }

        if (previousLine != null) {
            result.append(previousLine);
        }

        return result.toString();
    }

    /**
     * 判断两行是否需要合并
     * @param prevLine
     * @param currLine
     * @return
     */
    private boolean shouldStitch(String prevLine, String currLine) {
        if (prevLine.isEmpty() || currLine.isEmpty()) {
            return false;
        }

        // 当前行为标题、列表、表格等结构化内容时不合并
        if (currLine.startsWith("#") || currLine.startsWith("- ") || currLine.startsWith("* ")
                || currLine.startsWith("|") || currLine.startsWith(">") || currLine.startsWith("```")
                || currLine.startsWith("1.") || currLine.startsWith("2.") || currLine.startsWith("3.")) {
            return false;
        }

        // 前一行以句末标点结尾时不合并
        char lastChar = prevLine.charAt(prevLine.length() - 1);
        if (isSentenceEnd(lastChar)) {
            return false;
        }

        // 前一行以冒号结尾时不合并（通常是标题或标签行）
        if (lastChar == '：' || lastChar == ':') {
            return false;
        }

        return true;
    }

    /**
     * 判断是否为句末标点
     * @param ch
     * @return
     */
    private boolean isSentenceEnd(char ch) {
        return ch == '。' || ch == '？' || ch == '！' || ch == '.' || ch == '?' || ch == '!'
                || ch == '；' || ch == ';';
    }

    /**
     * 过滤垃圾行
     * @param content
     * @return
     */
    private String filterGarbageLines(String content) {
        return content.lines()
                .filter(line -> !isGarbageLine(line))
                .collect(Collectors.joining("\n"));
    }

    /**
     * 清除多余空白（仅行内，保留换行结构）
     * @param content
     * @return
     */
    private String cleanExcessiveWhitespace(String content) {
        // 逐行处理，只压缩行内空白
        return content.lines()
                .map(line -> MULTIPLE_WHITESPACE.matcher(line).replaceAll(" "))
                .collect(Collectors.joining("\n"));
    }
}
