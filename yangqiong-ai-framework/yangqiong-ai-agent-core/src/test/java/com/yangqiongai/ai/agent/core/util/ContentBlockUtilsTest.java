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
package com.yangqiongai.ai.agent.core.util;

import com.yangqiongai.ai.agent.runtime.message.AgentImageBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContentBlock工具图片处理单元测试
 * @author yangqiong
 */
class ContentBlockUtilsTest {

    @Test
    @DisplayName("URL图片块转Markdown保留链接")
    void toImageMarkdownUrlMode() {
        AgentImageBlock img = AgentImageBlock.builder().url("https://example.com/a.png").build();
        assertThat(ContentBlockUtils.toImageMarkdown(img))
                .isEqualTo("\n\n![图片](https://example.com/a.png)\n\n");
    }

    @Test
    @DisplayName("Base64图片块转Markdown携带媒体类型")
    void toImageMarkdownBase64Mode() {
        AgentImageBlock img = AgentImageBlock.builder()
                .base64Data("abc123").mediaType("image/jpeg").build();
        assertThat(ContentBlockUtils.toImageMarkdown(img))
                .isEqualTo("\n\n![图片](data:image/jpeg;base64,abc123)\n\n");
    }

    @Test
    @DisplayName("Base64图片块媒体类型缺失时默认png")
    void toImageMarkdownBase64DefaultMediaType() {
        AgentImageBlock img = AgentImageBlock.builder().base64Data("abc").build();
        assertThat(ContentBlockUtils.toImageMarkdown(img))
                .isEqualTo("\n\n![图片](data:image/png;base64,abc)\n\n");
    }

    @Test
    @DisplayName("空图片块与null输入转Markdown返回空串")
    void toImageMarkdownEmpty() {
        assertThat(ContentBlockUtils.toImageMarkdown((AgentImageBlock) null)).isEmpty();
        assertThat(ContentBlockUtils.toImageMarkdown(AgentImageBlock.builder().build())).isEmpty();
        assertThat(ContentBlockUtils.toImageMarkdown((List<AgentContentBlock>) null)).isEmpty();
        assertThat(ContentBlockUtils.toImageMarkdown(List.of())).isEmpty();
    }

    @Test
    @DisplayName("内容块列表仅提取图片块转Markdown")
    void toImageMarkdownFromBlocks() {
        List<AgentContentBlock> blocks = List.of(
                AgentTextBlock.builder().text("回答文本").build(),
                AgentImageBlock.builder().url("https://example.com/1.png").build(),
                AgentImageBlock.builder().base64Data("xyz").mediaType("image/png").build());
        String markdown = ContentBlockUtils.toImageMarkdown(blocks);
        assertThat(markdown)
                .contains("![图片](https://example.com/1.png)")
                .contains("![图片](data:image/png;base64,xyz)")
                .doesNotContain("回答文本");
    }

    @Test
    @DisplayName("持久化净化将图片块转为Markdown文本并保留消息元信息")
    void sanitizeImagesForPersist() {
        AgentMessage message = AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(
                        AgentTextBlock.builder().text("看这张图").build(),
                        AgentImageBlock.builder().base64Data("bigdata").mediaType("image/png").build()))
                .build();
        AgentMessage sanitized = ContentBlockUtils.sanitizeImagesForPersist(message);
        assertThat(sanitized.getName()).isEqualTo("assistant");
        assertThat(sanitized.getRole()).isEqualTo(AgentMessageRole.ASSISTANT);
        assertThat(sanitized.getContent()).hasSize(2);
        assertThat(sanitized.getContent().get(1)).isInstanceOf(AgentTextBlock.class);
        assertThat(((AgentTextBlock) sanitized.getContent().get(1)).getText())
                .isEqualTo("\n\n![图片](data:image/png;base64,bigdata)\n\n");
    }

    @Test
    @DisplayName("持久化净化对URL图片保留链接文本")
    void sanitizeImagesForPersistUrl() {
        AgentMessage message = AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentImageBlock.builder().url("https://example.com/u.png").build()))
                .build();
        AgentMessage sanitized = ContentBlockUtils.sanitizeImagesForPersist(message);
        assertThat(sanitized.getContent()).hasSize(1);
        assertThat(((AgentTextBlock) sanitized.getContent().get(0)).getText())
                .isEqualTo("\n\n![图片](https://example.com/u.png)\n\n");
    }

    @Test
    @DisplayName("无图片消息持久化净化返回原对象")
    void sanitizeImagesForPersistNoImage() {
        AgentMessage message = AgentMessage.builder()
                .name("assistant")
                .role(AgentMessageRole.ASSISTANT)
                .content(List.of(AgentTextBlock.builder().text("纯文本").build()))
                .build();
        assertThat(ContentBlockUtils.sanitizeImagesForPersist(message)).isSameAs(message);
        assertThat(ContentBlockUtils.sanitizeImagesForPersist(null)).isNull();
    }

    @Test
    @DisplayName("base64图片Markdown文本替换为占位符")
    void replaceBase64ImageMarkdown() {
        String answer = "前文\n\n![图片](data:image/png;base64,AAAA)\n\n后文";
        assertThat(ContentBlockUtils.replaceBase64ImageMarkdown(answer))
                .isEqualTo("前文\n\n![图片]()\n\n后文");
    }

    @Test
    @DisplayName("URL图片Markdown原样保留")
    void replaceBase64ImageMarkdownKeepsUrl() {
        String answer = "![图片](https://example.com/a.png)";
        assertThat(ContentBlockUtils.replaceBase64ImageMarkdown(answer))
                .isEqualTo("![图片](https://example.com/a.png)");
    }

    @Test
    @DisplayName("空文本替换base64图片Markdown原样返回")
    void replaceBase64ImageMarkdownEmpty() {
        assertThat(ContentBlockUtils.replaceBase64ImageMarkdown(null)).isNull();
        assertThat(ContentBlockUtils.replaceBase64ImageMarkdown("")).isEmpty();
    }
}
