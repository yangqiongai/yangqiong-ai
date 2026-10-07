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
package com.yangqiongai.ai.agent.runtime.message;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Agent图像块单元测试
 * @author yangqiong
 */
class AgentImageBlockTest {

    @Nested
    @DisplayName("URL模式")
    class UrlModeTest {

        @Test
        @DisplayName("构建URL模式图像块")
        void build_urlMode_shouldSetUrl() {
            AgentImageBlock block = AgentImageBlock.builder()
                    .url("https://example.com/image.png")
                    .build();
            assertThat(block.getUrl()).isEqualTo("https://example.com/image.png");
            assertThat(block.getBase64Data()).isNull();
            assertThat(block.isUrlMode()).isTrue();
            assertThat(block.isBase64Mode()).isFalse();
        }

        @Test
        @DisplayName("URL模式toString包含url")
        void toString_urlMode_shouldContainUrl() {
            AgentImageBlock block = AgentImageBlock.builder()
                    .url("https://example.com/image.png")
                    .build();
            assertThat(block.toString()).contains("url=").contains("https://example.com/image.png");
        }
    }

    @Nested
    @DisplayName("Base64模式")
    class Base64ModeTest {

        @Test
        @DisplayName("构建Base64模式图像块")
        void build_base64Mode_shouldSetDataAndMediaType() {
            AgentImageBlock block = AgentImageBlock.builder()
                    .base64Data("iVBORw0KGgo=")
                    .mediaType("image/png")
                    .build();
            assertThat(block.getBase64Data()).isEqualTo("iVBORw0KGgo=");
            assertThat(block.getMediaType()).isEqualTo("image/png");
            assertThat(block.getUrl()).isNull();
            assertThat(block.isBase64Mode()).isTrue();
            assertThat(block.isUrlMode()).isFalse();
        }

        @Test
        @DisplayName("Base64模式toString包含dataLength和mediaType")
        void toString_base64Mode_shouldContainDataLengthAndMediaType() {
            AgentImageBlock block = AgentImageBlock.builder()
                    .base64Data("iVBORw0KGgo=")
                    .mediaType("image/png")
                    .build();
            assertThat(block.toString()).contains("base64DataLength=").contains("mediaType=").contains("image/png");
        }
    }

    @Nested
    @DisplayName("equals与hashCode")
    class EqualsHashcodeTest {

        @Test
        @DisplayName("相同URL的图像块相等")
        void equals_sameUrl_shouldBeEqual() {
            AgentImageBlock b1 = AgentImageBlock.builder().url("https://example.com/a.png").build();
            AgentImageBlock b2 = AgentImageBlock.builder().url("https://example.com/a.png").build();
            assertThat(b1).isEqualTo(b2);
            assertThat(b1.hashCode()).isEqualTo(b2.hashCode());
        }

        @Test
        @DisplayName("相同Base64数据的图像块相等")
        void equals_sameBase64_shouldBeEqual() {
            AgentImageBlock b1 = AgentImageBlock.builder()
                    .base64Data("data").mediaType("image/png").build();
            AgentImageBlock b2 = AgentImageBlock.builder()
                    .base64Data("data").mediaType("image/png").build();
            assertThat(b1).isEqualTo(b2);
            assertThat(b1.hashCode()).isEqualTo(b2.hashCode());
        }

        @Test
        @DisplayName("不同URL的图像块不相等")
        void equals_differentUrl_shouldNotBeEqual() {
            AgentImageBlock b1 = AgentImageBlock.builder().url("https://a.com/1.png").build();
            AgentImageBlock b2 = AgentImageBlock.builder().url("https://b.com/2.png").build();
            assertThat(b1).isNotEqualTo(b2);
        }

        @Test
        @DisplayName("不同mediaType的图像块不相等")
        void equals_differentMediaType_shouldNotBeEqual() {
            AgentImageBlock b1 = AgentImageBlock.builder()
                    .base64Data("data").mediaType("image/png").build();
            AgentImageBlock b2 = AgentImageBlock.builder()
                    .base64Data("data").mediaType("image/jpeg").build();
            assertThat(b1).isNotEqualTo(b2);
        }
    }
}
