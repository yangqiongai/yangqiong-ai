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
package com.yangqiongai.ai.agent.core.model.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentImageBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;

/**
 * ContentBlockConverter单元测试
 * @author yangqiong
 */
class ContentBlockConverterTest {

    /**
     * 文本块转换
     */
    @Nested
    @DisplayName("文本块转换")
    class TextBlockConversion {

        @Test
        @DisplayName("TextInputBlock → AgentTextBlock")
        void fromInputBlock_textInput() {
            TextInputBlock block = new TextInputBlock("hello");
            AgentContentBlock result = ContentBlockConverter.fromInputBlock(block);
            assertThat(result).isInstanceOf(AgentTextBlock.class);
            assertThat(((AgentTextBlock) result).getText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("AgentTextBlock → TextInputBlock")
        void toInputBlock_textBlock() {
            AgentTextBlock block = AgentTextBlock.builder().text("world").build();
            InputBlock result = ContentBlockConverter.toInputBlock(block);
            assertThat(result).isInstanceOf(TextInputBlock.class);
            assertThat(((TextInputBlock) result).getText()).isEqualTo("world");
        }

        @Test
        @DisplayName("AgentTextBlock → TextOutputBlock")
        void toOutputBlock_textBlock() {
            AgentTextBlock block = AgentTextBlock.builder().text("output").build();
            OutputBlock result = ContentBlockConverter.toOutputBlock(block);
            assertThat(result).isInstanceOf(TextOutputBlock.class);
            assertThat(((TextOutputBlock) result).getText()).isEqualTo("output");
        }
    }

    /**
     * 图像块转换
     */
    @Nested
    @DisplayName("图像块转换")
    class ImageBlockConversion {

        @Test
        @DisplayName("ImageInputBlock with UrlInputSource → AgentImageBlock(URL模式)")
        void fromInputBlock_imageUrlSource() {
            UrlInputSource source = new UrlInputSource("https://example.com/img.png");
            ImageInputBlock block = new ImageInputBlock(source, null, null);
            AgentContentBlock result = ContentBlockConverter.fromInputBlock(block);
            assertThat(result).isInstanceOf(AgentImageBlock.class);
            AgentImageBlock imageBlock = (AgentImageBlock) result;
            assertThat(imageBlock.isUrlMode()).isTrue();
            assertThat(imageBlock.getUrl()).isEqualTo("https://example.com/img.png");
            assertThat(imageBlock.getBase64Data()).isNull();
        }

        @Test
        @DisplayName("ImageInputBlock with Base64InputSource → AgentImageBlock(Base64模式)")
        void fromInputBlock_imageBase64Source() {
            Base64InputSource source = new Base64InputSource("image/png", "iVBORw0KGgo=");
            ImageInputBlock block = new ImageInputBlock(source, null, null);
            AgentContentBlock result = ContentBlockConverter.fromInputBlock(block);
            assertThat(result).isInstanceOf(AgentImageBlock.class);
            AgentImageBlock imageBlock = (AgentImageBlock) result;
            assertThat(imageBlock.isBase64Mode()).isTrue();
            assertThat(imageBlock.getBase64Data()).isEqualTo("iVBORw0KGgo=");
            assertThat(imageBlock.getMediaType()).isEqualTo("image/png");
            assertThat(imageBlock.getUrl()).isNull();
        }

        @Test
        @DisplayName("ImageInputBlock with null source → 空AgentImageBlock")
        void fromInputBlock_imageNullSource() {
            ImageInputBlock block = new ImageInputBlock(null, null, null);
            AgentContentBlock result = ContentBlockConverter.fromInputBlock(block);
            assertThat(result).isInstanceOf(AgentImageBlock.class);
            AgentImageBlock imageBlock = (AgentImageBlock) result;
            assertThat(imageBlock.isUrlMode()).isFalse();
            assertThat(imageBlock.isBase64Mode()).isFalse();
        }

        @Test
        @DisplayName("AgentImageBlock(URL模式) → ImageInputBlock")
        void toInputBlock_imageUrlMode() {
            AgentImageBlock block = AgentImageBlock.builder().url("https://example.com/img.jpg").build();
            InputBlock result = ContentBlockConverter.toInputBlock(block);
            assertThat(result).isInstanceOf(ImageInputBlock.class);
            ImageInputBlock imageBlock = (ImageInputBlock) result;
            assertThat(imageBlock.getSource()).isInstanceOf(UrlInputSource.class);
            assertThat(((UrlInputSource) imageBlock.getSource()).getUrl()).isEqualTo("https://example.com/img.jpg");
        }

        @Test
        @DisplayName("AgentImageBlock(Base64模式) → ImageInputBlock")
        void toInputBlock_imageBase64Mode() {
            AgentImageBlock block = AgentImageBlock.builder()
                    .base64Data("iVBORw0KGgo=")
                    .mediaType("image/png")
                    .build();
            InputBlock result = ContentBlockConverter.toInputBlock(block);
            assertThat(result).isInstanceOf(ImageInputBlock.class);
            ImageInputBlock imageBlock = (ImageInputBlock) result;
            assertThat(imageBlock.getSource()).isInstanceOf(Base64InputSource.class);
            Base64InputSource source = (Base64InputSource) imageBlock.getSource();
            assertThat(source.getData()).isEqualTo("iVBORw0KGgo=");
            assertThat(source.getMediaType()).isEqualTo("image/png");
        }

        @Test
        @DisplayName("AgentImageBlock → toOutputBlock 返回null(输出侧不支持图像)")
        void toOutputBlock_imageBlock_returnsNull() {
            AgentImageBlock block = AgentImageBlock.builder().url("https://example.com/img.png").build();
            OutputBlock result = ContentBlockConverter.toOutputBlock(block);
            assertThat(result).isNull();
        }
    }

    /**
     * 批量转换
     */
    @Nested
    @DisplayName("批量转换")
    class BatchConversion {

        @Test
        @DisplayName("fromInputBlocks 混合文本和图像")
        void fromInputBlocks_mixed() {
            TextInputBlock text = new TextInputBlock("describe this");
            UrlInputSource source = new UrlInputSource("https://example.com/img.png");
            ImageInputBlock image = new ImageInputBlock(source, null, null);
            List<AgentContentBlock> result = ContentBlockConverter.fromInputBlocks(List.of(text, image));
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(AgentTextBlock.class);
            assertThat(result.get(1)).isInstanceOf(AgentImageBlock.class);
            assertThat(((AgentImageBlock) result.get(1)).getUrl()).isEqualTo("https://example.com/img.png");
        }

        @Test
        @DisplayName("toInputBlocks 过滤null(如图像之外的内部块)")
        void toInputBlocks_filtersNull() {
            AgentTextBlock text = AgentTextBlock.builder().text("hello").build();
            AgentImageBlock image = AgentImageBlock.builder().url("https://example.com/img.png").build();
            List<InputBlock> result = ContentBlockConverter.toInputBlocks(List.of(text, image));
            assertThat(result).hasSize(2);
            assertThat(result.get(0)).isInstanceOf(TextInputBlock.class);
            assertThat(result.get(1)).isInstanceOf(ImageInputBlock.class);
        }

        @Test
        @DisplayName("toOutputBlocks 过滤图像块(输出侧不支持)")
        void toOutputBlocks_filtersImage() {
            AgentTextBlock text = AgentTextBlock.builder().text("hello").build();
            AgentImageBlock image = AgentImageBlock.builder().url("https://example.com/img.png").build();
            List<OutputBlock> result = ContentBlockConverter.toOutputBlocks(List.of(text, image));
            assertThat(result).hasSize(1);
            assertThat(result.get(0)).isInstanceOf(TextOutputBlock.class);
        }
    }
}
