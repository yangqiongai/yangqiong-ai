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
 * Word创建DSL
 * @author yangqiong
 */
@Data
public class DocxDsl {

    /**
     * 保存路径（相对工作区根）
     */
    private String path;

    /**
     * 文档定义
     */
    private DocumentDsl document;

    /**
     * 文档定义
     */
    @Data
    public static class DocumentDsl {

        /**
         * 内容块列表（顺序即文档内顺序）
         */
        private List<Block> blocks;
    }

    /**
     * 内容块
     */
    @Data
    public static class Block {

        /**
         * 内容块类型：heading/paragraph/table/image
         */
        private String type;

        /**
         * 文本内容（heading/paragraph必填）
         */
        private String text;

        /**
         * 标题级别（heading时1-4）
         */
        private Integer level;

        /**
         * 水平对齐：left/center/right（可选）
         */
        private String align;

        /**
         * 是否加粗（可选）
         */
        private Boolean bold;

        /**
         * 表头行（table必填）
         */
        private List<String> header;

        /**
         * 数据行（table，值全部字符串化）
         */
        private List<List<String>> rows;

        /**
         * 图片路径（image必填，相对工作区根）
         */
        private String imagePath;

        /**
         * 图片宽度（image可选，单位pt，缺省450，高度按比例缩放）
         */
        private Integer width;
    }
}
