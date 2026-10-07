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
package com.yangqiongai.ai.agent.local.workspace.chart;

import lombok.Data;

import java.util.List;

/**
 * 图表渲染DSL
 * @author yangqiong
 */
@Data
public class ChartDsl {

    /**
     * 图片保存路径（相对工作区根，.png结尾）
     */
    private String path;

    /**
     * 图表类型：bar柱状图/line折线图/pie饼图
     */
    private String type;

    /**
     * 图表标题（可选）
     */
    private String title;

    /**
     * 类别标签列表
     */
    private List<String> categories;

    /**
     * 数据系列列表
     */
    private List<Serie> series;

    /**
     * 图片宽度（像素，可选，缺省800）
     */
    private Integer width;

    /**
     * 图片高度（像素，可选，缺省500）
     */
    private Integer height;

    /**
     * 数据系列
     */
    @Data
    public static class Serie {

        /**
         * 系列名称
         */
        private String name;

        /**
         * 数值列表（与categories一一对应）
         */
        private List<Double> values;
    }
}
