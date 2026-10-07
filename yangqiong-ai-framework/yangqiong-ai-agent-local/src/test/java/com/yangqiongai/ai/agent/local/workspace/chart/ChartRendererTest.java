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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 图表图片渲染测试
 * @author yangqiong
 */
class ChartRendererTest {

    @TempDir
    Path tempDir;

    /**
     * 构建双系列中文DSL
     * @param type
     * @return
     */
    private ChartDsl dsl(String type) {
        ChartDsl dsl = new ChartDsl();
        dsl.setPath("季度报表.png");
        dsl.setType(type);
        dsl.setTitle("季度销售额统计");
        dsl.setCategories(List.of("一季度", "二季度", "三季度"));
        dsl.setSeries(List.of(
                serie("销售额", List.of(120.0, 89.5, 150.0)),
                serie("成本", List.of(80.0, 60.0, 95.5))));
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

    /**
     * 读取PNG IHDR块中的宽度
     * @param png
     * @return
     */
    private static int readPngWidth(byte[] png) {
        return ((png[16] & 0xFF) << 24) | ((png[17] & 0xFF) << 16) | ((png[18] & 0xFF) << 8) | (png[19] & 0xFF);
    }

    /**
     * 读取PNG IHDR块中的高度
     * @param png
     * @return
     */
    private static int readPngHeight(byte[] png) {
        return ((png[20] & 0xFF) << 24) | ((png[21] & 0xFF) << 16) | ((png[22] & 0xFF) << 8) | (png[23] & 0xFF);
    }

    @Test
    void bar图渲染为PNG且数据点数正确() throws Exception {
        Path target = tempDir.resolve("bar.png");
        ChartRenderer.RenderResult result = ChartRenderer.render(dsl("bar"), target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.size(target)).isGreaterThan(0);
        assertThat(result.getPointCount()).isEqualTo(6);
    }

    @Test
    void line图渲染为PNG且数据点数正确() throws Exception {
        Path target = tempDir.resolve("line.png");
        ChartRenderer.RenderResult result = ChartRenderer.render(dsl("line"), target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.size(target)).isGreaterThan(0);
        assertThat(result.getPointCount()).isEqualTo(6);
    }

    @Test
    void pie图渲染为PNG且数据点数正确() throws Exception {
        ChartDsl dsl = dsl("pie");
        dsl.setSeries(List.of(serie("占比", List.of(45.0, 30.0, 25.0))));
        Path target = tempDir.resolve("pie.png");
        ChartRenderer.RenderResult result = ChartRenderer.render(dsl, target);

        assertThat(Files.exists(target)).isTrue();
        assertThat(Files.size(target)).isGreaterThan(0);
        assertThat(result.getPointCount()).isEqualTo(3);
    }

    @Test
    void 中文标题与标签渲染不抛异常() {
        assertThatCode(() -> ChartRenderer.render(dsl("bar"), tempDir.resolve("中文图表.png")))
                .doesNotThrowAnyException();
    }

    @Test
    void 缺省尺寸与自定义尺寸写入PNG头() throws Exception {
        Path defaultSize = tempDir.resolve("default.png");
        ChartRenderer.render(dsl("bar"), defaultSize);
        byte[] png = Files.readAllBytes(defaultSize);
        assertThat(readPngWidth(png)).isEqualTo(800);
        assertThat(readPngHeight(png)).isEqualTo(500);

        ChartDsl custom = dsl("bar");
        custom.setWidth(400);
        custom.setHeight(300);
        Path customSize = tempDir.resolve("custom.png");
        ChartRenderer.render(custom, customSize);
        byte[] small = Files.readAllBytes(customSize);
        assertThat(readPngWidth(small)).isEqualTo(400);
        assertThat(readPngHeight(small)).isEqualTo(300);
    }
}
