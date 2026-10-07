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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Docling载荷映射
 * @author yangqiong
 */
@Component
public class DoclingPayloadMapper {

    private static final Logger log = LoggerFactory.getLogger(DoclingPayloadMapper.class);

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 将Docling JSON字符串映射为文本段列表
     * @param jsonString
     * @return
     * @throws IOException
     */
    public List<SegmentRecord> mapToSegments(String jsonString) throws IOException {
        JsonNode root = objectMapper.readTree(jsonString);
        List<SegmentRecord> segments = new ArrayList<>();
        walkPayloadTree(root, segments);
        return segments;
    }

    /**
     * 提取父级文本段，基于md_content合并为父段
     * @param jsonString
     * @param baseMetadata
     * @return
     * @throws IOException
     */
    public List<SegmentRecord> mapParentChunks(String jsonString, Map<String, Object> baseMetadata) throws IOException {
        JsonNode root = objectMapper.readTree(jsonString);
        List<JsonNode> documentNodes = gatherDocumentNodes(root);
        List<SegmentRecord> segments = new ArrayList<>();

        for (JsonNode documentNode : documentNodes) {
            String markdown = resolveMarkdownBody(documentNode);
            if (markdown == null || markdown.isBlank()) {
                continue;
            }

            Map<String, Object> body = baseMetadata == null ? new HashMap<>() : new HashMap<>(baseMetadata);
            String title = resolveTitle(documentNode);
            if (title != null && !title.isBlank()) {
                body.put("parent_header", title);
                body.put("breadcrumbs", title);
                body.put("header_1", title);
                if (isSpreadsheetSource(baseMetadata)) {
                    body.put("sheet_name", title);
                }
                if (!hasHeadingPrefix(markdown, title)) {
                    markdown = "## " + title + "\n\n" + markdown.trim();
                }
            }

            segments.add(new SegmentRecord(markdown.trim(), body));
        }

        return segments;
    }

    /**
     * 遍历Docling载荷树结构
     * @param root
     * @param segments
     */
    private void walkPayloadTree(JsonNode root, List<SegmentRecord> segments) {
        if (root == null || root.isNull()) {
            return;
        }
        if (root.isArray()) {
            for (JsonNode item : root) {
                walkPayloadTree(item, segments);
            }
            return;
        }
        if (root.has("json_content")) {
            JsonNode jsonContentNode = root.get("json_content");
            if (jsonContentNode != null && !jsonContentNode.isNull()) {
                walkPayloadTree(jsonContentNode, segments);
                return;
            }
        }
        if (root.has("document")) {
            walkPayloadTree(root.get("document"), segments);
            return;
        }
        if (root.has("documents") && root.get("documents").isArray()) {
            for (JsonNode item : root.get("documents")) {
                walkPayloadTree(item, segments);
            }
            return;
        }

        List<String> breadcrumbs = new ArrayList<>();
        String rootTitle = resolveTitle(root);
        if (rootTitle != null && !rootTitle.isEmpty()) {
            breadcrumbs.add(rootTitle);
        }

        // docling v2导出格式: texts/tables/pictures为顶层扁平数组, body仅存$ref引用, 需直接从顶层提取
        if (root.has("body")) {
            processContentItems(root, "texts", breadcrumbs, segments);
            processContentItems(root, "tables", breadcrumbs, segments);
            processContentItems(root, "pictures", breadcrumbs, segments);
        }

        JsonNode startNode = root.has("body") ? root.get("body") : root;
        walkNodeTree(startNode, breadcrumbs, segments);
    }

    /**
     * 递归遍历节点树，提取文本段
     * @param node
     * @param breadcrumbs
     * @param segments
     */
    private void walkNodeTree(JsonNode node, List<String> breadcrumbs, List<SegmentRecord> segments) {
        if (node == null) {
            return;
        }

        String currentTitle = resolveTitle(node);
        List<String> nextBreadcrumbs = new ArrayList<>(breadcrumbs);
        if (currentTitle != null && !currentTitle.isBlank()) {
            if (breadcrumbs.isEmpty() || !breadcrumbs.get(breadcrumbs.size() - 1).equals(currentTitle)) {
                nextBreadcrumbs.add(currentTitle);
            }
        }

        processContentItems(node, "texts", nextBreadcrumbs, segments);
        processContentItems(node, "tables", nextBreadcrumbs, segments);
        processContentItems(node, "pictures", nextBreadcrumbs, segments);

        if (node.has("text") && !isContainerNode(node) && !isHeaderNode(node)) {
            String text = node.get("text").asText();
            if (text != null && !text.isBlank()) {
                buildSegment(text, breadcrumbs, segments, "text");
            }
        }

        if (node.has("children") && node.get("children").isArray()) {
            for (JsonNode child : node.get("children")) {
                walkNodeTree(child, nextBreadcrumbs, segments);
            }
        }

        if (node.has("groups") && node.get("groups").isArray()) {
            for (JsonNode group : node.get("groups")) {
                walkNodeTree(group, nextBreadcrumbs, segments);
            }
        }

        if (node.has("body") && node.get("body").isObject()) {
            walkNodeTree(node.get("body"), nextBreadcrumbs, segments);
        }
    }

    /**
     * 处理指定字段名的内容列表
     * @param node
     * @param fieldName
     * @param breadcrumbs
     * @param segments
     */
    private void processContentItems(JsonNode node, String fieldName, List<String> breadcrumbs, List<SegmentRecord> segments) {
        if (!node.has(fieldName) || !node.get(fieldName).isArray()) {
            return;
        }
        String contentType = toContentType(fieldName);
        for (JsonNode item : node.get(fieldName)) {
            // 过滤docling furniture层内容（页眉页脚等非正文噪音）
            if (item.isObject() && "furniture".equals(item.path("content_layer").asText())) {
                continue;
            }
            String text = resolveContentText(item, contentType);
            if (text != null && !text.isBlank()) {
                buildSegment(text, breadcrumbs, segments, contentType);
            }
        }
    }

    /**
     * 构建文本段并附加请求数据体
     * @param text
     * @param breadcrumbs
     * @param segments
     * @param contentType
     */
    private void buildSegment(String text, List<String> breadcrumbs, List<SegmentRecord> segments, String contentType) {
        Map<String, Object> body = new HashMap<>();
        if (!breadcrumbs.isEmpty()) {
            body.put("breadcrumbs", String.join(" > ", breadcrumbs));
            for (int i = 0; i < breadcrumbs.size(); i++) {
                body.put("header_" + (i + 1), breadcrumbs.get(i));
            }
            body.put("parent_header", breadcrumbs.get(breadcrumbs.size() - 1));
        }
        body.put("content_type", contentType);
        segments.add(new SegmentRecord(text, body));
    }

    /**
     * 收集所有文档节点
     * @param root
     * @return
     */
    private List<JsonNode> gatherDocumentNodes(JsonNode root) {
        List<JsonNode> documentNodes = new ArrayList<>();
        gatherDocumentNodesRecursive(root, documentNodes);
        return documentNodes;
    }

    /**
     * 递归收集文档节点
     * @param node
     * @param documentNodes
     */
    private void gatherDocumentNodesRecursive(JsonNode node, List<JsonNode> documentNodes) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                gatherDocumentNodesRecursive(item, documentNodes);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (node.has("document")) {
            gatherDocumentNodesRecursive(node.get("document"), documentNodes);
            return;
        }
        if (node.has("documents") && node.get("documents").isArray()) {
            for (JsonNode item : node.get("documents")) {
                gatherDocumentNodesRecursive(item, documentNodes);
            }
            return;
        }
        if (isDocumentLike(node)) {
            documentNodes.add(node);
        }
    }

    /**
     * 判断节点是否为文档类型节点
     * @param node
     * @return
     */
    private boolean isDocumentLike(JsonNode node) {
        return node.has("md_content")
                || node.has("json_content")
                || node.has("text_content")
                || node.has("body")
                || node.has("texts")
                || node.has("tables")
                || node.has("children")
                || node.has("groups");
    }

    /**
     * 解析文档节点的Markdown内容
     * @param documentNode
     * @return
     */
    private String resolveMarkdownBody(JsonNode documentNode) {
        String markdown = readTextValue(documentNode, "md_content");
        if (markdown != null) {
            return markdown;
        }

        JsonNode structuredNode = resolveStructuredNode(documentNode);
        if (structuredNode != documentNode) {
            markdown = readTextValue(structuredNode, "md_content");
            if (markdown != null) {
                return markdown;
            }
        }

        String textContent = readTextValue(documentNode, "text_content");
        if (textContent != null) {
            return textContent;
        }
        return readTextValue(structuredNode, "text_content");
    }

    /**
     * 解析结构化文档节点（从json_content中提取）
     * @param documentNode
     * @return
     */
    private JsonNode resolveStructuredNode(JsonNode documentNode) {
        if (documentNode == null || documentNode.isNull()) {
            return documentNode;
        }
        JsonNode jsonContentNode = documentNode.get("json_content");
        if (jsonContentNode != null && (jsonContentNode.isObject() || jsonContentNode.isArray())) {
            return jsonContentNode;
        }
        return documentNode;
    }

    /**
     * 解析节点标题
     * @param node
     * @return
     */
    private String resolveTitle(JsonNode node) {
        String[] directFields = {"sheet_name", "sheetName", "worksheet_name", "worksheetName", "title", "name"};
        for (String field : directFields) {
            String value = readTextValue(node, field);
            if (value != null) {
                return value;
            }
        }
        JsonNode originNode = node != null ? node.get("origin") : null;
        if (originNode != null && originNode.isObject()) {
            for (String field : directFields) {
                String value = readTextValue(originNode, field);
                if (value != null) {
                    return value;
                }
            }
        }
        if (isHeaderNode(node) && node != null && node.has("text")) {
            String text = node.get("text").asText();
            return text == null || text.isBlank() ? null : text;
        }
        return null;
    }

    /**
     * 读取节点中的文本字段值
     * @param node
     * @param fieldName
     * @return
     */
    private String readTextValue(JsonNode node, String fieldName) {
        if (node == null || fieldName == null || !node.has(fieldName) || !node.get(fieldName).isTextual()) {
            return null;
        }
        String value = node.get(fieldName).asText("").trim();
        return value.isEmpty() ? null : value;
    }

    /**
     * 判断节点是否为容器节点
     * @param node
     * @return
     */
    private boolean isContainerNode(JsonNode node) {
        return node.has("children") || node.has("groups") || node.has("body");
    }

    /**
     * 判断节点是否为标题节点
     * @param node
     * @return
     */
    private boolean isHeaderNode(JsonNode node) {
        if (node == null) {
            return false;
        }
        if (node.has("type")) {
            String type = node.get("type").asText("").toLowerCase();
            if (type.contains("header") || type.contains("title") || type.contains("heading")) {
                return true;
            }
        }
        if (node.has("label")) {
            String label = node.get("label").asText("").toLowerCase();
            return label.contains("header") || label.contains("title") || label.contains("heading");
        }
        return false;
    }

    /**
     * 解析内容节点的文本
     * @param node
     * @param contentType
     * @return
     */
    private String resolveContentText(JsonNode node, String contentType) {
        if ("table".equals(contentType)) {
            String tableText = resolveTableText(node);
            if (tableText != null && !tableText.isBlank()) {
                return tableText;
            }
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.has("text")) {
            return node.get("text").asText();
        }
        if (node.has("content")) {
            return node.get("content").asText();
        }
        if (node.has("data")) {
            return node.get("data").toString();
        }
        return null;
    }

    /**
     * 将字段名映射为内容类型
     * @param fieldName
     * @return
     */
    private String toContentType(String fieldName) {
        if ("tables".equals(fieldName)) {
            return "table";
        }
        if ("pictures".equals(fieldName)) {
            return "picture";
        }
        return "text";
    }

    /**
     * 解析表格节点的文本内容
     * @param node
     * @return
     */
    private String resolveTableText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.has("text") && node.get("text").isTextual()) {
            String text = node.get("text").asText();
            if (!text.isBlank()) {
                return text;
            }
        }
        if (node.has("content") && node.get("content").isTextual()) {
            String content = node.get("content").asText();
            if (!content.isBlank()) {
                return content;
            }
        }
        if (node.has("data")) {
            String dataText = resolveTableRows(node.get("data"));
            if (dataText != null && !dataText.isBlank()) {
                return dataText;
            }
        }
        return resolveTableRows(node);
    }

    /**
     * 解析表格行数据
     * @param node
     * @return
     */
    private String resolveTableRows(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isArray()) {
            List<String> rows = new ArrayList<>();
            for (JsonNode child : node) {
                String row = resolveRowText(child);
                if (row != null && !row.isBlank()) {
                    rows.add(row);
                }
            }
            return rows.isEmpty() ? null : String.join("\n", rows);
        }
        if (node.isObject()) {
            String[] candidates = {"rows", "table_rows", "grid", "body", "cells"};
            for (String candidate : candidates) {
                if (node.has(candidate)) {
                    String nested = resolveTableRows(node.get(candidate));
                    if (nested != null && !nested.isBlank()) {
                        return nested;
                    }
                }
            }
        }
        return flattenNodeText(node);
    }

    /**
     * 解析单行表格文本
     * @param rowNode
     * @return
     */
    private String resolveRowText(JsonNode rowNode) {
        if (rowNode == null || rowNode.isNull()) {
            return null;
        }
        if (rowNode.isTextual()) {
            return rowNode.asText();
        }
        if (rowNode.isArray()) {
            List<String> cells = new ArrayList<>();
            for (JsonNode cell : rowNode) {
                String text = flattenNodeText(cell);
                if (text != null && !text.isBlank()) {
                    cells.add(text.trim());
                }
            }
            return cells.isEmpty() ? null : "| " + String.join(" | ", cells) + " |";
        }
        if (rowNode.isObject() && rowNode.has("cells") && rowNode.get("cells").isArray()) {
            return resolveRowText(rowNode.get("cells"));
        }
        String flattened = flattenNodeText(rowNode);
        return flattened == null || flattened.isBlank() ? null : "| " + flattened.trim().replace("\n", " | ") + " |";
    }

    /**
     * 将JSON节点展平为文本
     * @param node
     * @return
     */
    private String flattenNodeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual() || node.isNumber() || node.isBoolean()) {
            return node.asText();
        }
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            for (JsonNode child : node) {
                String text = flattenNodeText(child);
                if (text != null && !text.isBlank()) {
                    values.add(text.trim());
                }
            }
        } else if (node.isObject()) {
            Iterator<String> fieldNames = node.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                if ("type".equals(fieldName) || "label".equals(fieldName)) {
                    continue;
                }
                String text = flattenNodeText(node.get(fieldName));
                if (text != null && !text.isBlank()) {
                    values.add(text.trim());
                }
            }
        }
        return values.isEmpty() ? null : String.join("\n", values);
    }

    /**
     * 判断请求数据体是否来自Excel文档
     * @param body
     * @return
     */
    private boolean isSpreadsheetSource(Map<String, Object> body) {
        if (body == null || !body.containsKey("source_filename")) {
            return false;
        }
        Object fileNameObj = body.get("source_filename");
        if (fileNameObj == null) {
            return false;
        }
        String fileName = fileNameObj.toString().toLowerCase();
        return fileName.endsWith(".xlsx") || fileName.endsWith(".xls");
    }

    /**
     * 判断Markdown文本是否以指定标题的标题格式开头
     * @param markdown
     * @param title
     * @return
     */
    private boolean hasHeadingPrefix(String markdown, String title) {
        if (markdown == null || markdown.isBlank() || title == null || title.isBlank()) {
            return false;
        }
        String[] lines = markdown.split("\\r?\\n", 2);
        if (lines.length == 0) {
            return false;
        }
        String firstLine = lines[0].trim();
        String normalizedTitle = title.trim();
        return firstLine.equals(normalizedTitle)
                || firstLine.equals("# " + normalizedTitle)
                || firstLine.equals("## " + normalizedTitle)
                || firstLine.equals("### " + normalizedTitle);
    }

    /**
     * 文本段记录，替代LangChain4j的TextSegment
     * @author yangqiong
     */
    public static class SegmentRecord {

        /**
         * 文本内容
         */
        private final String text;

        /**
         * 请求数据体
         */
        private final Map<String, Object> body;

        public SegmentRecord(String text, Map<String, Object> body) {
            this.text = text;
            this.body = body != null ? body : new HashMap<>();
        }

        public String text() {
            return text;
        }

        public Map<String, Object> body() {
            return body;
        }

        public String getText() {
            return text;
        }

        public Map<String, Object> getBody() {
            return body;
        }
    }
}
