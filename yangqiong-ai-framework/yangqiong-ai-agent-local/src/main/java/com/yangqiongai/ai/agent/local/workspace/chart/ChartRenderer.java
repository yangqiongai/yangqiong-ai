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

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.StandardChartTheme;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.data.general.DefaultPieDataset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

/**
 * 图表图片渲染
 * <p>
 * 渲染前通过StandardChartTheme应用中文字体（雅黑→宋体→逻辑SansSerif兜底），保证中文标签不乱码。
 * </p>
 * @author yangqiong
 */
public class ChartRenderer {

    private static final Logger log = LoggerFactory.getLogger(ChartRenderer.class);

    /**
     * 缺省图片宽度（像素）
     */
    private static final int DEFAULT_WIDTH = 800;

    /**
     * 缺省图片高度（像素）
     */
    private static final int DEFAULT_HEIGHT = 500;

    /**
     * 中文字体候选（按优先级）
     */
    private static final List<String> CN_FONT_CANDIDATES = List.of("Microsoft YaHei", "SimSun");

    private ChartRenderer() {
    }

    /**
     * 渲染结果
     */
    public static class RenderResult {

        /**
         * 数据点数量（类别数×系列数）
         */
        private final int pointCount;

        RenderResult(int pointCount) {
            this.pointCount = pointCount;
        }

        public int getPointCount() {
            return pointCount;
        }
    }

    /**
     * 按DSL渲染PNG图表图片并落盘
     * @param dsl
     * @param target
     * @return
     * @throws Exception
     */
    public static RenderResult render(ChartDsl dsl, Path target) throws Exception {
        applyChineseTheme();
        int width = dsl.getWidth() != null ? dsl.getWidth() : DEFAULT_WIDTH;
        int height = dsl.getHeight() != null ? dsl.getHeight() : DEFAULT_HEIGHT;
        List<String> categories = dsl.getCategories();
        JFreeChart chart;
        if ("pie".equals(dsl.getType())) {
            chart = buildPieChart(dsl, categories);
        } else {
            chart = buildCategoryChart(dsl, categories);
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        ChartUtils.saveChartAsPNG(target.toFile(), chart, width, height);
        int pointCount = categories.size() * dsl.getSeries().size();
        log.info("图表已渲染: {}（type={}，{}x{}，{}数据点）", target, dsl.getType(), width, height, pointCount);
        return new RenderResult(pointCount);
    }

    /**
     * 构建柱状/折线图（categories为行、系列为列）
     * @param dsl
     * @param categories
     * @return
     */
    private static JFreeChart buildCategoryChart(ChartDsl dsl, List<String> categories) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (ChartDsl.Serie serie : dsl.getSeries()) {
            List<Double> values = serie.getValues();
            for (int i = 0; i < categories.size(); i++) {
                dataset.addValue(values.get(i), categories.get(i), serie.getName());
            }
        }
        PlotOrientation orientation = PlotOrientation.VERTICAL;
        if ("line".equals(dsl.getType())) {
            return ChartFactory.createLineChart(dsl.getTitle(), "类别", "数值", dataset, orientation, true, true, false);
        }
        return ChartFactory.createBarChart(dsl.getTitle(), "类别", "数值", dataset, orientation, true, true, false);
    }

    /**
     * 构建饼图（数据集键为类别，标题缺省取系列名）
     * @param dsl
     * @param categories
     * @return
     */
    private static JFreeChart buildPieChart(ChartDsl dsl, List<String> categories) {
        ChartDsl.Serie serie = dsl.getSeries().get(0);
        DefaultPieDataset<String> dataset = new DefaultPieDataset<>();
        List<Double> values = serie.getValues();
        for (int i = 0; i < categories.size(); i++) {
            dataset.setValue(categories.get(i), values.get(i));
        }
        String title = isBlank(dsl.getTitle()) ? serie.getName() : dsl.getTitle();
        return ChartFactory.createPieChart(title, dataset, true, true, false);
    }

    /**
     * 构建并应用中文字体主题
     */
    private static void applyChineseTheme() {
        Font base = resolveChineseFont();
        StandardChartTheme theme = new StandardChartTheme("中文字体");
        theme.setExtraLargeFont(base.deriveFont(Font.BOLD, 18f));
        theme.setLargeFont(base.deriveFont(Font.PLAIN, 14f));
        theme.setRegularFont(base.deriveFont(Font.PLAIN, 12f));
        theme.setSmallFont(base.deriveFont(Font.PLAIN, 10f));
        ChartFactory.setChartTheme(theme);
    }

    /**
     * 探测可用中文字体（雅黑→宋体，均缺失时用逻辑SansSerif兜底）
     * @return
     */
    private static Font resolveChineseFont() {
        Set<String> families = Set.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String candidate : CN_FONT_CANDIDATES) {
            if (families.contains(candidate)) {
                return new Font(candidate, Font.PLAIN, 12);
            }
        }
        return new Font(Font.SANS_SERIF, Font.PLAIN, 12);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
