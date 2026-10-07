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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 图表DSL解析校验
 * @author yangqiong
 */
public class ChartValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 合法图表类型
     */
    private static final Set<String> TYPES = Set.of("bar", "line", "pie");

    /**
     * 类别数量上限
     */
    private static final int MAX_CATEGORIES = 10000;

    /**
     * 系列数量上限
     */
    private static final int MAX_SERIES = 10;

    /**
     * 图片尺寸下限（像素）
     */
    private static final int MIN_SIZE = 200;

    /**
     * 图片尺寸上限（像素）
     */
    private static final int MAX_SIZE = 2000;

    private ChartValidator() {
    }

    /**
     * 解析图表DSL的JSON文本
     * @param json
     * @return
     */
    public static ChartDsl parse(String json) {
        try {
            return MAPPER.readValue(json, ChartDsl.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("图表DSL的JSON解析失败：" + e.getMessage());
        }
    }

    /**
     * 校验图表DSL（错误信息含字段路径，供Agent自我修正）
     * @param dsl
     */
    public static void validate(ChartDsl dsl) {
        if (dsl == null || isBlank(dsl.getPath())) {
            throw new IllegalArgumentException("path不能为空");
        }
        if (!dsl.getPath().toLowerCase(Locale.ROOT).endsWith(".png")) {
            throw new IllegalArgumentException("path必须以.png结尾：" + dsl.getPath());
        }
        if (isBlank(dsl.getType())) {
            throw new IllegalArgumentException("type不能为空（允许：bar/line/pie）");
        }
        if (!TYPES.contains(dsl.getType())) {
            throw new IllegalArgumentException("type非法：" + dsl.getType() + "（允许：bar/line/pie）");
        }
        List<String> categories = dsl.getCategories();
        if (categories == null || categories.isEmpty()) {
            throw new IllegalArgumentException("categories不能为空");
        }
        if (categories.size() > MAX_CATEGORIES) {
            throw new IllegalArgumentException("categories数量超上限：" + categories.size() + " > " + MAX_CATEGORIES);
        }
        List<ChartDsl.Serie> series = dsl.getSeries();
        if (series == null || series.isEmpty()) {
            throw new IllegalArgumentException("series不能为空");
        }
        if (series.size() > MAX_SERIES) {
            throw new IllegalArgumentException("series数量超上限：" + series.size() + " > " + MAX_SERIES);
        }
        if ("pie".equals(dsl.getType()) && series.size() > 1) {
            throw new IllegalArgumentException("pie图仅允许1个系列，当前" + series.size() + "个");
        }
        checkSize(dsl.getWidth(), "width");
        checkSize(dsl.getHeight(), "height");
        for (int i = 0; i < series.size(); i++) {
            checkSerie(series.get(i), "series[" + i + "]", categories.size());
        }
    }

    /**
     * 校验单个数据系列
     * @param serie
     * @param where
     * @param categorySize
     */
    private static void checkSerie(ChartDsl.Serie serie, String where, int categorySize) {
        if (serie == null || isBlank(serie.getName())) {
            throw new IllegalArgumentException(where + ".name不能为空");
        }
        List<Double> values = serie.getValues();
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException(where + ".values不能为空");
        }
        if (values.size() != categorySize) {
            throw new IllegalArgumentException(where + ".values长度须等于categories数量：" + values.size()
                    + " != " + categorySize);
        }
    }

    /**
     * 校验图片尺寸取值
     * @param size
     * @param field
     */
    private static void checkSize(Integer size, String field) {
        if (size != null && (size < MIN_SIZE || size > MAX_SIZE)) {
            throw new IllegalArgumentException(field + "须在[" + MIN_SIZE + "," + MAX_SIZE + "]范围内：" + size);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
