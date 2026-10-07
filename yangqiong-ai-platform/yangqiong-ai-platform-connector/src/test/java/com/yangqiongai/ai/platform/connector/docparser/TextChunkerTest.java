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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("文本分块单元测试")
class TextChunkerTest {

    @Test
    @DisplayName("空文本返回空清单")
    void shouldReturnEmptyForBlankText() {
        assertThat(TextChunker.chunk(null, 100, 10)).isEmpty();
        assertThat(TextChunker.chunk("   ", 100, 10)).isEmpty();
    }

    @Test
    @DisplayName("短文本单块输出且去除首尾空白")
    void shouldChunkShortText() {
        List<String> chunks = TextChunker.chunk("  你好世界  ", 100, 10);

        assertThat(chunks).containsExactly("你好世界");
    }

    @Test
    @DisplayName("按行边界聚合不超过尺寸")
    void shouldRespectLineBoundary() {
        List<String> chunks = TextChunker.chunk("a\nbb\nccc", 3, 0);

        assertThat(chunks).containsExactly("a", "bb", "ccc");
    }

    @Test
    @DisplayName("无行边界时按字符硬切")
    void shouldHardSplitWhenNoBreak() {
        List<String> chunks = TextChunker.chunk("abcdefgh", 3, 0);

        assertThat(chunks).containsExactly("abc", "def", "gh");
    }

    @Test
    @DisplayName("相邻分块按重叠字符衔接")
    void shouldOverlapChunks() {
        List<String> chunks = TextChunker.chunk("0123456789", 4, 2);

        assertThat(chunks).containsExactly("0123", "2345", "4567", "6789");
        assertThat(chunks.get(1).charAt(0)).isEqualTo(chunks.get(0).charAt(2));
    }

    @Test
    @DisplayName("重叠起点跳过半行从下一完整行开始")
    void shouldStartOverlapAtLineBreak() {
        List<String> chunks = TextChunker.chunk("aaaa\nbbbb\ncccc\ndddd", 10, 5);

        assertThat(chunks).hasSize(2);
        assertThat(chunks.get(1)).startsWith("cccc");
    }

    @Test
    @DisplayName("纯空白切片不产出空分块")
    void shouldSkipBlankSlice() {
        List<String> chunks = TextChunker.chunk("a\n\n\n\n\n\n\nb", 3, 0);

        assertThat(chunks).containsExactly("a", "b");
    }

    @Test
    @DisplayName("非法参数被收敛为有效值")
    void shouldNormalizeInvalidParams() {
        List<String> chunks = TextChunker.chunk("abc", 0, -5);

        assertThat(chunks).containsExactly("abc");
    }
}
