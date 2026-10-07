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
package com.yangqiongai.ai.agent.skill.resolver;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.skill.config.SkillConditionalProperties;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("DefaultConditionalSkillMatcher 单元测试")
class DefaultConditionalSkillMatcherTest {

    @Mock
    private SkillConditionalProperties skillConditionalProperties;

    @InjectMocks
    private DefaultConditionalSkillMatcher matcher;

    private AgentRequest baseRequest;

    @BeforeEach
    void setUp() {
        baseRequest = new AgentRequest()
                .agentCode("wiki-ingest")
                .sessionId("session-1")
                .body(Map.of("projectId", "proj-001"));
    }

    @Nested
    @DisplayName("isSkillApplicable")
    class IsSkillApplicableTest {

        @Test
        @DisplayName("任务白名单匹配 → 适用")
        void taskWhitelistMatch() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(Map.of("agentCodes", List.of("wiki-ingest", "wiki-lint")));

            boolean result = matcher.isSkillApplicable(skill, baseRequest);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("通配符*在agentCodes中为字面匹配，不匹配其他agentCode → 不适用")
        void wildcardIsLiteral_notApplicable() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(Map.of("agentCodes", List.of("*")));

            boolean result = matcher.isSkillApplicable(skill, baseRequest);

            // checkTaskAllowList使用contains精确匹配，*不是通配符
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("请求数据体约束匹配 → 适用")
        void metaConstraintMatch() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(Map.of(
                    "agentCodes", List.of("wiki-ingest"),
                    "body", Map.of("projectId", "proj-001")
            ));

            boolean result = matcher.isSkillApplicable(skill, baseRequest);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("任务白名单不匹配 → 不适用")
        void taskWhitelistNoMatch() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(Map.of("agentCodes", List.of("other-task")));

            boolean result = matcher.isSkillApplicable(skill, baseRequest);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("无约束条件 → 适用")
        void noConstraints_applicable() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(null);

            boolean result = matcher.isSkillApplicable(skill, baseRequest);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("空约束条件 → 适用")
        void emptyConstraints_applicable() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(Map.of());

            boolean result = matcher.isSkillApplicable(skill, baseRequest);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("技能为null → 不适用")
        void nullSkill_notApplicable() {
            boolean result = matcher.isSkillApplicable(null, baseRequest);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("请求为null → 不适用")
        void nullRequest_notApplicable() {
            SkillDefinition skill = new SkillDefinition();
            skill.setConditions(Map.of("agentCodes", List.of("wiki-ingest")));

            boolean result = matcher.isSkillApplicable(skill, null);

            assertThat(result).isFalse();
        }
    }
}
