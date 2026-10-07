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
package com.yangqiongai.ai.platform.connector.docparser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("页眉页脚清洗单元测试")
class HeaderFooterCleanerTest {

    @Test
    @DisplayName("多页重复页眉被移除，差异页码页脚保留")
    void shouldRemoveRepeatedHeaderKeepVaryingFooter() {
        List<String> pages = List.of(
                "XX公司机密\n正文内容一\n1",
                "XX公司机密\n正文内容二\n2",
                "XX公司机密\n正文内容三\n3");

        List<String> cleaned = HeaderFooterCleaner.clean(pages);

        assertThat(cleaned).hasSize(3);
        assertThat(cleaned.get(0)).isEqualTo("正文内容一\n1");
        assertThat(cleaned.get(1)).isEqualTo("正文内容二\n2");
        assertThat(cleaned.get(2)).isEqualTo("正文内容三\n3");
    }

    @Test
    @DisplayName("多页重复页脚被移除")
    void shouldRemoveRepeatedFooter() {
        List<String> pages = List.of(
                "内容甲\n第X页 共3页",
                "内容乙\n第X页 共3页",
                "内容丙\n第X页 共3页");

        List<String> cleaned = HeaderFooterCleaner.clean(pages);

        assertThat(cleaned).containsExactly("内容甲", "内容乙", "内容丙");
    }

    @Test
    @DisplayName("单页文档不做清洗")
    void shouldKeepSinglePageUntouched() {
        List<String> pages = List.of("XX公司机密\n唯一内容");

        List<String> cleaned = HeaderFooterCleaner.clean(pages);

        assertThat(cleaned).isEqualTo(pages);
    }

    @Test
    @DisplayName("重复页数未达阈值时保留")
    void shouldKeepWhenBelowThreshold() {
        List<String> pages = List.of(
                "共通行\n内容一",
                "独有行\n内容二",
                "独有行2\n内容三",
                "独有行3\n内容四");

        List<String> cleaned = HeaderFooterCleaner.clean(pages);

        assertThat(cleaned.get(0)).isEqualTo("共通行\n内容一");
    }

    @Test
    @DisplayName("超长重复行视为正文不清洗")
    void shouldKeepLongRepeatedLine() {
        String longLine = "长".repeat(150);
        List<String> pages = List.of(
                longLine + "\n内容一",
                longLine + "\n内容二",
                longLine + "\n内容三");

        List<String> cleaned = HeaderFooterCleaner.clean(pages);

        assertThat(cleaned.get(0)).startsWith(longLine);
    }

    @Test
    @DisplayName("仅含页眉页脚的页清洗后为空白")
    void shouldBlankPageOnlyHeaderFooter() {
        List<String> pages = List.of(
                "页眉行\n正文甲\n页脚行",
                "页眉行\n页脚行",
                "页眉行\n正文乙\n页脚行");

        List<String> cleaned = HeaderFooterCleaner.clean(pages);

        assertThat(cleaned).hasSize(3);
        assertThat(cleaned.get(0)).isEqualTo("正文甲");
        assertThat(cleaned.get(1)).isBlank();
        assertThat(cleaned.get(2)).isEqualTo("正文乙");
    }
}
