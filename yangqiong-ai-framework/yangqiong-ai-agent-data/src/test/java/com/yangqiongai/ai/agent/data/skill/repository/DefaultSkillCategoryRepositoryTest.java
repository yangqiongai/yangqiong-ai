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
package com.yangqiongai.ai.agent.data.skill.repository;

import com.yangqiongai.ai.agent.data.skill.entity.SkillCategoryEntity;
import com.yangqiongai.ai.agent.data.skill.mapper.SkillCategoryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 技能分类存储单测
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DefaultSkillCategoryRepositoryTest {

    @Mock
    private SkillCategoryMapper mapper;

    @InjectMocks
    private DefaultSkillCategoryRepository repository;

    @Test
    @DisplayName("子树解析：遍历收集自身与全部子孙节点编码")
    void findSubtreeCodesCollectsDescendants() {
        when(mapper.selectList(any())).thenReturn(List.of(
                entity(1L, "root-cat", null),
                entity(2L, "child-cat", 1L),
                entity(3L, "grand-cat", 2L),
                entity(4L, "other-cat", null)));

        Set<String> codes = repository.findSubtreeCodes("root-cat");

        assertThat(codes).containsExactlyInAnyOrder("root-cat", "child-cat", "grand-cat");
    }

    @Test
    @DisplayName("子树解析：分类不存在时返回空集")
    void findSubtreeCodesReturnsEmptyForUnknown() {
        when(mapper.selectList(any())).thenReturn(List.of(entity(1L, "root-cat", null)));

        assertThat(repository.findSubtreeCodes("ghost-cat")).isEmpty();
    }

    @Test
    @DisplayName("按编码查询：存在时返回分类，缺失时返回空")
    void findByCodeReturnsOptional() {
        when(mapper.selectOne(any())).thenReturn(entity(1L, "root-cat", null));

        Optional<com.yangqiongai.ai.agent.skill.model.SkillCategory> found = repository.findByCode("root-cat");
        assertThat(found).isPresent();
        assertThat(found.get().getCode()).isEqualTo("root-cat");

        when(mapper.selectOne(any())).thenReturn(null);
        assertThat(repository.findByCode("ghost-cat")).isEmpty();
    }

    private SkillCategoryEntity entity(Long id, String code, Long parentId) {
        SkillCategoryEntity entity = new SkillCategoryEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setName(code);
        entity.setParentId(parentId);
        entity.setSortNum(0);
        return entity;
    }
}
