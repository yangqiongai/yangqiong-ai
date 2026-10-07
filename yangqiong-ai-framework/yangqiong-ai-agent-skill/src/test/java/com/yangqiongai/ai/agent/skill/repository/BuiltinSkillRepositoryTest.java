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
package com.yangqiongai.ai.agent.skill.repository;

import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BuiltinSkillRepository 单元测试")
class BuiltinSkillRepositoryTest {

    @Test
    @DisplayName("resources目录下的md文件作为资源加载，不覆盖技能主定义")
    void resourceMdFileDoesNotOverrideMainDefinition() {
        BuiltinSkillRepository repository = new BuiltinSkillRepository();

        SkillDefinition skill = repository.findById("demo-skill").orElse(null);

        assertThat(skill).isNotNull();
        assertThat(skill.getSkillContent()).contains("主定义标记");
        assertThat(skill.getResources()).containsKey("usage-notes.md");
        assertThat(skill.getResources().get("usage-notes.md")).contains("资源标记");
    }

    @Test
    @DisplayName("resources目录下的md文件不会注册为独立技能")
    void resourceMdFileNotRegisteredAsSkill() {
        BuiltinSkillRepository repository = new BuiltinSkillRepository();

        boolean exists = repository.findAll().stream()
                .anyMatch(s -> "usage-notes".equals(s.getSkillId()));

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("平铺结构md文件仍按文件名识别为技能")
    void flatSkillFileIsLoaded() {
        BuiltinSkillRepository repository = new BuiltinSkillRepository();

        SkillDefinition skill = repository.findById("flat-skill").orElse(null);

        assertThat(skill).isNotNull();
        assertThat(skill.getSkillContent()).contains("平铺结构标记");
    }
}
