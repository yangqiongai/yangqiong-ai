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
package com.yangqiongai.ai.agent.skill.generation;

import com.yangqiongai.ai.agent.skill.config.SkillProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
@DisplayName("SkillContentValidator 单元测试")
class SkillContentValidatorTest {

    private SkillContentValidator validator;

    @BeforeEach
    void setUp() {
        validator = new SkillContentValidator(new SkillProperties());
    }

    @Nested
    @DisplayName("validate")
    class ValidateTest {

        @Test
        @DisplayName("null内容 → 无效")
        void nullContent_invalid() {
            SkillContentValidator.ValidationResult result = validator.validate(null);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("不能为空"));
        }

        @Test
        @DisplayName("空字符串 → 无效")
        void emptyContent_invalid() {
            SkillContentValidator.ValidationResult result = validator.validate("");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("不能为空"));
        }

        @Test
        @DisplayName("空白字符串 → 无效")
        void blankContent_invalid() {
            SkillContentValidator.ValidationResult result = validator.validate("   ");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("不能为空"));
        }

        @Test
        @DisplayName("缺少front matter → 无效")
        void missingFrontMatter_invalid() {
            SkillContentValidator.ValidationResult result = validator.validate("# 标题\n内容");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("YAML front matter"));
        }

        @Test
        @DisplayName("未关闭front matter → 无效")
        void unclosedFrontMatter_invalid() {
            SkillContentValidator.ValidationResult result = validator.validate("---\nname: test\n内容");

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("未正确关闭"));
        }

        @Test
        @DisplayName("缺少name字段 → 无效")
        void missingNameField_invalid() {
            String markdown = "---\ndescription: 测试技能\n---\n## 目标\n## 工作流\n## 输入\n## 输出";
            SkillContentValidator.ValidationResult result = validator.validate(markdown);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("name"));
        }

        @Test
        @DisplayName("缺少description字段 → 无效")
        void missingDescriptionField_invalid() {
            String markdown = "---\nname: test-skill\n---\n## 目标\n## 工作流\n## 输入\n## 输出";
            SkillContentValidator.ValidationResult result = validator.validate(markdown);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("description"));
        }

        @Test
        @DisplayName("缺少必要章节(目标/工作流/输入/输出) → 无效")
        void missingRequiredSections_invalid() {
            String markdown = "---\nname: test-skill\ndescription: 测试\n---\n## 其他";
            SkillContentValidator.ValidationResult result = validator.validate(markdown);

            assertThat(result.isValid()).isFalse();
            assertThat(result.getErrors()).anyMatch(e -> e.contains("目标"));
            assertThat(result.getErrors()).anyMatch(e -> e.contains("工作流"));
            assertThat(result.getErrors()).anyMatch(e -> e.contains("输入"));
            assertThat(result.getErrors()).anyMatch(e -> e.contains("输出"));
        }

        @Test
        @DisplayName("合法Markdown → 有效")
        void validMarkdown_valid() {
            String markdown = "---\nname: test-skill\ndescription: 测试技能\n---\n\n## 目标\n完成测试\n\n## 工作流\n- 步骤1\n\n## 输入\n- input1\n\n## 输出\n- output1";
            SkillContentValidator.ValidationResult result = validator.validate(markdown);

            assertThat(result.isValid()).isTrue();
            assertThat(result.getErrors()).isEmpty();
        }
    }
}
