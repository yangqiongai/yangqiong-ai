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

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.yangqiongai.ai.common.scope.CollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultCollectionNameResolver;
import com.yangqiongai.ai.common.scope.DefaultFeatureGuard;
import com.yangqiongai.ai.common.scope.DefaultObjectKeyResolver;
import com.yangqiongai.ai.common.scope.DefaultPlanLimitGuard;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.scope.ObjectKeyResolver;
import com.yangqiongai.ai.common.scope.PlanLimitGuard;
import com.yangqiongai.ai.platform.bss.scope.auth.AuthAwareTaskDecorator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.TaskDecorator;

/**
 * 作用域通用自动配置
 * @author yangqiong
 */
@AutoConfiguration
@EnableConfigurationProperties(ScopeProperties.class)
public class CommonScopeAutoConfiguration {

    /**
     * 默认集合名称解析器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public CollectionNameResolver collectionNameResolver() {
        return new DefaultCollectionNameResolver();
    }

    /**
     * 默认对象Key解析器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectKeyResolver objectKeyResolver() {
        return new DefaultObjectKeyResolver();
    }

    /**
     * 默认功能集守卫
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public FeatureGuard featureGuard() {
        return new DefaultFeatureGuard();
    }

    /**
     * 默认套餐数量限制守卫
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public PlanLimitGuard planLimitGuard() {
        return new DefaultPlanLimitGuard();
    }

    /**
     * 默认配额校验器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public QuotaChecker quotaChecker() {
        return new DefaultQuotaChecker();
    }

    /**
     * 默认管理员上下文
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public AdminContext adminContext() {
        return new DefaultAdminContext();
    }

    /**
     * 作用域上下文异步任务装饰器（鉴权未启用时使用）
     * <p>
     * Spring Boot 自动配置的 applicationTaskExecutor 会自动拾取容器中的 TaskDecorator Bean，
     * 从而在 @Async 方法执行时跨线程传递 scopeId。
     * </p>
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    @ConditionalOnProperty(name = "ai.system.auth.enabled", havingValue = "false", matchIfMissing = true)
    public TaskDecorator scopeAwareTaskDecorator() {
        return new ScopeAwareTaskDecorator();
    }

    /**
     * 鉴权上下文异步任务装饰器（鉴权启用时替代作用域装饰器，同时传播 scopeId 与 AuthContext）
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(TaskDecorator.class)
    @ConditionalOnProperty(name = "ai.system.auth.enabled", havingValue = "true")
    public TaskDecorator authAwareTaskDecorator() {
        return new AuthAwareTaskDecorator();
    }

    /**
     * 审计字段自动填充处理器
     * @return
     */
    @Bean
    @ConditionalOnMissingBean
    public MetaObjectHandler aiMetaObjectHandler() {
        return new AiMetaObjectHandler();
    }
}
