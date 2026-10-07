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
package com.yangqiongai.ai.rag.websearch;

/**
 * Web搜索结果
 * @author yangqiong
 */
public class WebSearchResult {

    /**
     * 标题
     */
    private String title;

    /**
     * URL
     */
    private String url;

    /**
     * 摘要
     */
    private String snippet;

    /**
     * 全文内容（可选，由fetchContent填充）
     */
    private String content;

    /**
     * 相关度分数 0.0-1.0
     */
    private double score;

    public WebSearchResult() {
    }

    public WebSearchResult(String title, String url, String snippet, double score) {
        this.title = title;
        this.url = url;
        this.snippet = snippet;
        this.score = score;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public double getScore() {
        return score;
    }

    public void setScore(double score) {
        this.score = score;
    }
}
