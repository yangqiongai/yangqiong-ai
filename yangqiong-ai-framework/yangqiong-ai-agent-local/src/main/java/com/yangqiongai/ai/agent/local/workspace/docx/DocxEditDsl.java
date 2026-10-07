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

import lombok.Data;

import java.util.List;

/**
 * Word编辑DSL
 * @author yangqiong
 */
@Data
public class DocxEditDsl {

    /**
     * 目标文件路径（相对工作区根，须已存在的.docx）
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
         * 操作类型：replaceText/insertPara/addTable
         */
        private String op;

        /**
         * replaceText的被替换文本
         */
        private String from;

        /**
         * replaceText的替换文本（缺省替换为空串）
         */
        private String to;

        /**
         * insertPara的定位文本（段落文本包含匹配，命中第一处后插入；为空或未命中追加文末）
         */
        private String after;

        /**
         * insertPara的段落文本
         */
        private String text;

        /**
         * insertPara的段落类型：paragraph/heading（缺省paragraph）
         */
        private String type;

        /**
         * insertPara的标题级别（heading时1-4）
         */
        private Integer level;

        /**
         * addTable的表头行
         */
        private List<String> header;

        /**
         * addTable的数据行（值全部字符串化）
         */
        private List<List<String>> rows;
    }
}
