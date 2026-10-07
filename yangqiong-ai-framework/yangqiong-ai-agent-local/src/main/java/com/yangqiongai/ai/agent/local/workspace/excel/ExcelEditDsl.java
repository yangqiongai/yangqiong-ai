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
import java.util.Map;

/**
 * Excel编辑DSL
 * @author yangqiong
 */
@Data
public class ExcelEditDsl {

    /**
     * 目标文件路径（相对工作区根，须已存在的.xlsx）
     */
    private String path;

    /**
     * 编辑操作列表（逐条顺序执行，任一失败整体失败）
     */
    private List<EditOperation> operations;

    /**
     * 编辑操作
     */
    @Data
    public static class EditOperation {

        /**
         * 操作类型：setCell/addRow/deleteRow/addSheet/deleteSheet/renameSheet/setStyle/sort
         */
        private String op;

        /**
         * 目标sheet名称（addSheet时为新sheet名）
         */
        private String sheet;

        /**
         * renameSheet的源sheet名
         */
        private String from;

        /**
         * renameSheet的目标sheet名
         */
        private String to;

        /**
         * 行定位条件（键为表头名或列字母如A，多条件为与关系）
         */
        private Map<String, Object> match;

        /**
         * setCell的目标列（表头名或列字母）
         */
        private String column;

        /**
         * setCell写入的值
         */
        private Object value;

        /**
         * addRow的行值数组
         */
        private List<Object> values;

        /**
         * addSheet的表头
         */
        private List<String> header;

        /**
         * addSheet的数据行
         */
        private List<List<Object>> rows;

        /**
         * addSheet的列类型（text/number/date/currency/percent）
         */
        private List<String> columnTypes;

        /**
         * setStyle的作用范围（如 A1:D1）
         */
        private String range;

        /**
         * setStyle的样式
         */
        private ExcelDsl.StyleDsl style;

        /**
         * sort的排序列（表头名或列字母）
         */
        private String by;

        /**
         * sort排序方向：asc/desc（缺省asc）
         */
        private String order;
    }
}
