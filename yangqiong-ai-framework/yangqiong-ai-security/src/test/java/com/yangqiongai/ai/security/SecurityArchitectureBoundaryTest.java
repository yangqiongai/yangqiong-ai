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
package com.yangqiongai.ai.security;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 安全模块架构边界测试
 * @author yangqiong
 */
@AnalyzeClasses(packages = "com.yangqiongai.ai.security", importOptions = ImportOption.DoNotIncludeTests.class)
class SecurityArchitectureBoundaryTest {

    /**
     * 社区安全包禁止依赖商业包（开源/商业切分边界）
     */
    @ArchTest
    static final ArchRule communityMustNotDependOnEnterprise = noClasses()
            .that().resideInAPackage("com.yangqiongai.ai.security..")
            .should().dependOnClassesThat().resideInAnyPackage("..enterprise..")
            .because("社区版不得引用商业实现，规则中心与触发审计只能经 spi 包契约接入");
}
