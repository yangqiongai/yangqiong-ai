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
package com.yangqiongai.ai.agent.local.workspace.docx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Word文档读回测试
 * @author yangqiong
 */
class DocxReaderTest {

    @TempDir
    Path tempDir;

    /**
     * 构建带标题/正文/表格的docx并落盘
     * @param extraParagraphs 额外段落数（用于截断测试）
     * @return
     * @throws Exception
     */
    private Path buildDoc(int extraParagraphs) throws Exception {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("读回.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        List<DocxDsl.Block> blocks = new ArrayList<>();
        DocxDsl.Block h1 = new DocxDsl.Block();
        h1.setType("heading");
        h1.setLevel(1);
        h1.setText("标题一");
        blocks.add(h1);
        DocxDsl.Block p = new DocxDsl.Block();
        p.setType("paragraph");
        p.setText("正文内容");
        blocks.add(p);
        DocxDsl.Block table = new DocxDsl.Block();
        table.setType("table");
        table.setHeader(List.of("事项", "负责人"));
        table.setRows(List.of(List.of("联调", "张三"), List.of("上线", "李四")));
        blocks.add(table);
        for (int i = 0; i < extraParagraphs; i++) {
            DocxDsl.Block extra = new DocxDsl.Block();
            extra.setType("paragraph");
            extra.setText("附加段" + i);
            blocks.add(extra);
        }
        document.setBlocks(blocks);
        dsl.setDocument(document);
        Path target = tempDir.resolve("读回.docx");
        DocxGenerator.generate(dsl, target, tempDir);
        return target;
    }

    @Test
    void 标题段落表格读回结构正确() throws Exception {
        Path target = buildDoc(0);
        String json = DocxReader.readAsJson(target);
        JsonNode root = new ObjectMapper().readTree(json);

        assertThat(root.get("paragraphs").size()).isEqualTo(2);
        JsonNode heading = root.get("paragraphs").get(0);
        assertThat(heading.get("type").asText()).isEqualTo("heading");
        assertThat(heading.get("text").asText()).isEqualTo("标题一");
        JsonNode para = root.get("paragraphs").get(1);
        assertThat(para.get("type").asText()).isEqualTo("paragraph");
        assertThat(para.get("text").asText()).isEqualTo("正文内容");

        assertThat(root.get("tables").size()).isEqualTo(1);
        JsonNode table = root.get("tables").get(0);
        assertThat(table.get("header").get(0).asText()).isEqualTo("事项");
        assertThat(table.get("header").get(1).asText()).isEqualTo("负责人");
        assertThat(table.get("rows").get(0).get(0).asText()).isEqualTo("联调");
        assertThat(table.get("rows").get(1).get(1).asText()).isEqualTo("李四");
        assertThat(table.get("truncated").asBoolean()).isFalse();
        assertThat(root.get("truncated").asBoolean()).isFalse();
    }

    @Test
    void 超三百段截断并标记() throws Exception {
        Path target = buildDoc(310);
        JsonNode root = new ObjectMapper().readTree(DocxReader.readAsJson(target));
        assertThat(root.get("paragraphs").size()).isEqualTo(300);
        assertThat(root.get("truncated").asBoolean()).isTrue();
        // 列表下标0为标题、1为正文，其余为附加段
        assertThat(root.get("paragraphs").get(299).get("text").asText()).isEqualTo("附加段297");
    }

    @Test
    void 中文内容读回编码正确() throws Exception {
        Path target = buildDoc(0);
        String json = DocxReader.readAsJson(target);
        assertThat(json).contains("标题一").contains("正文内容").contains("联调").contains("李四");
    }

    @Test
    void 段落文本超两千字符截断() throws Exception {
        DocxDsl dsl = new DocxDsl();
        dsl.setPath("长文本.docx");
        DocxDsl.DocumentDsl document = new DocxDsl.DocumentDsl();
        DocxDsl.Block p = new DocxDsl.Block();
        p.setType("paragraph");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            sb.append("a");
        }
        p.setText(sb.toString());
        document.setBlocks(List.of(p));
        dsl.setDocument(document);
        Path target = tempDir.resolve("长文本.docx");
        DocxGenerator.generate(dsl, target, tempDir);

        JsonNode root = new ObjectMapper().readTree(DocxReader.readAsJson(target));
        assertThat(root.get("paragraphs").get(0).get("text").asText().length()).isEqualTo(2000);
    }
}
