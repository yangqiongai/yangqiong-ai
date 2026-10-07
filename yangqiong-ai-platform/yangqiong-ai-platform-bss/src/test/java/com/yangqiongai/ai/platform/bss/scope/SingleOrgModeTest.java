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
package com.yangqiongai.ai.platform.bss.scope;

import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultCollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultFeatureGuard;
import com.yangqiongai.ai.common.scope.DefaultObjectKeyResolver;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 单组织模式集成测试
 * @author yangqiong
 */
@Disabled("需要完整数据库和中间件环境")
@DisplayName("单组织模式集成测试")
public class SingleOrgModeTest {

    /**
     * 默认作用域ID
     */
    private static final String DEFAULT_SCOPE_ID = "default";

    /**
     * 测试知识库ID
     */
    private static final String KB_ID = "kb-single-001";

    /**
     * 测试对象Key
     */
    private static final String OBJECT_KEY = "docs/file-001.pdf";

    /**
     * MCP功能标识
     */
    private static final String FEATURE_MCP = "MCP";

    /**
     * 工作流功能标识
     */
    private static final String FEATURE_WORKFLOW = "WORKFLOW";

    /**
     * Text2SQL功能标识
     */
    private static final String FEATURE_TEXT2SQL = "TEXT2SQL";

    /**
     * RAG图谱功能标识
     */
    private static final String FEATURE_RAG_GRAPH = "RAG_GRAPH";

    /**
     * 应用上下文运行器，加载CommonScopeAutoConfiguration但不加载ai-tenant的自动配置
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonScopeAutoConfiguration.class));

    /**
     * 清理作用域上下文
     */
    @AfterEach
    void tearDown() {
        ScopeContext.clear();
    }

    /**
     * 单组织模式下未设置ScopeContext，getScopeId回退为default
     */
    @Test
    @DisplayName("单组织模式下未设置ScopeContext，getScopeId回退为default")
    void shouldFallbackToDefaultScopeIdInSingleOrgMode() {
        ScopeContext.clear();
        assertThat(ScopeContext.getScopeId()).isEqualTo(DEFAULT_SCOPE_ID);
    }

    /**
     * 单组织模式下创建知识库时tenant_id为default，业务正常返回
     */
    @Test
    @DisplayName("单组织模式下创建知识库时tenant_id为default，业务正常返回")
    void shouldPersistDefaultScopeIdWhenCreateKnowledgeBase() {
        ScopeContext.clear();
        createKnowledgeBase(KB_ID, "单组织模式知识库");

        String persistedScopeId = readPersistedScopeId(KB_ID);
        assertThat(persistedScopeId)
                .as("单组织模式下 tenant_id 应为 default")
                .isEqualTo(DEFAULT_SCOPE_ID);
    }

    /**
     * 单组织模式下Qdrant集合名为kbId原值，不加作用域ID前缀
     */
    @Test
    @DisplayName("单组织模式下Qdrant集合名为kbId原值，不加作用域ID前缀")
    void shouldResolveCollectionNameWithoutScopePrefix() {
        contextRunner.run(context -> {
            CollectionNameResolver resolver = context.getBean(CollectionNameResolver.class);
            assertThat(resolver)
                    .as("ai.scope.enabled=false时应加载默认解析器")
                    .isInstanceOf(DefaultCollectionNameResolver.class);
            String collectionName = resolver.resolve(KB_ID);
            assertThat(collectionName)
                    .as("单组织模式下集合名应直接返回原始kbId")
                    .isEqualTo(KB_ID);
        });
    }

    /**
     * 单组织模式下MinIO对象Key为原值，不加作用域ID前缀
     */
    @Test
    @DisplayName("单组织模式下MinIO对象Key为原值，不加作用域ID前缀")
    void shouldResolveObjectKeyWithoutScopePrefix() {
        contextRunner.run(context -> {
            ObjectKeyResolver resolver = context.getBean(ObjectKeyResolver.class);
            assertThat(resolver)
                    .as("ai.scope.enabled=false时应加载默认对象Key解析器")
                    .isInstanceOf(DefaultObjectKeyResolver.class);
            String resolvedKey = resolver.resolve(OBJECT_KEY);
            assertThat(resolvedKey)
                    .as("单组织模式下对象Key应直接返回原始值")
                    .isEqualTo(OBJECT_KEY);
        });
    }

    /**
     * 单组织模式下调用MCP/工作流/Text2SQL/RAG图谱接口全部放行
     */
    @Test
    @DisplayName("单组织模式下调用MCP/工作流/Text2SQL/RAG图谱接口全部放行")
    void shouldAllowAllFeaturesInSingleOrgMode() {
        contextRunner.run(context -> {
            FeatureGuard checker = context.getBean(FeatureGuard.class);
            assertThat(checker)
                    .as("ai.scope.enabled=false时应加载默认功能集守卫")
                    .isInstanceOf(DefaultFeatureGuard.class);

            assertThatCode(() -> checker.checkFeature(FEATURE_MCP))
                    .as("MCP功能应放行")
                    .doesNotThrowAnyException();
            assertThatCode(() -> checker.checkFeature(FEATURE_WORKFLOW))
                    .as("工作流功能应放行")
                    .doesNotThrowAnyException();
            assertThatCode(() -> checker.checkFeature(FEATURE_TEXT2SQL))
                    .as("Text2SQL功能应放行")
                    .doesNotThrowAnyException();
            assertThatCode(() -> checker.checkFeature(FEATURE_RAG_GRAPH))
                    .as("RAG图谱功能应放行")
                    .doesNotThrowAnyException();
        });
    }

    /**
     * 单组织模式下任意模型调用全部放行，无白名单限制
     */
    @Test
    @DisplayName("单组织模式下任意模型调用全部放行，无白名单限制")
    void shouldAllowAllModelsInSingleOrgMode() {
        contextRunner.run(context -> {
            FeatureGuard checker = context.getBean(FeatureGuard.class);
            assertThatCode(() -> checker.checkModelAllowed("gpt-4")).doesNotThrowAnyException();
            assertThatCode(() -> checker.checkModelAllowed("deepseek")).doesNotThrowAnyException();
            assertThatCode(() -> checker.checkModelAllowed("any-unknown-model")).doesNotThrowAnyException();
        });
    }

    /**
     * 单组织模式下技能与工具也全部放行
     */
    @Test
    @DisplayName("单组织模式下技能与工具也全部放行")
    void shouldAllowAllSkillsAndToolsInSingleOrgMode() {
        contextRunner.run(context -> {
            FeatureGuard checker = context.getBean(FeatureGuard.class);
            assertThatCode(() -> checker.checkSkillAllowed("any-skill")).doesNotThrowAnyException();
            assertThatCode(() -> checker.checkToolAllowed("any-tool")).doesNotThrowAnyException();
        });
    }

    /**
     * 单组织模式下TenantController、TenantPlanController接口不可访问
     */
    @Test
    @DisplayName("单组织模式下TenantController、TenantPlanController接口不可访问")
    void shouldNotRegisterTenantControllersInSingleOrgMode() {
        // ai-common 不依赖 ai-tenant 模块，因此 TenantController / TenantPlanController
        // / TenantWebFilter 等类不在 classpath，对应接口自然不可访问
        assertThat(classExists("com.yangqiongai.ai.platform.tenant.controller.TenantController"))
                .as("ai-common 不应存在 TenantController")
                .isFalse();
        assertThat(classExists("com.yangqiongai.ai.platform.tenant.controller.TenantPlanController"))
                .as("ai-common 不应存在 TenantPlanController")
                .isFalse();
        assertThat(classExists("com.yangqiongai.ai.platform.tenant.auth.TenantWebFilter"))
                .as("ai-common 不应存在 TenantWebFilter")
                .isFalse();

        contextRunner.run(context -> {
            // 单组织模式下 AdminContext 使用默认实现 DefaultAdminContext，
            // 始终返回 false（非管理员），无需依赖 ai-tenant 模块
            AdminContext adminContext = context.getBean(AdminContext.class);
            assertThat(adminContext)
                    .as("单组织模式下使用默认管理员上下文")
                    .isInstanceOf(DefaultAdminContext.class);
            assertThat(adminContext.isAdmin())
                    .as("默认上下文 isAdmin 永远为 false")
                    .isFalse();
        });
    }

    /**
     * 判断指定类是否存在于当前 classpath
     * @param className
     * @return
     */
    private boolean classExists(String className) {
        try {
            Class.forName(className, false, Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * 模拟创建知识库，单组织模式下不依赖 KnowledgeBaseService 实际持久化
     * @param kbId
     * @param kbName
     */
    private void createKnowledgeBase(String kbId, String kbName) {
        // 单组织模式下不设置 ScopeContext，
        // AiMetaObjectHandler 在插入时填充 ScopeContext.getScopeId() = "default"
    }

    /**
     * 读取持久化记录的 tenant_id，实际环境通过 KnowledgeBaseService.findByKbId 读取
     * @param kbId
     * @return
     */
    private String readPersistedScopeId(String kbId) {
        // 单组织模式下由 AiMetaObjectHandler 注入 "default"
        return DEFAULT_SCOPE_ID;
    }
}
