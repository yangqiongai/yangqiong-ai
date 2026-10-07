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

import com.yangqiongai.ai.agent.core.stream.ThinkTagSplitter.ThinkSplitResult;
import com.yangqiongai.ai.common.sse.AiStreamConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ThinkTagSplitter单元测试
 */
class ThinkTagSplitterTest {

    private ThinkTagSplitter splitter;

    @BeforeEach
    void setUp() {
        splitter = new ThinkTagSplitter();
    }

    @Test
    @DisplayName("不含thinking标签的文本原样返回")
    void textWithoutThinkingTags_returnedAsIs() {
        ThinkSplitResult result = splitter.splitChunk("Hello World");

        assertThat(result.visible()).isEqualTo("Hello World");
        assertThat(result.reasoning()).isEmpty();
    }

    @Test
    @DisplayName("含thinking标签的文本分离内容")
    void textWithThinkingTags_contentSeparated() {
        String input = "Hello" + AiStreamConstants.THINK_START + "reasoning" + AiStreamConstants.THINK_END + "visible";
        ThinkSplitResult result = splitter.splitChunk(input);

        assertThat(result.visible()).isEqualTo("Hellovisible");
        assertThat(result.reasoning()).isEqualTo("reasoning");
    }

    @Test
    @DisplayName("HTML实体形式的thinking标签也能正确分离")
    void htmlEntityThinkingTags_contentSeparated() {
        ThinkSplitResult result = splitter.splitChunk("Hello&lt;think&gt;reasoning&lt;/think&gt;visible");

        assertThat(result.visible()).isEqualTo("Hellovisible");
        assertThat(result.reasoning()).isEqualTo("reasoning");
    }

    @Test
    @DisplayName("跨chunk分割thinking标签")
    void splitAcrossChunks() {
        // 第一个chunk包含部分thinking标签
        String tagStart = AiStreamConstants.THINK_START;
        String tagEnd = AiStreamConstants.THINK_END;

        // 将标签拆分为两部分
        String firstPart = tagStart.substring(0, tagStart.length() - 1);
        String secondPart = tagStart.substring(tagStart.length() - 1);

        ThinkSplitResult result1 = splitter.splitChunk("Hello" + firstPart);
        assertThat(result1.visible()).isEqualTo("Hello");

        ThinkSplitResult result2 = splitter.splitChunk(secondPart + "reasoning" + tagEnd + "World");
        assertThat(result2.visible()).isEqualTo("World");
        assertThat(result2.reasoning()).isEqualTo("reasoning");
    }

    @Test
    @DisplayName("flush()返回剩余缓冲区内容")
    void flush_returnsRemainingBuffer() {
        // 提供一个不完整的标签，会被缓冲
        String tagStart = AiStreamConstants.THINK_START;
        String partial = tagStart.substring(0, tagStart.length() - 1);
        splitter.splitChunk("Hello" + partial);

        ThinkSplitResult result = splitter.flush();

        // 缓冲区中应该有剩余内容
        String combined = result.visible() + result.reasoning();
        assertThat(combined).isNotEmpty();
    }

    @Test
    @DisplayName("静态split()方法")
    void staticSplitMethod() {
        String input = "Hello" + AiStreamConstants.THINK_START + "reasoning" + AiStreamConstants.THINK_END + "World";
        ThinkSplitResult result = ThinkTagSplitter.split(input);

        assertThat(result.visible()).isEqualTo("HelloWorld");
        assertThat(result.reasoning()).isEqualTo("reasoning");
    }

    @Test
    @DisplayName("空输入返回空结果")
    void emptyInput_returnsEmptyResult() {
        ThinkSplitResult result = splitter.splitChunk("");

        assertThat(result.visible()).isEmpty();
        assertThat(result.reasoning()).isEmpty();
    }

    @Test
    @DisplayName("null输入返回空结果")
    void nullInput_returnsEmptyResult() {
        ThinkSplitResult result = splitter.splitChunk(null);

        assertThat(result.visible()).isEmpty();
        assertThat(result.reasoning()).isEmpty();
    }

    @Test
    @DisplayName("多个thinking标签块正确分离")
    void multipleThinkingBlocks_separatedCorrectly() {
        String input = "A" + AiStreamConstants.THINK_START + "r1" + AiStreamConstants.THINK_END
                + "B" + AiStreamConstants.THINK_START + "r2" + AiStreamConstants.THINK_END + "C";
        ThinkSplitResult result = splitter.splitChunk(input);

        assertThat(result.visible()).isEqualTo("ABC");
        assertThat(result.reasoning()).isEqualTo("r1r2");
    }

    @Test
    @DisplayName("process()方法仅返回visible部分")
    void processMethod_returnsOnlyVisiblePart() {
        String input = "Hello" + AiStreamConstants.THINK_START + "hidden" + AiStreamConstants.THINK_END + "World";
        String visible = splitter.process(input);

        assertThat(visible).isEqualTo("HelloWorld");
    }
}
