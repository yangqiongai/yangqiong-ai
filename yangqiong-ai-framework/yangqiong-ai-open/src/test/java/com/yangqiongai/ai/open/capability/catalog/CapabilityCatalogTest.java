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
package com.yangqiongai.ai.open.capability.catalog;

import com.yangqiongai.ai.open.capability.spec.CapabilitySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力目录
 * @author yangqiong
 */
class CapabilityCatalogTest {

    private CapabilityCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new CapabilityCatalog();
    }

    @Test
    void testRegisterAndGet() {
        CapabilitySpec spec = createSpec("test-code", "测试能力", "category-a");
        catalog.register(spec);

        CapabilitySpec result = catalog.get("test-code");
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("test-code");
        assertThat(result.getName()).isEqualTo("测试能力");
    }

    @Test
    void testRegisterNullSpec() {
        catalog.register(null);
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void testRegisterNullCode() {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setName("无编码能力");
        catalog.register(spec);
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void testListAllCapabilities() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));
        catalog.register(createSpec("code-2", "能力2", "cat-b"));
        catalog.register(createSpec("code-3", "能力3", "cat-a"));

        List<CapabilitySpec> result = catalog.list();
        assertThat(result).hasSize(3);
    }

    @Test
    void testListByCategory() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));
        catalog.register(createSpec("code-2", "能力2", "cat-b"));
        catalog.register(createSpec("code-3", "能力3", "cat-a"));

        List<CapabilitySpec> result = catalog.listByCategory("cat-a");
        assertThat(result).hasSize(2)
                .extracting(CapabilitySpec::getCode)
                .containsExactlyInAnyOrder("code-1", "code-3");
    }

    @Test
    void testListByCategoryWithNullCategory() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        List<CapabilitySpec> result = catalog.listByCategory(null);
        assertThat(result).isEmpty();
    }

    @Test
    void testListByCategoryWithNonExistentCategory() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        List<CapabilitySpec> result = catalog.listByCategory("non-existent");
        assertThat(result).isEmpty();
    }

    @Test
    void testIsEnabled() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        assertThat(catalog.isEnabled("code-1")).isTrue();
        assertThat(catalog.isEnabled("non-existent")).isFalse();
    }

    @Test
    void testGetReturnsNullForDisabledCapability() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setEnabled(false);
        catalog.mergeDatabaseDefinition(entity);

        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNull();
        assertThat(catalog.isEnabled("code-1")).isFalse();
    }

    @Test
    void testMergeDatabaseDefinitionAddsNewCapability() {
        String json = "{\"code\":\"db-code\",\"name\":\"数据库新增能力\",\"category\":\"cat-db\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("db-code");
        entity.setDefinitionJson(json);

        catalog.mergeDatabaseDefinition(entity);

        CapabilitySpec result = catalog.get("db-code");
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo("db-code");
        assertThat(result.getName()).isEqualTo("数据库新增能力");
    }

    @Test
    void testMergeDatabaseDefinitionOverridesYaml() {
        catalog.register(createSpec("code-1", "原始名称", "cat-a"));

        String json = "{\"code\":\"code-1\",\"name\":\"数据库覆盖的名称\",\"category\":\"cat-b\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setDefinitionJson(json);

        catalog.mergeDatabaseDefinition(entity);

        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("数据库覆盖的名称");
        assertThat(result.getCategory()).isEqualTo("cat-b");
    }

    @Test
    void testMergeDatabaseDefinitionWithSchemaFields() {
        String json = "{\"code\":\"db-code-full\",\"name\":\"完整能力\",\"category\":\"cat-db\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("db-code-full");
        entity.setDefinitionJson(json);
        entity.setInputSchema("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        entity.setOutputSchema("{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\"}}}");
        entity.setPromptTemplate("请生成关于${name}的报告");

        catalog.mergeDatabaseDefinition(entity);

        CapabilitySpec result = catalog.get("db-code-full");
        assertThat(result).isNotNull();
        assertThat(result.getInputSchemaContent()).isEqualTo("{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}");
        assertThat(result.getOutputSchemaContent()).isEqualTo("{\"type\":\"object\",\"properties\":{\"result\":{\"type\":\"string\"}}}");
        assertThat(result.getPromptTemplateContent()).isEqualTo("请生成关于${name}的报告");
    }

    @Test
    void testMergeDatabaseDefinitionWithInvalidJson() {
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("db-code");
        entity.setDefinitionJson("{invalid json}");

        catalog.mergeDatabaseDefinition(entity);

        assertThat(catalog.get("db-code")).isNull();
    }

    @Test
    void testMergeDatabaseDefinitionDisablesCapability() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setEnabled(false);
        catalog.mergeDatabaseDefinition(entity);

        assertThat(catalog.get("code-1")).isNull();
        assertThat(catalog.isEnabled("code-1")).isFalse();
    }

    @Test
    void testMergeDatabaseDefinitionWithEnabledTrue() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setEnabled(true);
        catalog.mergeDatabaseDefinition(entity);

        assertThat(catalog.get("code-1")).isNotNull();
        assertThat(catalog.isEnabled("code-1")).isTrue();
    }

    @Test
    void testMergeDatabaseDefinitionWithNullEntity() {
        catalog.mergeDatabaseDefinition(null);
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void testMergeDatabaseDefinitionWithNullCode() {
        CapabilityDefinition entity = new CapabilityDefinition();
        catalog.mergeDatabaseDefinition(entity);
        // should not throw exception
        assertThat(catalog.list()).isEmpty();
    }

    @Test
    void testListDoesNotIncludeDisabled() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));
        catalog.register(createSpec("code-2", "能力2", "cat-b"));

        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setEnabled(false);
        catalog.mergeDatabaseDefinition(entity);

        List<CapabilitySpec> result = catalog.list();
        assertThat(result).hasSize(1)
                .extracting(CapabilitySpec::getCode)
                .containsExactly("code-2");
    }

    @Test
    void testMergeDatabaseDefinitionWithDefinitionJsonAndEnabledFalse() {
        catalog.register(createSpec("code-1", "能力1", "cat-a"));

        // enabled=false 优先级高于 definitionJson
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setDefinitionJson("{\"code\":\"code-1\",\"name\":\"新名称\"}");
        entity.setEnabled(false);
        catalog.mergeDatabaseDefinition(entity);

        assertThat(catalog.get("code-1")).isNull();
        assertThat(catalog.isEnabled("code-1")).isFalse();
    }

    // ======================== 版本比较测试 ========================

    @Test
    void testCompareVersions() {
        assertThat(CapabilityCatalog.compareVersions("1.0.0", "1.0.0")).isEqualTo(0);
        assertThat(CapabilityCatalog.compareVersions("2.0.0", "1.0.0")).isGreaterThan(0);
        assertThat(CapabilityCatalog.compareVersions("1.0.0", "2.0.0")).isLessThan(0);
        assertThat(CapabilityCatalog.compareVersions("1.1.0", "1.0.0")).isGreaterThan(0);
        assertThat(CapabilityCatalog.compareVersions("1.0.1", "1.0.0")).isGreaterThan(0);
        assertThat(CapabilityCatalog.compareVersions("1.0.0", "1.0.1")).isLessThan(0);
        assertThat(CapabilityCatalog.compareVersions("10.0.0", "9.0.0")).isGreaterThan(0);
        assertThat(CapabilityCatalog.compareVersions("1.0.0.0", "1.0.0")).isEqualTo(0);
    }

    @Test
    void testDbVersionHigherThanYaml_overwrites() {
        catalog.register(createSpec("code-1", "YAML名称", "cat-a", "1.0.0"));

        String json = "{\"code\":\"code-1\",\"name\":\"DB名称\",\"category\":\"cat-b\",\"version\":\"2.0.0\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setDefinitionJson(json);
        catalog.mergeDatabaseDefinition(entity);

        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("DB名称");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void testDbVersionLowerThanYaml_skips() {
        catalog.register(createSpec("code-1", "YAML名称", "cat-a", "2.0.0"));

        String json = "{\"code\":\"code-1\",\"name\":\"DB名称\",\"category\":\"cat-b\",\"version\":\"1.0.0\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setDefinitionJson(json);
        catalog.mergeDatabaseDefinition(entity);

        // 数据库版本低，应保留YAML配置
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("YAML名称");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void testDbVersionEqualsYaml_overwrites() {
        catalog.register(createSpec("code-1", "YAML名称", "cat-a", "1.0.0"));

        String json = "{\"code\":\"code-1\",\"name\":\"DB名称\",\"category\":\"cat-b\",\"version\":\"1.0.0\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setDefinitionJson(json);
        catalog.mergeDatabaseDefinition(entity);

        // 同名同版本，数据库覆盖
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("DB名称");
    }

    @Test
    void testDbVersionNullYamlHasVersion_skips() {
        catalog.register(createSpec("code-1", "YAML名称", "cat-a", "1.0.0"));

        // 数据库definitionJson中无version字段
        String json = "{\"code\":\"code-1\",\"name\":\"DB名称\",\"category\":\"cat-b\"}";
        CapabilityDefinition entity = new CapabilityDefinition();
        entity.setCode("code-1");
        entity.setDefinitionJson(json);
        catalog.mergeDatabaseDefinition(entity);

        // 数据库无版本号，应保留有版本的YAML配置
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("YAML名称");
    }

    @Test
    void testSameCodeMultipleDbVersions_highestWins() {
        catalog.register(createSpec("code-1", "YAML名称", "cat-a", "1.0.0"));

        // 先注册DB v1.0.0（同名同版本，覆盖）
        String json1 = "{\"code\":\"code-1\",\"name\":\"DB-v1\",\"version\":\"1.0.0\"}";
        CapabilityDefinition entity1 = new CapabilityDefinition();
        entity1.setCode("code-1");
        entity1.setDefinitionJson(json1);
        catalog.mergeDatabaseDefinition(entity1);

        // 再注册DB v2.0.0（更高版本，覆盖）
        String json2 = "{\"code\":\"code-1\",\"name\":\"DB-v2\",\"version\":\"2.0.0\"}";
        CapabilityDefinition entity2 = new CapabilityDefinition();
        entity2.setCode("code-1");
        entity2.setDefinitionJson(json2);
        catalog.mergeDatabaseDefinition(entity2);

        // 最终应生效最高版本
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("DB-v2");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void testSameCodeMultipleDbVersions_lowerAfterHigher_keepsHighest() {
        catalog.register(createSpec("code-1", "YAML名称", "cat-a", "1.0.0"));

        // 先注册DB v2.0.0
        String json1 = "{\"code\":\"code-1\",\"name\":\"DB-v2\",\"version\":\"2.0.0\"}";
        CapabilityDefinition entity1 = new CapabilityDefinition();
        entity1.setCode("code-1");
        entity1.setDefinitionJson(json1);
        catalog.mergeDatabaseDefinition(entity1);

        // 再注册DB v1.0.0（更低版本，应跳过）
        String json2 = "{\"code\":\"code-1\",\"name\":\"DB-v1\",\"version\":\"1.0.0\"}";
        CapabilityDefinition entity2 = new CapabilityDefinition();
        entity2.setCode("code-1");
        entity2.setDefinitionJson(json2);
        catalog.mergeDatabaseDefinition(entity2);

        // 最终应保留v2.0.0
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("DB-v2");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    // ======================== YAML同名版本比较测试 ========================

    @Test
    void testRegisterHigherVersionOverwrites() {
        catalog.register(createSpec("code-1", "v1", "cat-a", "1.0.0"));
        catalog.register(createSpec("code-1", "v2", "cat-a", "2.0.0"));

        CapabilitySpec result = catalog.get("code-1");
        assertThat(result.getName()).isEqualTo("v2");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void testRegisterLowerVersionSkipped() {
        catalog.register(createSpec("code-1", "v2", "cat-a", "2.0.0"));
        catalog.register(createSpec("code-1", "v1", "cat-a", "1.0.0"));

        // 低版本应跳过，保留v2
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result.getName()).isEqualTo("v2");
        assertThat(result.getVersion()).isEqualTo("2.0.0");
    }

    @Test
    void testRegisterSameVersionOverwrites() {
        catalog.register(createSpec("code-1", "v1-original", "cat-a", "1.0.0"));
        catalog.register(createSpec("code-1", "v1-new", "cat-a", "1.0.0"));

        // 同名同版本，后注册的覆盖
        CapabilitySpec result = catalog.get("code-1");
        assertThat(result.getName()).isEqualTo("v1-new");
        assertThat(result.getVersion()).isEqualTo("1.0.0");
    }

    @Test
    void testBumpPatchVersion() {
        // 常规三段版本递增补丁位
        assertThat(CapabilityCatalog.bumpPatchVersion("1.2.3")).isEqualTo("1.2.4");
        // 两段版本同样递增末位
        assertThat(CapabilityCatalog.bumpPatchVersion("1.2")).isEqualTo("1.3");
        // 空版本回退默认1.0.0
        assertThat(CapabilityCatalog.bumpPatchVersion(null)).isEqualTo("1.0.0");
        assertThat(CapabilityCatalog.bumpPatchVersion("")).isEqualTo("1.0.0");
        assertThat(CapabilityCatalog.bumpPatchVersion("  ")).isEqualTo("1.0.0");
        // 末段为数字时递增末位（v1.2 → v1.3）
        assertThat(CapabilityCatalog.bumpPatchVersion("v1.2")).isEqualTo("v1.3");
        // 末段非数字时追加.1
        assertThat(CapabilityCatalog.bumpPatchVersion("v1.x")).isEqualTo("v1.x.1");
        assertThat(CapabilityCatalog.bumpPatchVersion("beta")).isEqualTo("beta.1");
        // 补丁位进位不受位数限制
        assertThat(CapabilityCatalog.bumpPatchVersion("2.0.9")).isEqualTo("2.0.10");
    }

    private CapabilitySpec createSpec(String code, String name, String category) {
        CapabilitySpec spec = new CapabilitySpec();
        spec.setCode(code);
        spec.setName(name);
        spec.setCategory(category);
        return spec;
    }

    private CapabilitySpec createSpec(String code, String name, String category, String version) {
        CapabilitySpec spec = createSpec(code, name, category);
        spec.setVersion(version);
        return spec;
    }
}