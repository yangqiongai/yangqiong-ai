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
package com.yangqiongai.ai.agent.runtime.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用户澄清应答单元测试
 * @author yangqiong
 */
class ClarificationAnswerTest {

    @Test
    @DisplayName("构建澄清应答应携带工具调用ID与应答内容")
    void build_shouldCarryToolCallIdAndAnswer() {
        ClarificationAnswer answer = new ClarificationAnswer("tc-1", "使用MySQL数据源");
        assertThat(answer.getToolCallId()).isEqualTo("tc-1");
        assertThat(answer.getAnswer()).isEqualTo("使用MySQL数据源");
    }

    @Test
    @DisplayName("允许空的工具调用ID与应答内容边界值")
    void build_nullValues_shouldBeAllowed() {
        ClarificationAnswer answer = new ClarificationAnswer(null, null);
        assertThat(answer.getToolCallId()).isNull();
        assertThat(answer.getAnswer()).isNull();
    }
}
