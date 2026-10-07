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

import java.util.List;
import java.util.Map;

/**
 * 解析后的文档
 * @author yangqiong
 */
public class ParsedDocument {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 请求数据体
     */
    private Map<String, Object> body;

    /**
     * 文档章节列表
     */
    private List<DocumentSection> sections;

    public ParsedDocument() {
    }

    public ParsedDocument(String title, String content, Map<String, Object> body, List<DocumentSection> sections) {
        this.title = title;
        this.content = content;
        this.body = body;
        this.sections = sections;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Map<String, Object> getBody() {
        return body;
    }

    public void setMetadata(Map<String, Object> body) {
        this.body = body;
    }

    public List<DocumentSection> getSections() {
        return sections;
    }

    public void setSections(List<DocumentSection> sections) {
        this.sections = sections;
    }
}
