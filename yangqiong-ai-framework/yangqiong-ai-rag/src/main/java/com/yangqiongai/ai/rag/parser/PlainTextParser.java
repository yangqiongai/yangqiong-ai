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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 纯文本解析器
 * @author yangqiong
 */
@Service
public class PlainTextParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PlainTextParser.class);

    private static final String PARSER_NAME = "text";

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            "txt", "md", "csv", "json", "xml", "html", "log"
    );

    private static final Set<String> SUPPORTED_CONTENT_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json",
            "application/xml",
            "text/html"
    );

    @Override
    public String getParserName() {
        return PARSER_NAME;
    }

    @Override
    public boolean supports(String contentType, String fileName) {
        if (contentType != null && SUPPORTED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            return true;
        }
        if (fileName != null) {
            String extension = getFileExtension(fileName).toLowerCase();
            return SUPPORTED_EXTENSIONS.contains(extension);
        }
        return false;
    }

    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName, String fileUrl) {
        log.info("纯文本解析器处理文件: {}, fileUrl: {}", fileName, fileUrl);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String content = reader.lines().collect(Collectors.joining("\n"));

            ParsedDocument doc = new ParsedDocument();
            doc.setTitle(fileName);
            doc.setContent(content);
            doc.setMetadata(new HashMap<>());
            doc.setSections(new ArrayList<>());
            return doc;
        } catch (Exception e) {
            log.error("纯文本解析失败: {}", fileName, e);
            ParsedDocument doc = new ParsedDocument();
            doc.setTitle(fileName);
            doc.setContent("");
            doc.setMetadata(new HashMap<>());
            doc.setSections(new ArrayList<>());
            return doc;
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }
}
