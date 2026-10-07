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
package com.yangqiongai.ai.agent.local.workspace.docx;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * WordDSL解析校验
 * @author yangqiong
 */
public class DocxValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 合法内容块类型
     */
    private static final Set<String> BLOCK_TYPES = Set.of("heading", "paragraph", "table", "image");

    /**
     * 合法编辑段落类型
     */
    private static final Set<String> PARA_TYPES = Set.of("paragraph", "heading");

    /**
     * 合法对齐方式
     */
    private static final Set<String> ALIGNS = Set.of("left", "center", "right");

    /**
     * 内容块数量上限
     */
    private static final int MAX_BLOCKS = 500;

    /**
     * 表格列数上限
     */
    private static final int MAX_COLUMNS = 50;

    /**
     * 表格行数上限
     */
    private static final int MAX_TABLE_ROWS = 10000;

    /**
     * 编辑操作条数上限
     */
    private static final int MAX_OPERATIONS = 200;

    private DocxValidator() {
    }

    /**
     * 解析创建DSL的JSON文本
     * @param json
     * @return
     */
    public static DocxDsl parseCreate(String json) {
        try {
            return MAPPER.readValue(json, DocxDsl.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("创建DSL的JSON解析失败：" + e.getMessage());
        }
    }

    /**
     * 解析编辑DSL的JSON文本
     * @param json
     * @return
     */
    public static DocxEditDsl parseEdit(String json) {
        try {
            return MAPPER.readValue(json, DocxEditDsl.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("编辑DSL的JSON解析失败：" + e.getMessage());
        }
    }

    /**
     * 校验创建DSL（错误信息含字段路径，供Agent自我修正）
     * @param dsl
     */
    public static void validateCreate(DocxDsl dsl) {
        if (dsl == null || isBlank(dsl.getPath())) {
            throw new IllegalArgumentException("path不能为空");
        }
        if (!dsl.getPath().toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("path必须以.docx结尾：" + dsl.getPath());
        }
        DocxDsl.DocumentDsl document = dsl.getDocument();
        if (document == null) {
            throw new IllegalArgumentException("document不能为空");
        }
        List<DocxDsl.Block> blocks = document.getBlocks();
        if (blocks == null || blocks.isEmpty()) {
            throw new IllegalArgumentException("document.blocks不能为空");
        }
        if (blocks.size() > MAX_BLOCKS) {
            throw new IllegalArgumentException("内容块数量超上限：" + blocks.size() + " > " + MAX_BLOCKS
                    + "，请拆分文档");
        }
        for (int i = 0; i < blocks.size(); i++) {
            checkBlock(blocks.get(i), "document.blocks[" + i + "]");
        }
    }

    /**
     * 校验编辑DSL
     * @param dsl
     */
    public static void validateEdit(DocxEditDsl dsl) {
        if (dsl == null || isBlank(dsl.getPath())) {
            throw new IllegalArgumentException("path不能为空");
        }
        if (!dsl.getPath().toLowerCase(Locale.ROOT).endsWith(".docx")) {
            throw new IllegalArgumentException("path必须以.docx结尾：" + dsl.getPath());
        }
        List<DocxEditDsl.EditOperation> operations = dsl.getOperations();
        if (operations == null || operations.isEmpty()) {
            throw new IllegalArgumentException("operations不能为空");
        }
        if (operations.size() > MAX_OPERATIONS) {
            throw new IllegalArgumentException("操作数量超上限：" + operations.size() + " > " + MAX_OPERATIONS);
        }
        for (int i = 0; i < operations.size(); i++) {
            checkOperation(operations.get(i), "operations[" + i + "]");
        }
    }

    /**
     * 校验单个内容块
     * @param block
     * @param where
     */
    private static void checkBlock(DocxDsl.Block block, String where) {
        if (block == null || isBlank(block.getType())) {
            throw new IllegalArgumentException(where + ".type不能为空");
        }
        if (!BLOCK_TYPES.contains(block.getType())) {
            throw new IllegalArgumentException(where + ".type非法：" + block.getType()
                    + "（允许：heading/paragraph/table/image）");
        }
        if (block.getAlign() != null && !ALIGNS.contains(block.getAlign())) {
            throw new IllegalArgumentException(where + ".align非法：" + block.getAlign()
                    + "（允许：left/center/right）");
        }
        switch (block.getType()) {
            case "heading" -> {
                if (block.getLevel() == null || block.getLevel() < 1 || block.getLevel() > 4) {
                    throw new IllegalArgumentException(where + ".level须为1-4：" + block.getLevel());
                }
                if (isBlank(block.getText())) {
                    throw new IllegalArgumentException(where + ".text不能为空");
                }
            }
            case "paragraph" -> {
                if (isBlank(block.getText())) {
                    throw new IllegalArgumentException(where + ".text不能为空");
                }
            }
            case "table" -> checkTable(block.getHeader(), block.getRows(), where);
            case "image" -> {
                if (isBlank(block.getImagePath())) {
                    throw new IllegalArgumentException(where + ".imagePath不能为空");
                }
            }
            default -> throw new IllegalArgumentException(where + ".type非法：" + block.getType());
        }
    }

    /**
     * 校验表格定义（行单元格数不要求等于表头列数）
     * @param header
     * @param rows
     * @param where
     */
    private static void checkTable(List<String> header, List<List<String>> rows, String where) {
        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException(where + ".header不能为空");
        }
        if (header.size() > MAX_COLUMNS) {
            throw new IllegalArgumentException(where + ".header列数超上限：" + header.size() + " > " + MAX_COLUMNS);
        }
        if (rows != null && rows.size() > MAX_TABLE_ROWS) {
            throw new IllegalArgumentException(where + ".rows行数超上限：" + rows.size() + " > " + MAX_TABLE_ROWS);
        }
    }

    /**
     * 校验单个编辑操作
     * @param op
     * @param where
     */
    private static void checkOperation(DocxEditDsl.EditOperation op, String where) {
        if (op == null || isBlank(op.getOp())) {
            throw new IllegalArgumentException(where + ".op不能为空");
        }
        switch (op.getOp()) {
            case "replaceText" -> {
                if (isBlank(op.getFrom())) {
                    throw new IllegalArgumentException(where + ".from不能为空");
                }
            }
            case "insertPara" -> {
                if (isBlank(op.getText())) {
                    throw new IllegalArgumentException(where + ".text不能为空");
                }
                if (isNotBlank(op.getType())) {
                    if (!PARA_TYPES.contains(op.getType())) {
                        throw new IllegalArgumentException(where + ".type非法：" + op.getType()
                                + "（允许：paragraph/heading）");
                    }
                    if ("heading".equals(op.getType())
                            && (op.getLevel() == null || op.getLevel() < 1 || op.getLevel() > 4)) {
                        throw new IllegalArgumentException(where + ".level须为1-4：" + op.getLevel());
                    }
                }
            }
            case "addTable" -> checkTable(op.getHeader(), op.getRows(), where);
            default -> throw new IllegalArgumentException(where + ".op非法：" + op.getOp()
                    + "（允许：replaceText/insertPara/addTable）");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean isNotBlank(String value) {
        return !isBlank(value);
    }
}
