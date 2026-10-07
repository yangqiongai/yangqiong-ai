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
package com.yangqiongai.ai.platform.api.skill;

import com.yangqiongai.ai.agent.skill.model.SkillCategory;
import com.yangqiongai.ai.agent.skill.model.SkillDefinition;
import com.yangqiongai.ai.agent.skill.repository.SkillCategoryRepository;
import com.yangqiongai.ai.agent.skill.repository.SkillConfigRepository;
import com.yangqiongai.ai.common.exception.AiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 技能分类管理单测
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SkillCategoryServiceTest {

    @Mock
    private SkillCategoryRepository categoryRepository;

    @Mock
    private SkillConfigRepository skillConfigRepository;

    private SkillCategoryService service;

    @BeforeEach
    void setUp() {
        service = new SkillCategoryService(categoryRepository, skillConfigRepository);
    }

    @Test
    @DisplayName("分类树：返回层级结构与各节点技能数")
    void treeBuildsHierarchy() {
        SkillDefinition inChild = skill("skill-a", "child-cat");
        SkillDefinition inRoot = skill("skill-b", "root-cat");
        SkillDefinition ungrouped = skill("skill-c", null);
        when(skillConfigRepository.listAll()).thenReturn(List.of(inChild, inRoot, ungrouped));
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        Map<String, Object> result = service.tree();

        List<SkillCategoryService.CategoryNode> nodes =
                (List<SkillCategoryService.CategoryNode>) result.get("nodes");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getCode()).isEqualTo("root-cat");
        assertThat(nodes.get(0).getSkillCount()).isEqualTo(1L);
        assertThat(nodes.get(0).getChildren()).hasSize(1);
        assertThat(nodes.get(0).getChildren().get(0).getSkillCount()).isEqualTo(1L);
        assertThat(result.get("ungroupedCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("新增分类：编码非法或重复时拒绝")
    void createValidations() {
        SkillCategory badCode = category(null, "非法编码!", null);
        assertThatThrownBy(() -> service.create(badCode)).isInstanceOf(AiException.class);

        SkillCategory dup = category(null, "dup-cat", null);
        when(categoryRepository.findByCode("dup-cat")).thenReturn(Optional.of(dup));
        assertThatThrownBy(() -> service.create(dup)).isInstanceOf(AiException.class);

        SkillCategory orphan = category(null, "orphan-cat", 999L);
        when(categoryRepository.findByCode("orphan-cat")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));
        assertThatThrownBy(() -> service.create(orphan)).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("新增分类：合法节点写入库并返回")
    void createPersists() {
        when(categoryRepository.findByCode("new-cat")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));

        SkillCategory request = category(null, "new-cat", 1L);
        request.setName("新分类");
        SkillCategory created = service.create(request);

        assertThat(created.getCode()).isEqualTo("new-cat");
        ArgumentCaptor<SkillCategory> captor = ArgumentCaptor.forClass(SkillCategory.class);
        verify(categoryRepository).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("new-cat");
    }

    @Test
    @DisplayName("更新分类：父节点不能选择自身或子孙")
    void updateRejectsCycle() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        SkillCategory selfParent = category(1L, "root-cat", 1L);
        assertThatThrownBy(() -> service.update(1L, selfParent)).isInstanceOf(AiException.class);

        SkillCategory descendantParent = category(1L, "root-cat", 2L);
        assertThatThrownBy(() -> service.update(1L, descendantParent)).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("更新分类：编码不可修改，合法变更写入库")
    void updateIgnoresCodeAndPersists() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        // 提交体携带新的编码，更新应以存量编码为准
        SkillCategory patch = category(2L, "hacked-code", null);
        patch.setName("改名分类");
        SkillCategory updated = service.update(2L, patch);

        assertThat(updated.getCode()).isEqualTo("child-cat");
        assertThat(updated.getName()).isEqualTo("改名分类");
        ArgumentCaptor<SkillCategory> captor = ArgumentCaptor.forClass(SkillCategory.class);
        verify(categoryRepository).update(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("child-cat");
        assertThat(captor.getValue().getParentId()).isNull();
    }

    @Test
    @DisplayName("删除分类：存在子节点或挂载技能时拒绝")
    void deleteValidations() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));
        when(skillConfigRepository.listAll()).thenReturn(List.of());

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(AiException.class)
                .hasMessageContaining("子分类节点");

        // 子节点清理后，节点下仍挂载技能时同样拒绝
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));
        when(skillConfigRepository.listAll()).thenReturn(List.of(skill("skill-a", "root-cat")));
        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(AiException.class)
                .hasMessageContaining("挂载");
    }

    @Test
    @DisplayName("删除分类：空节点允许删除")
    void deletePersists() {
        when(skillConfigRepository.listAll()).thenReturn(List.of(skill("skill-b", null)));
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));

        service.delete(1L);

        verify(categoryRepository).deleteById(1L);
    }

    @Test
    @DisplayName("子树解析：透传仓储解析结果")
    void resolveSubtreeCodesDelegates() {
        when(categoryRepository.findSubtreeCodes("root-cat"))
                .thenReturn(Set.of("root-cat", "child-cat", "grand-cat"));

        Set<String> codes = service.resolveSubtreeCodes("root-cat");

        assertThat(codes).containsExactlyInAnyOrder("root-cat", "child-cat", "grand-cat");
    }

    @Test
    @DisplayName("子树解析：__ungrouped__返回虚拟编码自身")
    void resolveSubtreeCodesUngrouped() {
        Set<String> codes = service.resolveSubtreeCodes(SkillCategoryRepository.UNGROUPED_CODE);

        assertThat(codes).containsExactly(SkillCategoryRepository.UNGROUPED_CODE);
        verify(categoryRepository, never()).findAll();
    }

    @Test
    @DisplayName("子树解析：分类不存在时返回空集")
    void resolveSubtreeCodesReturnsEmptyForUnknown() {
        when(categoryRepository.findSubtreeCodes("ghost-cat")).thenReturn(Set.of());

        assertThat(service.resolveSubtreeCodes("ghost-cat")).isEmpty();
    }

    @Test
    @DisplayName("归属校验：分类节点不存在时抛出异常")
    void validateCategoryRejectsUnknown() {
        when(categoryRepository.findByCode("ghost-cat")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.validateCategory("ghost-cat")).isInstanceOf(AiException.class);
    }

    @Test
    @DisplayName("归属校验：分类为空时放行")
    void validateCategoryAllowsBlank() {
        service.validateCategory(null);
        service.validateCategory("  ");
        verify(categoryRepository, never()).findByCode(any());
    }

    private SkillDefinition skill(String skillId, String category) {
        SkillDefinition definition = new SkillDefinition();
        definition.setSkillId(skillId);
        definition.setSkillName(skillId);
        definition.setCategory(category);
        return definition;
    }

    private SkillCategory category(Long id, String code, Long parentId) {
        SkillCategory category = new SkillCategory();
        category.setId(id);
        category.setCode(code);
        category.setName(code);
        category.setParentId(parentId);
        category.setSortNum(0);
        return category;
    }
}
