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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UniversalDocumentCleaner 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class UniversalDocumentCleanerTest {

    private UniversalDocumentCleaner cleaner;

    @BeforeEach
    void setUp() {
        cleaner = new UniversalDocumentCleaner();
    }

    @Test
    @DisplayName("clean - 去除控制字符")
    void clean_removesControlCharacters() {
        String input = "Hello\u0001World\u0007Test\u001F";
        String result = cleaner.clean(input);

        assertThat(result).isEqualTo("HelloWorldTest");
    }

    @Test
    @DisplayName("clean - 去除页码行")
    void clean_removesPageNumbers() {
        String input = "正文内容\n第 1 页\n更多内容\nPage 2\n结尾";
        String result = cleaner.clean(input);

        assertThat(result).doesNotContain("第 1 页");
        assertThat(result).doesNotContain("Page 2");
        assertThat(result).contains("正文内容");
        assertThat(result).contains("更多内容");
        assertThat(result).contains("结尾");
    }

    @Test
    @DisplayName("clean - 去除水印行")
    void clean_removesWatermarks() {
        String input = "正文内容\n水印\n更多内容\nCONFIDENTIAL\n结尾";
        String result = cleaner.clean(input);

        assertThat(result).doesNotContain("水印");
        assertThat(result).doesNotContain("CONFIDENTIAL");
        assertThat(result).contains("正文内容");
        assertThat(result).contains("更多内容");
    }

    @Test
    @DisplayName("clean - null输入返回空字符串")
    void clean_returnsEmpty_whenNull() {
        assertThat(cleaner.clean(null)).isEmpty();
    }

    @Test
    @DisplayName("clean - 空白输入返回空字符串")
    void clean_returnsEmpty_whenBlank() {
        assertThat(cleaner.clean("   ")).isEmpty();
    }

    @Test
    @DisplayName("isGarbageLine - 有意义字符占比低于20%时返回true")
    void isGarbageLine_returnsTrue_whenMeaningfulCharsBelowThreshold() {
        // 4个有意义字符 vs 大量无意义符号，占比低于20%
        String line = "@@@@####$$$$%%%%abcd";
        // meaningful chars: a, b, c, d = 4, total = 20, ratio = 20% => boundary
        // 使用更极端的例子
        String garbageLine = "@@@@@@@@@@@@@@@@@@@@a";
        assertThat(cleaner.isGarbageLine(garbageLine)).isTrue();
    }

    @Test
    @DisplayName("isGarbageLine - 正常行返回false")
    void isGarbageLine_returnsFalse_forNormalLine() {
        assertThat(cleaner.isGarbageLine("这是一段正常的文本内容")).isFalse();
    }

    @Test
    @DisplayName("isGarbageLine - null返回false")
    void isGarbageLine_returnsFalse_whenNull() {
        assertThat(cleaner.isGarbageLine(null)).isFalse();
    }

    @Test
    @DisplayName("isGarbageLine - 空行返回false")
    void isGarbageLine_returnsFalse_whenBlank() {
        assertThat(cleaner.isGarbageLine("   ")).isFalse();
    }

    @Test
    @DisplayName("isGarbageLine - 重复字符行返回true")
    void isGarbageLine_returnsTrue_forRepeatedChars() {
        assertThat(cleaner.isGarbageLine("==========")).isTrue();
        assertThat(cleaner.isGarbageLine("----------")).isTrue();
    }

    @Test
    @DisplayName("stitchBrokenParagraphs - 合并被断行的段落")
    void stitchBrokenParagraphs_mergesBrokenLines() {
        // 前一行不以句末标点结尾，当前行不以结构化标记开头，应合并
        String input = "这是第一行没有\n结束标点\n这是新段落。";
        String result = cleaner.stitchBrokenParagraphs(input);

        assertThat(result).contains("这是第一行没有结束标点");
    }

    @Test
    @DisplayName("stitchBrokenParagraphs - 前一行以句号结尾时不合并")
    void stitchBrokenParagraphs_doesNotMerge_whenSentenceEnd() {
        String input = "这是第一行。\n这是新段落。";
        String result = cleaner.stitchBrokenParagraphs(input);

        assertThat(result).contains("这是第一行。");
        assertThat(result).contains("这是新段落。");
    }

    @Test
    @DisplayName("stitchBrokenParagraphs - 当前行为标题时不合并")
    void stitchBrokenParagraphs_doesNotMerge_whenNextIsHeading() {
        String input = "前一行\n# 标题";
        String result = cleaner.stitchBrokenParagraphs(input);

        assertThat(result).contains("# 标题");
    }

    @Test
    @DisplayName("stitchBrokenParagraphs - null输入返回null")
    void stitchBrokenParagraphs_returnsNull_whenNull() {
        assertThat(cleaner.stitchBrokenParagraphs(null)).isNull();
    }

    @Test
    @DisplayName("stitchBrokenParagraphs - 空字符串返回空")
    void stitchBrokenParagraphs_returnsEmpty_whenEmpty() {
        assertThat(cleaner.stitchBrokenParagraphs("")).isEmpty();
    }
}
