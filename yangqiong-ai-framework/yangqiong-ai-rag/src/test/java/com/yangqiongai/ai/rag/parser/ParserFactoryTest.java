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
package com.yangqiongai.ai.rag.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ParserFactory 单元测试
 *
 * @author test
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParserFactoryTest {

    private DocumentParser createMockParser(String name, boolean supports) {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.getParserName()).thenReturn(name);
        when(parser.supports(anyString(), anyString())).thenReturn(supports);
        return parser;
    }

    @Test
    @DisplayName("getParser - 策略名优先匹配")
    void getParser_matchesByStrategyNameFirst() {
        DocumentParser tikaParser = createMockParser("tika", false);
        DocumentParser doclingParser = createMockParser("docling", true);

        ParserFactory factory = new ParserFactory(List.of(tikaParser, doclingParser));

        Optional<DocumentParser> result = factory.getParser("docling", "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("docling");
    }

    @Test
    @DisplayName("getParser - 策略名不匹配时按contentType回退")
    void getParser_fallsBackToContentType() {
        DocumentParser tikaParser = mock(DocumentParser.class);
        when(tikaParser.getParserName()).thenReturn("tika");
        when(tikaParser.supports("application/pdf", "test.pdf")).thenReturn(true);

        DocumentParser doclingParser = mock(DocumentParser.class);
        when(doclingParser.getParserName()).thenReturn("docling");
        when(doclingParser.supports("application/pdf", "test.pdf")).thenReturn(false);

        ParserFactory factory = new ParserFactory(List.of(tikaParser, doclingParser));

        // 策略名指定了一个不存在的名称，回退到contentType匹配
        Optional<DocumentParser> result = factory.getParser("nonexistent", "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("tika");
    }

    @Test
    @DisplayName("getParser - 无匹配时返回empty")
    void getParser_returnsEmpty_whenNoMatch() {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.getParserName()).thenReturn("tika");
        when(parser.supports(anyString(), anyString())).thenReturn(false);

        ParserFactory factory = new ParserFactory(List.of(parser));

        Optional<DocumentParser> result = factory.getParser("nonexistent", "text/plain", "test.txt");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getParser - 策略名为null时按contentType匹配")
    void getParser_matchesByContentType_whenStrategyNull() {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.getParserName()).thenReturn("tika");
        when(parser.supports("application/pdf", "test.pdf")).thenReturn(true);

        ParserFactory factory = new ParserFactory(List.of(parser));

        Optional<DocumentParser> result = factory.getParser(null, "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("tika");
    }

    @Test
    @DisplayName("getParser - 策略名为空白时按contentType匹配")
    void getParser_matchesByContentType_whenStrategyBlank() {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.getParserName()).thenReturn("tika");
        when(parser.supports("application/pdf", "test.pdf")).thenReturn(true);

        ParserFactory factory = new ParserFactory(List.of(parser));

        Optional<DocumentParser> result = factory.getParser("  ", "application/pdf", "test.pdf");

        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("getParser - 空解析器列表返回empty")
    void getParser_returnsEmpty_whenNoParsers() {
        ParserFactory factory = new ParserFactory(Collections.emptyList());

        Optional<DocumentParser> result = factory.getParser("tika", "application/pdf", "test.pdf");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getParser - 策略名匹配忽略大小写")
    void getParser_matchesStrategyName_caseInsensitive() {
        DocumentParser parser = mock(DocumentParser.class);
        when(parser.getParserName()).thenReturn("Tika");

        ParserFactory factory = new ParserFactory(List.of(parser));

        Optional<DocumentParser> result = factory.getParser("tika", "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("Tika");
    }

    @Test
    @DisplayName("getParser - docling和tika都支持时优先docling")
    void getParser_prefersDoclingOverTika() {
        DocumentParser tikaParser = mock(DocumentParser.class);
        when(tikaParser.getParserName()).thenReturn("tika");
        when(tikaParser.supports("application/pdf", "test.pdf")).thenReturn(true);

        DocumentParser doclingParser = mock(DocumentParser.class);
        when(doclingParser.getParserName()).thenReturn("docling");
        when(doclingParser.supports("application/pdf", "test.pdf")).thenReturn(true);

        ParserFactory factory = new ParserFactory(List.of(tikaParser, doclingParser));

        Optional<DocumentParser> result = factory.getParser(null, "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("docling");
    }

    @Test
    @DisplayName("getParser - docling不支持时回退tika")
    void getParser_fallsBackToTika_whenDoclingNotSupport() {
        DocumentParser tikaParser = mock(DocumentParser.class);
        when(tikaParser.getParserName()).thenReturn("tika");
        when(tikaParser.supports("application/pdf", "test.pdf")).thenReturn(true);

        DocumentParser doclingParser = mock(DocumentParser.class);
        when(doclingParser.getParserName()).thenReturn("docling");
        when(doclingParser.supports("application/pdf", "test.pdf")).thenReturn(false);

        ParserFactory factory = new ParserFactory(List.of(doclingParser, tikaParser));

        Optional<DocumentParser> result = factory.getParser(null, "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("tika");
    }

    @Test
    @DisplayName("getParser - 未知解析器优先级排在已知解析器之后")
    void getParser_unknownParserLowerPriority() {
        DocumentParser tikaParser = mock(DocumentParser.class);
        when(tikaParser.getParserName()).thenReturn("tika");
        when(tikaParser.supports("application/pdf", "test.pdf")).thenReturn(true);

        DocumentParser customParser = mock(DocumentParser.class);
        when(customParser.getParserName()).thenReturn("custom");
        when(customParser.supports("application/pdf", "test.pdf")).thenReturn(true);

        ParserFactory factory = new ParserFactory(List.of(customParser, tikaParser));

        Optional<DocumentParser> result = factory.getParser(null, "application/pdf", "test.pdf");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("tika");
    }

    @Test
    @DisplayName("getParser - 文本解析器优先于docling和tika")
    void getParser_prefersTextOverDoclingAndTika() {
        DocumentParser textParser = mock(DocumentParser.class);
        when(textParser.getParserName()).thenReturn("text");
        when(textParser.supports("application/json", "data.json")).thenReturn(true);

        DocumentParser doclingParser = mock(DocumentParser.class);
        when(doclingParser.getParserName()).thenReturn("docling");
        when(doclingParser.supports("application/json", "data.json")).thenReturn(true);

        DocumentParser tikaParser = mock(DocumentParser.class);
        when(tikaParser.getParserName()).thenReturn("tika");
        when(tikaParser.supports("application/json", "data.json")).thenReturn(false);

        ParserFactory factory = new ParserFactory(List.of(tikaParser, doclingParser, textParser));

        Optional<DocumentParser> result = factory.getParser(null, "application/json", "data.json");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("text");
    }

    @Test
    @DisplayName("getParser - docling结构化json优先于纯文本解析器")
    void getParser_doclingJsonPrefersDocling() {
        DocumentParser textParser = mock(DocumentParser.class);
        when(textParser.getParserName()).thenReturn("text");
        when(textParser.supports(null, "report.docling.json")).thenReturn(true);

        DocumentParser doclingParser = mock(DocumentParser.class);
        when(doclingParser.getParserName()).thenReturn("docling");
        when(doclingParser.supports(null, "report.docling.json")).thenReturn(true);

        ParserFactory factory = new ParserFactory(List.of(doclingParser, textParser));

        Optional<DocumentParser> result = factory.getParser(null, null, "report.docling.json");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("docling");
    }

    @Test
    @DisplayName("getParser - docling结构化json但docling缺失时回退纯文本")
    void getParser_doclingJsonFallsBackToText_whenDoclingAbsent() {
        DocumentParser textParser = mock(DocumentParser.class);
        when(textParser.getParserName()).thenReturn("text");
        when(textParser.supports(null, "report.docling.json")).thenReturn(true);

        ParserFactory factory = new ParserFactory(List.of(textParser));

        Optional<DocumentParser> result = factory.getParser(null, null, "report.docling.json");

        assertThat(result).isPresent();
        assertThat(result.get().getParserName()).isEqualTo("text");
    }
}
