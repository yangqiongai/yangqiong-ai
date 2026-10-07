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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Docling载荷映射测试
 * @author yangqiong
 */
class DoclingPayloadMapperTest {

    private final DoclingPayloadMapper mapper = new DoclingPayloadMapper();

    /**
     * 构建docling v2导出格式JSON（body为$ref引用树, texts为顶层扁平数组）
     * @param textsJson
     * @return
     */
    private String v2Document(String textsJson) {
        return "{\"schema_name\": \"DoclingDocument\", \"version\": \"1.0.0\","
                + "\"pages\": {\"1\": {\"size\": {\"width\": 612.0, \"height\": 792.0}}},"
                + "\"body\": {\"self_ref\": \"#/body\", \"children\": [{\"$ref\": \"#/texts/0\"}, {\"$ref\": \"#/texts/1\"}], \"label\": \"body\"},"
                + "\"texts\": " + textsJson + "}";
    }

    @Test
    @DisplayName("mapToSegments - docling v2格式从顶层texts扁平数组提取文本段")
    void mapToSegments_extractsFromV2FlatTexts() throws Exception {
        String json = v2Document("["
                + "{\"self_ref\": \"#/texts/0\", \"content_layer\": \"body\", \"label\": \"text\", \"text\": \"资源管理模式概述\"},"
                + "{\"self_ref\": \"#/texts/1\", \"content_layer\": \"body\", \"label\": \"text\", \"text\": \"Eager Acquisition模式\"}"
                + "]");

        List<DoclingPayloadMapper.SegmentRecord> segments = mapper.mapToSegments(json);

        assertThat(segments).hasSize(2);
        assertThat(segments.get(0).text()).isEqualTo("资源管理模式概述");
        assertThat(segments.get(1).text()).isEqualTo("Eager Acquisition模式");
    }

    @Test
    @DisplayName("mapToSegments - 过滤furniture层的页眉页脚内容")
    void mapToSegments_filtersFurnitureLayer() throws Exception {
        String json = v2Document("["
                + "{\"self_ref\": \"#/texts/0\", \"content_layer\": \"furniture\", \"label\": \"page_header\", \"text\": \"TURING图灵程序设计丛书\"},"
                + "{\"self_ref\": \"#/texts/1\", \"content_layer\": \"body\", \"label\": \"text\", \"text\": \"正文内容段落\"}"
                + "]");

        List<DoclingPayloadMapper.SegmentRecord> segments = mapper.mapToSegments(json);

        assertThat(segments).hasSize(1);
        assertThat(segments.get(0).text()).isEqualTo("正文内容段落");
    }

    @Test
    @DisplayName("mapToSegments - v2格式body引用树不产生重复段落")
    void mapToSegments_noDuplicateFromRefBody() throws Exception {
        String json = v2Document("["
                + "{\"self_ref\": \"#/texts/0\", \"content_layer\": \"body\", \"label\": \"text\", \"text\": \"唯一段落\"}"
                + "]");

        List<DoclingPayloadMapper.SegmentRecord> segments = mapper.mapToSegments(json);

        assertThat(segments).hasSize(1);
    }
}
