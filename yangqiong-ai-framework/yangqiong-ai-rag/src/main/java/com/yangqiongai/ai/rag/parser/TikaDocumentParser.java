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

import com.yangqiongai.ai.rag.config.RagProperties;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Tika文档解析器
 * @author yangqiong
 */
@Service
public class TikaDocumentParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(TikaDocumentParser.class);

    private static final String PARSER_NAME = "tika";

    @Autowired
    private RagProperties ragProperties;

    private static final java.util.Set<String> SUPPORTED_EXTENSIONS = java.util.Set.of(
            "pdf", "docx", "doc", "pptx", "ppt", "xlsx", "xls", "rtf", "odt"
    );

    private static final java.util.Set<String> SUPPORTED_CONTENT_TYPES = java.util.Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-excel",
            "application/rtf",
            "application/vnd.oasis.opendocument.text"
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

    /**
     * 解析文档
     * @param inputStream
     * @param fileName
     * @param fileUrl
     * @return
     */
    @Override
    public ParsedDocument parse(InputStream inputStream, String fileName, String fileUrl) {
        log.info("Tika解析器处理文件: {}, fileUrl: {}", fileName, fileUrl);

        BodyContentHandler handler = new BodyContentHandler(ragProperties.getParser().getTikaMaxContentLength());
        Metadata body = new Metadata();
        if (fileName != null) {
            body.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileName);
        }
        Parser parser = new AutoDetectParser();
        ParseContext context = new ParseContext();
        context.set(Parser.class, parser);

        try {
            parser.parse(inputStream, handler, body, context);
        } catch (Exception e) {
            log.error("Tika解析失败: fileName={}", fileName, e);
            ParsedDocument doc = new ParsedDocument();
            doc.setTitle(fileName);
            doc.setContent("");
            doc.setMetadata(new HashMap<>());
            doc.setSections(new ArrayList<>());
            return doc;
        }

        String content = handler.toString();
        Map<String, Object> metaMap = extractMetadata(body);

        ParsedDocument doc = new ParsedDocument();
        doc.setTitle(resolveTitle(body, fileName));
        doc.setContent(content != null ? content.trim() : "");
        doc.setMetadata(metaMap);
        doc.setSections(new ArrayList<>());
        return doc;
    }

    /**
     * 提取Tika元数据
     * @param metadata
     * @return
     */
    private Map<String, Object> extractMetadata(Metadata metadata) {
        Map<String, Object> metaMap = new HashMap<>();
        metaMap.put("source", "tika");
        for (String name : metadata.names()) {
            metaMap.put(name, metadata.get(name));
        }
        return metaMap;
    }

    /**
     * 解析文档标题
     * @param body
     * @param fileName
     * @return
     */
    private String resolveTitle(Metadata body, String fileName) {
        String title = body.get(TikaCoreProperties.TITLE);
        if (title != null && !title.isBlank()) {
            return title;
        }
        return fileName;
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dotIndex + 1);
    }
}
