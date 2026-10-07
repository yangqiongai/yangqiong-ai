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
package com.yangqiongai.ai.memory.agentmemory.service;

import com.yangqiongai.ai.memory.agentmemory.model.OrgContextInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.OrgContextRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 组织上下文记忆单元测试
 * @author yangqiong
 */
@DisplayName("组织上下文记忆单元测试")
class OrgContextServiceTest {

    @Mock
    private OrgContextRepository orgContextRepository;

    private OrgContextService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new OrgContextService();
        injectField("orgContextRepository", orgContextRepository);
        injectField("injectTokenBudget", 256);
    }

    @Test
    @DisplayName("创建时类型不合法抛出异常")
    void create_invalidType_throws() {
        OrgContextInfo context = context("INVALID", "术语", "目标");

        assertThatThrownBy(() -> service.create(context))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("类型不合法");
    }

    @Test
    @DisplayName("创建时术语或目标术语为空抛出异常")
    void create_blankTerm_throws() {
        assertThatThrownBy(() -> service.create(context(OrgContextInfo.TYPE_TERM, " ", "目标")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(context(OrgContextInfo.TYPE_TERM, "术语", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("创建无冲突上下文默认启用并落库")
    void create_noConflict_enabledByDefault() {
        when(orgContextRepository.findByTypeAndTerm(OrgContextInfo.TYPE_TERM, "研发部"))
                .thenReturn(List.of());
        when(orgContextRepository.insert(any(OrgContextInfo.class))).thenReturn(10L);

        OrgContextInfo created = service.create(context(OrgContextInfo.TYPE_TERM, "研发部", "研究与开发部"));

        assertThat(created.getId()).isEqualTo(10L);
        assertThat(created.getEnabled()).isEqualTo(1);
        assertThat(created.getConflictFlag()).isZero();
        assertThat(created.getCreateTime()).isNotNull();
    }

    @Test
    @DisplayName("创建与已有记录目标不一致时双方标记冲突")
    void create_targetMismatch_bothMarkedConflict() {
        OrgContextInfo existing = context(OrgContextInfo.TYPE_ALIAS, "研发", "研究与开发部");
        existing.setId(2L);
        when(orgContextRepository.findByTypeAndTerm(OrgContextInfo.TYPE_ALIAS, "研发"))
                .thenReturn(List.of(existing));
        when(orgContextRepository.insert(any(OrgContextInfo.class))).thenReturn(11L);

        OrgContextInfo created = service.create(context(OrgContextInfo.TYPE_ALIAS, "研发", "研发中心"));

        assertThat(created.getConflictFlag()).isEqualTo(1);
        ArgumentCaptor<OrgContextInfo> captor = ArgumentCaptor.forClass(OrgContextInfo.class);
        verify(orgContextRepository).update(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(2L);
        assertThat(captor.getValue().getConflictFlag()).isEqualTo(1);
    }

    @Test
    @DisplayName("自身记录不参与冲突判定")
    void create_sameRecordExcludedFromConflict() {
        OrgContextInfo self = context(OrgContextInfo.TYPE_TERM, "研发部", "研究与开发部");
        self.setId(3L);
        when(orgContextRepository.findByTypeAndTerm(OrgContextInfo.TYPE_TERM, "研发部"))
                .thenReturn(List.of(self));
        when(orgContextRepository.insert(any(OrgContextInfo.class))).thenReturn(3L);

        OrgContextInfo created = service.create(self);

        assertThat(created.getConflictFlag()).isZero();
        verify(orgContextRepository, never()).update(any());
    }

    @Test
    @DisplayName("更新时重算冲突标记")
    void update_recomputesConflict() {
        OrgContextInfo updating = context(OrgContextInfo.TYPE_TERM, "研发部", "新目标");
        updating.setId(5L);
        when(orgContextRepository.findByTypeAndTerm(OrgContextInfo.TYPE_TERM, "研发部"))
                .thenReturn(List.of());

        service.update(updating);

        assertThat(updating.getConflictFlag()).isZero();
        assertThat(updating.getUpdateTime()).isNotNull();
        verify(orgContextRepository).update(updating);
    }

    @Test
    @DisplayName("别名归一跳过冲突记录返回无冲突映射")
    void resolveAlias_skipsConflict() {
        OrgContextInfo conflicted = context(OrgContextInfo.TYPE_ALIAS, "研发", "旧目标");
        conflicted.setConflictFlag(1);
        OrgContextInfo normal = context(OrgContextInfo.TYPE_ALIAS, "研发", "研究与开发部");
        normal.setConflictFlag(0);
        when(orgContextRepository.findEnabledByAlias("研发")).thenReturn(List.of(conflicted, normal));

        assertThat(service.resolveAlias("研发")).isEqualTo("研究与开发部");
    }

    @Test
    @DisplayName("别名归一无匹配或空白入参返回null")
    void resolveAlias_noMatch_returnsNull() {
        assertThat(service.resolveAlias("  ")).isNull();
        assertThat(service.resolveAlias(null)).isNull();
        when(orgContextRepository.findEnabledByAlias("未知")).thenReturn(List.of());
        assertThat(service.resolveAlias("未知")).isNull();
    }

    @Test
    @DisplayName("CSV导入统计插入冲突与跳过条数")
    void importCsv_summarizesLines() {
        when(orgContextRepository.findByTypeAndTerm(anyString(), anyString())).thenReturn(List.of());
        when(orgContextRepository.insert(any(OrgContextInfo.class))).thenReturn(1L);

        String csv = "# 注释行\n"
                + "TERM,研发部,研究与开发部,标准组织术语\n"
                + "\n"
                + "ALIAS,研发,研究与开发部\n"
                + "INVALID,x,y\n"
                + "TERM,,空术语\n"
                + "TERM,只有两列\n";

        OrgContextService.ImportSummary summary = service.importCsv(csv);

        assertThat(summary.getInserted()).isEqualTo(2);
        assertThat(summary.getSkipped()).isEqualTo(3);
        assertThat(summary.getConflicts()).isZero();
    }

    @Test
    @DisplayName("CSV导入空白内容返回空摘要")
    void importCsv_blank_returnsEmptySummary() {
        assertThat(service.importCsv(null).getInserted()).isZero();
        assertThat(service.importCsv("   ").getSkipped()).isZero();
    }

    @Test
    @DisplayName("CSV导入行内类型自动转大写")
    void importCsv_uppercasesType() {
        when(orgContextRepository.findByTypeAndTerm(anyString(), anyString())).thenReturn(List.of());
        when(orgContextRepository.insert(any(OrgContextInfo.class))).thenReturn(1L);

        service.importCsv("term,研发部,研究与开发部");

        ArgumentCaptor<OrgContextInfo> captor = ArgumentCaptor.forClass(OrgContextInfo.class);
        verify(orgContextRepository).insert(captor.capture());
        assertThat(captor.getValue().getContextType()).isEqualTo(OrgContextInfo.TYPE_TERM);
    }

    @Test
    @DisplayName("注入块包含术语与隶属关系行")
    void buildInjectionBlock_rendersTermAndLineage() {
        OrgContextInfo term = context(OrgContextInfo.TYPE_TERM, "研发部", "研究与开发部");
        term.setEnabled(1);
        OrgContextInfo lineage = context(OrgContextInfo.TYPE_LINEAGE, "研发部", "技术中心");
        lineage.setEnabled(1);
        when(orgContextRepository.findEnabledByType(OrgContextInfo.TYPE_TERM)).thenReturn(List.of(term));
        when(orgContextRepository.findEnabledByType(OrgContextInfo.TYPE_LINEAGE)).thenReturn(List.of(lineage));

        String block = service.buildInjectionBlock();

        assertThat(block).startsWith("<org_context>");
        assertThat(block).endsWith("</org_context>");
        assertThat(block).contains("组织上下文");
        assertThat(block).contains("术语[研发部]：研究与开发部");
        assertThat(block).contains("研发部 隶属于 技术中心");
    }

    @Test
    @DisplayName("注入块受Token预算约束截断")
    void buildInjectionBlock_respectsTokenBudget() {
        OrgContextService tight = new OrgContextService();
        inject(tight, "orgContextRepository", orgContextRepository);
        inject(tight, "injectTokenBudget", 4);
        OrgContextInfo term = context(OrgContextInfo.TYPE_TERM, "研发部", "研究与开发部");
        term.setEnabled(1);
        when(orgContextRepository.findEnabledByType(OrgContextInfo.TYPE_TERM)).thenReturn(List.of(term));
        when(orgContextRepository.findEnabledByType(OrgContextInfo.TYPE_LINEAGE)).thenReturn(List.of());

        String block = tight.buildInjectionBlock();

        assertThat(block).doesNotContain("术语[研发部]");
    }

    @Test
    @DisplayName("无可用条目时注入块返回空串")
    void buildInjectionBlock_noEntries_returnsEmpty() {
        when(orgContextRepository.findEnabledByType(OrgContextInfo.TYPE_TERM)).thenReturn(List.of());
        when(orgContextRepository.findEnabledByType(OrgContextInfo.TYPE_LINEAGE)).thenReturn(List.of());

        assertThat(service.buildInjectionBlock()).isEmpty();
    }

    @Test
    @DisplayName("删除与查询委托仓储")
    void crud_delegates() {
        OrgContextInfo stored = context(OrgContextInfo.TYPE_TERM, "研发部", "研究与开发部");
        when(orgContextRepository.selectById(1L)).thenReturn(stored);
        when(orgContextRepository.findAll()).thenReturn(List.of(stored));

        assertThat(service.getById(1L)).isSameAs(stored);
        assertThat(service.listAll()).hasSize(1);

        service.delete(1L);
        verify(orgContextRepository).deleteById(1L);
    }

    private OrgContextInfo context(String type, String term, String targetTerm) {
        OrgContextInfo context = new OrgContextInfo();
        context.setContextType(type);
        context.setTerm(term);
        context.setTargetTerm(targetTerm);
        return context;
    }

    private void injectField(String name, Object value) {
        inject(service, name, value);
    }

    private void inject(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("字段注入失败: " + name, e);
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
        }
        throw new NoSuchFieldException(name);
    }
}
