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
package com.yangqiongai.ai.agent.local.workspace.excel;

import lombok.Data;

import java.util.List;

/**
 * Excel创建DSL
 * @author yangqiong
 */
@Data
public class ExcelDsl {

    /**
     * 保存路径（相对工作区根）
     */
    private String path;

    /**
     * 工作簿定义
     */
    private WorkbookDsl workbook;

    /**
     * 工作簿定义
     */
    @Data
    public static class WorkbookDsl {

        /**
         * 打开时激活的sheet名称（缺省第一个）
         */
        private String activeSheet;

        /**
         * sheet定义列表（顺序即工作簿内顺序）
         */
        private List<SheetDsl> sheets;
    }

    /**
     * sheet定义
     */
    @Data
    public static class SheetDsl {

        /**
         * sheet名称
         */
        private String name;

        /**
         * 表头行
         */
        private List<String> header;

        /**
         * 数据行（每行为单元格值数组）
         */
        private List<List<Object>> rows;

        /**
         * 列类型：text/number/date/currency/percent（按下标对应数据列）
         */
        private List<String> columnTypes;

        /**
         * 列宽（字符数，按下标对应列）
         */
        private List<Integer> columnWidths;

        /**
         * 合并区域（如 A1:D1）
         */
        private List<String> merges;

        /**
         * 公式列表（支持跨sheet引用）
         */
        private List<FormulaDsl> formulas;

        /**
         * 表头样式
         */
        private StyleDsl headerStyle;

        /**
         * 是否冻结首行
         */
        private Boolean freezeHeader;

        /**
         * 条件格式
         */
        private ConditionalFormatDsl conditionalFormat;
    }

    /**
     * 公式定义
     */
    @Data
    public static class FormulaDsl {

        /**
         * 公式所在单元格（如 D9）
         */
        private String cell;

        /**
         * 公式表达式（不含等号，如 SUM(D2:D8)）
         */
        private String expr;
    }

    /**
     * 单元格样式
     */
    @Data
    public static class StyleDsl {

        /**
         * 是否加粗
         */
        private Boolean bold;

        /**
         * 是否斜体
         */
        private Boolean italic;

        /**
         * 背景色（RRGGBB）
         */
        private String background;

        /**
         * 字体色（RRGGBB）
         */
        private String fontColor;

        /**
         * 水平对齐：left/center/right
         */
        private String align;
    }

    /**
     * 条件格式
     */
    @Data
    public static class ConditionalFormatDsl {

        /**
         * 作用范围（如 D2:D8）
         */
        private String range;

        /**
         * 规则：dataBar/colorScale
         */
        private String rule;
    }
}
