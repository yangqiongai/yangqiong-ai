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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 解析器工厂
 * @author yangqiong
 */
@Service
public class ParserFactory {

    private static final Logger log = LoggerFactory.getLogger(ParserFactory.class);

    /**
     * 解析器优先级：纯文本优先，Docling次之，Tika兜底
     */
    private static final List<String> PARSER_PRIORITY = List.of("text", "docling", "tika");

    private final List<DocumentParser> parsers;

    @Autowired
    public ParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
    }

    /**
     * 根据策略名和文件类型获取解析器
     * @param strategyName
     * @param contentType
     * @param fileName
     * @return
     */
    public Optional<DocumentParser> getParser(String strategyName, String contentType, String fileName) {
        if (strategyName != null && !strategyName.isBlank()) {
            Optional<DocumentParser> byName = parsers.stream()
                    .filter(p -> p.getParserName().equalsIgnoreCase(strategyName))
                    .findFirst();
            if (byName.isPresent()) {
                return byName;
            }
            log.warn("未找到指定策略名的解析器: {}, 尝试按文件类型匹配", strategyName);
        }

        List<DocumentParser> matched = parsers.stream()
                .filter(p -> p.supports(contentType, fileName))
                .sorted(Comparator.comparingInt(p -> {
                    int index = PARSER_PRIORITY.indexOf(p.getParserName().toLowerCase());
                    return index >= 0 ? index : Integer.MAX_VALUE;
                }))
                .toList();
        return pickParser(matched, contentType, fileName);
    }

    /**
     * 从匹配解析器中选取最终解析器
     * @param matched
     * @param contentType
     * @param fileName
     * @return
     */
    private Optional<DocumentParser> pickParser(List<DocumentParser> matched, String contentType, String fileName) {
        // .docling.json为Docling结构化格式, 优先于纯文本交给Docling解析
        if (isDoclingStructuredFile(contentType, fileName)) {
            Optional<DocumentParser> docling = matched.stream()
                    .filter(p -> "docling".equalsIgnoreCase(p.getParserName()))
                    .findFirst();
            if (docling.isPresent()) {
                return docling;
            }
        }
        return matched.stream().findFirst();
    }

    /**
     * 判断是否为Docling结构化JSON文件
     * @param contentType
     * @param fileName
     * @return
     */
    private boolean isDoclingStructuredFile(String contentType, String fileName) {
        if (fileName == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        if (lowerName.endsWith(".docling.json")) {
            return true;
        }
        return "application/json".equalsIgnoreCase(contentType) && lowerName.contains("docling");
    }
}
