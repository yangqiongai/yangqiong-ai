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
package com.yangqiongai.ai.security.store;

import com.yangqiongai.ai.security.spi.GuardrailRuleEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 护栏规则内存仓库单元测试
 *
 * @author yangqiong
 */
@DisplayName("InMemoryGuardrailRuleRepository 测试")
class InMemoryGuardrailRuleRepositoryTest {

    private InMemoryGuardrailRuleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryGuardrailRuleRepository();
    }

    /**
     * 构建规则对象
     * @param name
     * @param ruleType
     * @param isEnabled
     * @return
     */
    private GuardrailRuleEntity buildRule(String name, String ruleType, int isEnabled) {
        GuardrailRuleEntity rule = new GuardrailRuleEntity();
        rule.setName(name);
        rule.setRuleType(ruleType);
        rule.setIsEnabled(isEnabled);
        rule.setHookPoint("INPUT");
        return rule;
    }

    @Nested
    @DisplayName("保存与查询")
    class SaveAndQuery {

        @Test
        @DisplayName("新增规则应自动生成主键且可按名称查询")
        void shouldGenerateIdAndFindByName() {
            GuardrailRuleEntity rule = buildRule("r1", "KEYWORD", 1);
            repository.saveRule(rule);

            assertThat(rule.getId()).isNotBlank();
            GuardrailRuleEntity found = repository.findByName("r1");
            assertThat(found).isNotNull();
            assertThat(found.getRuleType()).isEqualTo("KEYWORD");
        }

        @Test
        @DisplayName("同名保存应覆盖且保留原主键")
        void shouldUpdateByIdempotentName() {
            GuardrailRuleEntity first = buildRule("r1", "KEYWORD", 1);
            repository.saveRule(first);
            String originId = first.getId();

            GuardrailRuleEntity second = buildRule("r1", "REGEX", 1);
            repository.saveRule(second);

            assertThat(second.getId()).isEqualTo(originId);
            assertThat(repository.findAll()).hasSize(1);
            assertThat(repository.findByName("r1").getRuleType()).isEqualTo("REGEX");
        }

        @Test
        @DisplayName("查询所有应返回全量规则")
        void shouldFindAll() {
            repository.saveRule(buildRule("r1", "KEYWORD", 1));
            repository.saveRule(buildRule("r2", "REGEX", 0));

            List<GuardrailRuleEntity> all = repository.findAll();
            assertThat(all).hasSize(2);
        }

        @Test
        @DisplayName("名称不存在时应返回null")
        void shouldReturnNullWhenNameMissing() {
            assertThat(repository.findByName("no-exist")).isNull();
            assertThat(repository.findByName(null)).isNull();
        }
    }

    @Nested
    @DisplayName("启用状态过滤")
    class EnabledFilter {

        @Test
        @DisplayName("findAllEnabled应只返回启用规则")
        void shouldReturnOnlyEnabledRules() {
            repository.saveRule(buildRule("r1", "KEYWORD", 1));
            repository.saveRule(buildRule("r2", "REGEX", 0));

            List<GuardrailRuleEntity> enabled = repository.findAllEnabled();
            assertThat(enabled).hasSize(1);
            assertThat(enabled.get(0).getName()).isEqualTo("r1");
        }

        @Test
        @DisplayName("findEnabledByHookPoint应按挂载点过滤且大小写不敏感")
        void shouldFilterByHookPointIgnoreCase() {
            GuardrailRuleEntity rule = buildRule("r1", "KEYWORD", 1);
            rule.setHookPoint("output");
            repository.saveRule(rule);
            repository.saveRule(buildRule("r2", "REGEX", 1));

            List<GuardrailRuleEntity> matched = repository.findEnabledByHookPoint("OUTPUT");
            assertThat(matched).hasSize(1);
            assertThat(matched.get(0).getName()).isEqualTo("r1");
        }
    }

    @Nested
    @DisplayName("启停与删除")
    class ToggleAndDelete {

        @Test
        @DisplayName("切换状态应生效")
        void shouldToggleStatus() {
            GuardrailRuleEntity rule = buildRule("r1", "KEYWORD", 1);
            repository.saveRule(rule);

            assertThat(repository.toggleStatus(rule.getId(), 0)).isTrue();
            assertThat(repository.findAllEnabled()).isEmpty();
            assertThat(repository.toggleStatus(rule.getId(), 1)).isTrue();
            assertThat(repository.findAllEnabled()).hasSize(1);
        }

        @Test
        @DisplayName("切换不存在的规则应返回false")
        void shouldReturnFalseWhenToggleMissing() {
            assertThat(repository.toggleStatus(UUID.randomUUID().toString(), 1)).isFalse();
        }

        @Test
        @DisplayName("删除规则应生效且删除不存在规则返回false")
        void shouldDeleteRule() {
            GuardrailRuleEntity rule = buildRule("r1", "KEYWORD", 1);
            repository.saveRule(rule);

            assertThat(repository.deleteByRuleId(rule.getId())).isTrue();
            assertThat(repository.findByName("r1")).isNull();
            assertThat(repository.deleteByRuleId(rule.getId())).isFalse();
        }
    }
}
