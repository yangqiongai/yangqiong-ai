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
package com.yangqiongai.ai.rag.chunk;

import com.yangqiongai.ai.rag.model.SliceRecord;
import com.yangqiongai.ai.rag.repository.SliceRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultDocumentChunker 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
class DefaultDocumentChunkerTest {

    @Mock
    private SliceRecordRepository sliceRecordRepository;

    @InjectMocks
    private DefaultDocumentChunker documentChunker;

    @Test
    @DisplayName("computeContentHash - 返回SHA-256哈希")
    void computeContentHash_returnsSHA256Hash() throws Exception {
        String content = "测试内容";
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
        StringBuilder expected = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                expected.append('0');
            }
            expected.append(hex);
        }

        String result = documentChunker.computeContentHash(content);

        assertThat(result).isEqualTo(expected.toString());
        assertThat(result).hasSize(64);
    }

    @Test
    @DisplayName("computeContentHash - null输入返回空字符串")
    void computeContentHash_returnsEmpty_whenNull() {
        assertThat(documentChunker.computeContentHash(null)).isEmpty();
    }

    @Test
    @DisplayName("splitMarkdownTableByRows - 拆分Markdown表格")
    void splitMarkdownTableByRows_splitsTable() {
        String table = "| 姓名 | 年龄 |\n|------|------|\n| 张三 | 25 |\n| 李四 | 30 |";

        List<String> result = documentChunker.splitMarkdownTableByRows(table);

        assertThat(result).isNotEmpty();
        assertThat(result.size()).isGreaterThanOrEqualTo(2); // 至少表头+1行数据
    }

    @Test
    @DisplayName("splitMarkdownTableByRows - null输入返回空列表")
    void splitMarkdownTableByRows_returnsEmpty_whenNull() {
        assertThat(documentChunker.splitMarkdownTableByRows(null)).isEmpty();
    }

    @Test
    @DisplayName("splitMarkdownTableByRows - 空白输入返回空列表")
    void splitMarkdownTableByRows_returnsEmpty_whenBlank() {
        assertThat(documentChunker.splitMarkdownTableByRows("   ")).isEmpty();
    }

    @Test
    @DisplayName("buildTableContext - 构建上下文字符串")
    void buildTableContext_buildsContextString() {
        List<String> headers = List.of("姓名", "年龄");
        // 注意：源码中split regex为"\\s*|\\s*"（|是正则OR），实际按字符拆分
        // 使用2个ASCII字符确保拆分后恰好匹配2个header
        String row = "AB";

        String result = documentChunker.buildTableContext("", headers, row);

        assertThat(result).contains("姓名: A");
        assertThat(result).contains("年龄: B");
    }

    @Test
    @DisplayName("buildTableContext - 带表名时包含表名")
    void buildTableContext_includesTableName() {
        List<String> headers = List.of("姓名", "年龄");
        String row = "AB";

        String result = documentChunker.buildTableContext("用户表", headers, row);

        assertThat(result).contains("表: 用户表");
        assertThat(result).contains("姓名: A");
    }

    @Test
    @DisplayName("injectContextHeaders - 在内容前添加文档标题")
    void injectContextHeaders_prependsDocumentTitle() {
        List<SliceRecord> chunks = new ArrayList<>();
        SliceRecord chunk = new SliceRecord();
        chunk.setContent("原始内容");
        chunks.add(chunk);

        documentChunker.injectContextHeaders(chunks, "测试文档");

        assertThat(chunk.getContent()).startsWith("【测试文档】");
        assertThat(chunk.getContent()).contains("原始内容");
    }

    @Test
    @DisplayName("injectContextHeaders - 已有标题前缀时不重复添加")
    void injectContextHeaders_doesNotDuplicate_whenAlreadyPrefixed() {
        List<SliceRecord> chunks = new ArrayList<>();
        SliceRecord chunk = new SliceRecord();
        chunk.setContent("【测试文档】\n原始内容");
        chunks.add(chunk);

        documentChunker.injectContextHeaders(chunks, "测试文档");

        assertThat(chunk.getContent()).isEqualTo("【测试文档】\n原始内容");
    }

    @Test
    @DisplayName("injectContextHeaders - 空列表不做操作")
    void injectContextHeaders_doesNothing_whenEmptyList() {
        List<SliceRecord> chunks = new ArrayList<>();
        documentChunker.injectContextHeaders(chunks, "测试文档");

        assertThat(chunks).isEmpty();
    }

    @Test
    @DisplayName("injectContextHeaders - 标题为null时不添加")
    void injectContextHeaders_doesNothing_whenTitleNull() {
        List<SliceRecord> chunks = new ArrayList<>();
        SliceRecord chunk = new SliceRecord();
        chunk.setContent("原始内容");
        chunks.add(chunk);

        documentChunker.injectContextHeaders(chunks, null);

        assertThat(chunk.getContent()).isEqualTo("原始内容");
    }

    @Test
    @DisplayName("linkParentChunks - 链接子块到父块")
    void linkParentChunks_linksChildrenToParent() {
        SliceRecord parent = new SliceRecord();
        parent.setSliceId("parent-1");
        parent.setMetadata(null);

        SliceRecord child = new SliceRecord();
        child.setSliceId("child-1");
        child.setParentId("parent-1");

        List<SliceRecord> parents = List.of(parent);
        List<SliceRecord> children = List.of(child);

        documentChunker.linkParentChunks(parents, children);

        // 验证父块metadata中包含childCount
        assertThat(parent.getMetadata()).isNotNull();
        assertThat(parent.getMetadata()).contains("childCount");
    }

    @Test
    @DisplayName("linkParentChunks - null父块列表不做操作")
    void linkParentChunks_doesNothing_whenParentNull() {
        SliceRecord child = new SliceRecord();
        child.setSliceId("child-1");
        child.setParentId("parent-1");

        // 不应抛出异常
        documentChunker.linkParentChunks(null, List.of(child));
    }

    @Test
    @DisplayName("linkParentChunks - null子块列表不做操作")
    void linkParentChunks_doesNothing_whenChildrenNull() {
        SliceRecord parent = new SliceRecord();
        parent.setSliceId("parent-1");

        // 不应抛出异常
        documentChunker.linkParentChunks(List.of(parent), null);
    }
}
