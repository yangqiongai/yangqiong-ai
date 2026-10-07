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
package com.yangqiongai.ai.agent.tool.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DynamicSkillInvoker 单元测试")
class DynamicSkillInvokerTest {

    private final DynamicSkillInvoker invoker = new DynamicSkillInvoker();

    @Test
    @DisplayName("invokeSkill返回not_implemented JSON")
    void shouldReturnNotImplementedJson() {
        String result = invoker.invokeSkill("testSkill", "{}");

        assertThat(result).contains("not_implemented").contains("动态技能调用尚未实现");
    }
}
