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

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 图表DSL解析校验测试
 * @author yangqiong
 */
class ChartValidatorTest {

    /**
     * 构建合法bar图DSL
     * @return
     */
    private ChartDsl barDsl() {
        ChartDsl dsl = new ChartDsl();
        dsl.setPath("图表.png");
        dsl.setType("bar");
        dsl.setTitle("销售额统计");
        dsl.setCategories(List.of("一月", "二月", "三月"));
        dsl.setSeries(List.of(serie("销售额", List.of(120.0, 89.0, 150.0))));
        return dsl;
    }

    /**
     * 构建数据系列
     * @param name
     * @param values
     * @return
     */
    private ChartDsl.Serie serie(String name, List<Double> values) {
        ChartDsl.Serie serie = new ChartDsl.Serie();
        serie.setName(name);
        serie.setValues(values);
        return serie;
    }

    @Test
    void 合法bar与line与pie通过校验() {
        ChartValidator.validate(barDsl());

        ChartDsl line = barDsl();
        line.setType("line");
        ChartValidator.validate(line);

        ChartDsl pie = barDsl();
        pie.setType("pie");
        ChartValidator.validate(pie);
    }

    @Test
    void path非法被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setPath(null);
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("path不能为空");

        dsl.setPath("图表.jpg");
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(".png");
    }

    @Test
    void 非法type被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setType("scatter");
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type非法").hasMessageContaining("scatter");

        dsl.setType("");
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("type不能为空");
    }

    @Test
    void 空categories被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setCategories(List.of());
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("categories不能为空");

        dsl.setCategories(null);
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("categories不能为空");
    }

    @Test
    void categories超上限被拒绝() {
        ChartDsl dsl = barDsl();
        List<String> categories = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (int i = 0; i <= 10000; i++) {
            categories.add("类别" + i);
            values.add((double) i);
        }
        dsl.setCategories(categories);
        dsl.setSeries(List.of(serie("s", values)));
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("categories数量超上限");
    }

    @Test
    void 空series或超上限被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setSeries(List.of());
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("series不能为空");

        List<ChartDsl.Serie> series = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            series.add(serie("s" + i, List.of(1.0, 2.0, 3.0)));
        }
        dsl.setSeries(series);
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("series数量超上限");
    }

    @Test
    void values为空或长度不匹配被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setSeries(List.of(serie("s", List.of(1.0))));
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("series[0].values").hasMessageContaining("1 != 3");

        dsl.setSeries(List.of(serie("s", List.of())));
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("values不能为空");

        dsl.setSeries(List.of(serie("", List.of(1.0, 2.0, 3.0))));
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("name不能为空");
    }

    @Test
    void pie多系列被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setType("pie");
        dsl.setSeries(List.of(serie("a", List.of(1.0, 2.0, 3.0)), serie("b", List.of(4.0, 5.0, 6.0))));
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pie").hasMessageContaining("1个系列");
    }

    @Test
    void 宽高越界被拒绝() {
        ChartDsl dsl = barDsl();
        dsl.setWidth(199);
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("width");

        dsl.setWidth(2001);
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("width");

        dsl.setWidth(800);
        dsl.setHeight(5000);
        assertThatThrownBy(() -> ChartValidator.validate(dsl))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("height");

        dsl.setHeight(500);
        ChartValidator.validate(dsl);
    }

    @Test
    void 解析失败报含上下文的错误且容忍未知字段() {
        assertThatThrownBy(() -> ChartValidator.parse("{not json"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("JSON解析失败");

        ChartDsl dsl = ChartValidator.parse(
                "{\"path\":\"a.png\",\"type\":\"bar\",\"extra\":1,\"categories\":[\"一月\"],"
                        + "\"series\":[{\"name\":\"s\",\"values\":[1],\"unknown\":true}]}");
        assertThat(dsl.getType()).isEqualTo("bar");
        assertThat(dsl.getSeries()).hasSize(1);
        assertThat(dsl.getSeries().get(0).getValues()).containsExactly(1.0);
    }
}
