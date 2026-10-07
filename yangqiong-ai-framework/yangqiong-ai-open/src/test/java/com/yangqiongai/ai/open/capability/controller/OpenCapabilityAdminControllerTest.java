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
package com.yangqiongai.ai.open.capability.controller;

import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategory;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinition;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistory;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionRepository;
import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRecord;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 开放能力目录管理单测
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class OpenCapabilityAdminControllerTest {

    @Mock
    private CapabilityCatalog catalog;

    @Mock
    private CapabilityDefinitionRepository definitionRepository;

    @Mock
    private CapabilityCategoryRepository categoryRepository;

    @Mock
    private CapabilityCallRepository callRepository;

    @Mock
    private CapabilityDefinitionHistoryRepository historyRepository;

    private OpenCapabilityAdminController controller;

    @BeforeEach
    void setUp() {
        controller = new OpenCapabilityAdminController(catalog, definitionRepository, categoryRepository,
                callRepository, historyRepository);
    }

    @Test
    @DisplayName("目录分页：按编码排序并返回总数")
    void listPagesCatalog() {
        when(catalog.list()).thenReturn(List.of(spec("b-cap"), spec("a-cap")));

        Map<String, Object> page1 = controller.list(1, 1, null, null).getData();
        Map<String, Object> page2 = controller.list(2, 1, null, null).getData();

        assertThat(page1.get("total")).isEqualTo(2);
        assertThat(((List<CapabilitySpec>) page1.get("list")).get(0).getCode()).isEqualTo("a-cap");
        assertThat(((List<CapabilitySpec>) page2.get("list")).get(0).getCode()).isEqualTo("b-cap");
    }

    @Test
    @DisplayName("目录分页：关键字匹配编码/名称/分类")
    void listFiltersByKeyword() {
        CapabilitySpec target = spec("text-summarize");
        target.setName("文本摘要");
        target.setCategory("text");
        when(catalog.list()).thenReturn(List.of(target, spec("other")));

        Map<String, Object> result = controller.list(1, 10, "摘要", null).getData();

        assertThat((List<CapabilitySpec>) result.get("list")).hasSize(1);
        assertThat(((List<CapabilitySpec>) result.get("list")).get(0).getCode()).isEqualTo("text-summarize");
    }

    @Test
    @DisplayName("目录分页：按分类子树过滤返回范围内能力")
    void listFiltersByCategorySubtree() {
        CapabilitySpec child = spec("cap-child");
        child.setCategory("child-cat");
        CapabilitySpec other = spec("cap-other");
        other.setCategory("other-cat");
        when(catalog.list()).thenReturn(List.of(child, other));
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L),
                category(3L, "other-cat", null)));

        Map<String, Object> result = controller.list(1, 10, null, "root-cat").getData();

        assertThat((List<CapabilitySpec>) result.get("list")).hasSize(1);
        assertThat(((List<CapabilitySpec>) result.get("list")).get(0).getCode()).isEqualTo("cap-child");
    }

    @Test
    @DisplayName("目录分页：__ungrouped__过滤未分类能力")
    void listFiltersByUngrouped() {
        CapabilitySpec grouped = spec("cap-grouped");
        grouped.setCategory("root-cat");
        CapabilitySpec ungrouped = spec("cap-ungrouped");
        when(catalog.list()).thenReturn(List.of(grouped, ungrouped));

        Map<String, Object> result = controller.list(1, 10, null, "__ungrouped__").getData();

        assertThat((List<CapabilitySpec>) result.get("list")).hasSize(1);
        assertThat(((List<CapabilitySpec>) result.get("list")).get(0).getCode()).isEqualTo("cap-ungrouped");
    }

    @Test
    @DisplayName("新增能力：写入库定义并刷新目录")
    void createPersistsDefinition() {
        // 第一次调用用于重复校验(空)，插入后refreshCatalog再次查询返回库定义
        when(definitionRepository.findByCode("new-cap"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(new CapabilityDefinition()));
        when(catalog.get("new-cap")).thenReturn(spec("new-cap"));

        CapabilitySpec request = spec("new-cap");
        request.setName("新能力");

        var result = controller.create(request);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<CapabilityDefinition> captor = ArgumentCaptor.forClass(CapabilityDefinition.class);
        verify(definitionRepository).create(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo("new-cap");
        assertThat(captor.getValue().getDefinitionJson()).contains("new-cap");
        verify(catalog).mergeDatabaseDefinition(any(CapabilityDefinition.class));
    }

    @Test
    @DisplayName("新增能力：分类节点不存在时拒绝")
    void createRejectsUnknownCategory() {
        when(categoryRepository.findByCode("ghost-cat")).thenReturn(Optional.empty());

        CapabilitySpec request = spec("new-cap");
        request.setCategory("ghost-cat");

        var result = controller.create(request);

        assertThat(result.isSuccess()).isFalse();
        verify(definitionRepository, never()).create(any());
    }

    @Test
    @DisplayName("新增能力：编码重复时拒绝")
    void createRejectsDuplicateCode() {
        when(definitionRepository.findByCode("dup-cap")).thenReturn(Optional.of(new CapabilityDefinition()));

        var result = controller.create(spec("dup-cap"));

        assertThat(result.isSuccess()).isFalse();
        verify(definitionRepository, never()).create(any());
    }

    @Test
    @DisplayName("新增能力：编码为空时拒绝")
    void createRejectsBlankCode() {
        var result = controller.create(spec(" "));

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("新增能力：WORKFLOW执行体为企业版能力，社区版拒绝")
    void createRejectsWorkflowExecutor() {
        CapabilitySpec request = spec("wf-cap");
        request.setExecType("WORKFLOW");
        request.setWorkflowCode("order-audit");

        var result = controller.create(request);

        assertThat(result.isSuccess()).isFalse();
        verify(definitionRepository, never()).create(any());
    }

    @Test
    @DisplayName("更新能力：切换为工作流执行体被拒绝")
    void updateRejectsWorkflowExecutor() throws Exception {
        CapabilityDefinition existing = new CapabilityDefinition();
        existing.setCode("edit-cap");
        existing.setDefinitionJson("{\"code\":\"edit-cap\",\"agentCode\":\"default\"}");
        when(definitionRepository.findByCode("edit-cap")).thenReturn(Optional.of(existing));
        when(catalog.get("edit-cap")).thenReturn(spec("edit-cap"));

        CapabilitySpec patch = new CapabilitySpec();
        patch.setExecType("WORKFLOW");
        patch.setWorkflowCode("order-audit");

        var result = controller.update("edit-cap", patch, null);

        assertThat(result.isSuccess()).isFalse();
        verify(definitionRepository, never()).override(any(), any());
    }

    @Test
    @DisplayName("更新能力：以库定义合并字段并支持启停")
    void updateMergesFieldsAndToggles() throws Exception {
        CapabilityDefinition existing = new CapabilityDefinition();
        existing.setCode("edit-cap");
        existing.setDefinitionJson("{\"code\":\"edit-cap\",\"name\":\"旧名\",\"description\":\"旧描述\"}");
        when(definitionRepository.findByCode("edit-cap")).thenReturn(Optional.of(existing));

        CapabilitySpec patch = new CapabilitySpec();
        patch.setName("新名");

        var result = controller.update("edit-cap", patch, Boolean.FALSE);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(definitionRepository).override(eq("edit-cap"), captor.capture());
        assertThat(captor.getValue().get("name")).isEqualTo("新名");
        // 未提交字段保留库内旧值
        assertThat(captor.getValue().get("description")).isEqualTo("旧描述");
        verify(definitionRepository).disable("edit-cap");
        verify(definitionRepository, never()).enable(anyString());
    }

    @Test
    @DisplayName("更新能力：能力不存在时拒绝")
    void updateRejectsUnknownCapability() {
        when(definitionRepository.findByCode("ghost")).thenReturn(Optional.empty());
        when(catalog.get("ghost")).thenReturn(null);

        var result = controller.update("ghost", new CapabilitySpec(), null);

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("更新能力：版本号未调整时自动递增补丁号并记录快照")
    void updateAutoBumpsVersionAndSavesHistory() throws Exception {
        CapabilityDefinition existing = new CapabilityDefinition();
        existing.setCode("edit-cap");
        existing.setDefinitionJson("{\"code\":\"edit-cap\",\"version\":\"1.0.0\",\"name\":\"旧名\"}");
        when(definitionRepository.findByCode("edit-cap")).thenReturn(Optional.of(existing));

        CapabilitySpec patch = new CapabilitySpec();
        patch.setVersion("1.0.0");

        var result = controller.update("edit-cap", patch, null);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(definitionRepository).override(eq("edit-cap"), captor.capture());
        // 提交版本与现版本一致时自动+0.0.1
        assertThat(captor.getValue().get("version")).isEqualTo("1.0.1");
        verify(historyRepository).save(argThat(history ->
                "UPDATE".equals(history.getOperation())
                        && "edit-cap".equals(history.getCapabilityCode())
                        && "1.0.1".equals(history.getVersion())));
    }

    @Test
    @DisplayName("回滚版本：以快照内容生成新版本并记录回滚快照")
    void rollbackRestoresSnapshotAsNewVersion() {
        CapabilityDefinition existing = new CapabilityDefinition();
        existing.setCode("rb-cap");
        existing.setDefinitionJson("{\"code\":\"rb-cap\",\"version\":\"2.0.0\",\"name\":\"新版\"}");
        when(definitionRepository.findByCode("rb-cap")).thenReturn(Optional.of(existing));

        CapabilityDefinitionHistory history = new CapabilityDefinitionHistory();
        history.setCapabilityCode("rb-cap");
        history.setVersion("1.5.0");
        history.setDefinitionJson("{\"code\":\"rb-cap\",\"version\":\"1.5.0\",\"name\":\"旧版\"}");
        when(historyRepository.findById(9L)).thenReturn(Optional.of(history));

        var result = controller.rollback("rb-cap", 9L);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(definitionRepository).override(eq("rb-cap"), captor.capture());
        // 回滚在现版本基础上递增补丁号，避免历史版本号低于现版本
        assertThat(captor.getValue().get("name")).isEqualTo("旧版");
        assertThat(captor.getValue().get("version")).isEqualTo("2.0.1");
        verify(historyRepository).save(argThat(item ->
                "ROLLBACK".equals(item.getOperation()) && "2.0.1".equals(item.getVersion())));
    }

    @Test
    @DisplayName("回滚版本：历史记录不存在时拒绝")
    void rollbackRejectsUnknownHistory() {
        when(historyRepository.findById(404L)).thenReturn(Optional.empty());

        var result = controller.rollback("rb-cap", 404L);

        assertThat(result.isSuccess()).isFalse();
        verify(definitionRepository, never()).override(anyString(), any());
    }

    @Test
    @DisplayName("删除能力：注销目录中的能力")
    void deleteUnregistersCapability() {
        var result = controller.delete("db-cap");

        assertThat(result.isSuccess()).isTrue();
        verify(definitionRepository).delete("db-cap");
        verify(catalog).unregister("db-cap");
        verify(historyRepository).deleteByCode("db-cap");
    }

    @Test
    @DisplayName("分类树：返回层级结构与各节点能力数")
    void categoryTreeBuildsHierarchy() {
        CapabilitySpec inChild = spec("cap-a");
        inChild.setCategory("child-cat");
        CapabilitySpec inRoot = spec("cap-b");
        inRoot.setCategory("root-cat");
        CapabilitySpec ungrouped = spec("cap-c");
        when(catalog.list()).thenReturn(List.of(inChild, inRoot, ungrouped));
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        Map<String, Object> result = controller.categoryTree().getData();

        List<OpenCapabilityAdminController.CategoryNode> nodes =
                (List<OpenCapabilityAdminController.CategoryNode>) result.get("nodes");
        assertThat(nodes).hasSize(1);
        assertThat(nodes.get(0).getCode()).isEqualTo("root-cat");
        assertThat(nodes.get(0).getCapabilityCount()).isEqualTo(1L);
        assertThat(nodes.get(0).getChildren()).hasSize(1);
        assertThat(nodes.get(0).getChildren().get(0).getCapabilityCount()).isEqualTo(1L);
        assertThat(result.get("ungroupedCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("新增分类：编码非法或重复时拒绝")
    void createCategoryValidations() {
        CapabilityCategory badCode = category(null, "非法编码!", null);
        var invalidResult = controller.createCategory(badCode);
        assertThat(invalidResult.isSuccess()).isFalse();

        CapabilityCategory dup = category(null, "dup-cat", null);
        when(categoryRepository.findByCode("dup-cat")).thenReturn(Optional.of(dup));
        var dupResult = controller.createCategory(dup);
        assertThat(dupResult.isSuccess()).isFalse();

        CapabilityCategory orphan = category(null, "orphan-cat", 999L);
        when(categoryRepository.findByCode("orphan-cat")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));
        var parentResult = controller.createCategory(orphan);
        assertThat(parentResult.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("新增分类：合法节点写入库并返回")
    void createCategoryPersists() {
        when(categoryRepository.findByCode("new-cat")).thenReturn(Optional.empty());
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));

        CapabilityCategory request = category(null, "new-cat", 1L);
        request.setName("新分类");
        var result = controller.createCategory(request);

        assertThat(result.isSuccess()).isTrue();
        verify(categoryRepository).create(any(CapabilityCategory.class));
    }

    @Test
    @DisplayName("更新分类：父节点不能选择自身或子孙")
    void updateCategoryRejectsCycle() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        CapabilityCategory selfParent = category(1L, "root-cat", 1L);
        var selfResult = controller.updateCategory(1L, selfParent);
        assertThat(selfResult.isSuccess()).isFalse();

        CapabilityCategory descendantParent = category(1L, "root-cat", 2L);
        var cycleResult = controller.updateCategory(1L, descendantParent);
        assertThat(cycleResult.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("更新分类：合法变更写入库")
    void updateCategoryPersists() {
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        CapabilityCategory patch = category(2L, "child-cat", null);
        patch.setName("改名分类");
        var result = controller.updateCategory(2L, patch);

        assertThat(result.isSuccess()).isTrue();
        ArgumentCaptor<CapabilityCategory> captor = ArgumentCaptor.forClass(CapabilityCategory.class);
        verify(categoryRepository).update(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("改名分类");
        assertThat(captor.getValue().getParentId()).isNull();
    }

    @Test
    @DisplayName("删除分类：存在子节点或挂载能力时拒绝")
    void deleteCategoryValidations() {
        CapabilitySpec mounted = spec("cap-a");
        mounted.setCategory("root-cat");
        when(catalog.list()).thenReturn(List.of(mounted));
        when(categoryRepository.findAll()).thenReturn(List.of(
                category(1L, "root-cat", null),
                category(2L, "child-cat", 1L)));

        var childResult = controller.deleteCategory(1L);
        assertThat(childResult.isSuccess()).isFalse();

        when(catalog.list()).thenReturn(List.of(spec("cap-b")));
        var mountedResult = controller.deleteCategory(1L);
        assertThat(mountedResult.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("删除分类：空节点允许删除")
    void deleteCategoryPersists() {
        when(catalog.list()).thenReturn(List.of(spec("cap-b")));
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "root-cat", null)));

        var result = controller.deleteCategory(1L);

        assertThat(result.isSuccess()).isTrue();
        verify(categoryRepository).deleteById(1L);
    }

    @Test
    @DisplayName("详情查询：不存在时返回失败")
    void getReturnsFailureForMissing() {
        when(catalog.get("ghost")).thenReturn(null);

        var result = controller.get("ghost");

        assertThat(result.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("调用记录：按开始时间倒序分页并返回总数")
    void callsPagesByStartedAtDesc() {
        CapabilityCallRecord older = new CapabilityCallRecord();
        older.setId("older");
        older.setStartedAt(java.time.Instant.parse("2026-09-24T01:00:00Z"));
        CapabilityCallRecord newer = new CapabilityCallRecord();
        newer.setId("newer");
        newer.setStartedAt(java.time.Instant.parse("2026-09-24T02:00:00Z"));
        when(callRepository.findByCapability("cap-a")).thenReturn(List.of(older, newer));

        Map<String, Object> page = controller.calls("cap-a", 1, 10).getData();

        assertThat(page.get("total")).isEqualTo(2);
        assertThat(((List<CapabilityCallRecord>) page.get("list")).get(0).getId()).isEqualTo("newer");
    }

    @Test
    @DisplayName("调用记录：开始时间为空时排在末尾")
    void callsPutsNullStartedAtLast() {
        CapabilityCallRecord noTime = new CapabilityCallRecord();
        noTime.setId("no-time");
        CapabilityCallRecord timed = new CapabilityCallRecord();
        timed.setId("timed");
        timed.setStartedAt(java.time.Instant.parse("2026-09-24T01:00:00Z"));
        when(callRepository.findByCapability("cap-a")).thenReturn(List.of(noTime, timed));

        Map<String, Object> page = controller.calls("cap-a", 1, 10).getData();

        assertThat(((List<CapabilityCallRecord>) page.get("list")).get(1).getId()).isEqualTo("no-time");
    }

    private CapabilitySpec spec(String code) {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode(code);
        spec.setName(code);
        return spec;
    }

    private CapabilityCategory category(Long id, String code, Long parentId) {
        CapabilityCategory category = new CapabilityCategory();
        category.setId(id);
        category.setCode(code);
        category.setName(code);
        category.setParentId(parentId);
        category.setSortNum(0);
        return category;
    }
}
