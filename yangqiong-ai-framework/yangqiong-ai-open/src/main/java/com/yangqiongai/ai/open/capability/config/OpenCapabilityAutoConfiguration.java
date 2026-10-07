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
package com.yangqiongai.ai.open.capability.config;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCatalog;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinition;
import com.yangqiongai.ai.open.capability.catalog.CapabilityCategoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionHistoryRepository;
import com.yangqiongai.ai.open.capability.catalog.CapabilityDefinitionRepository;
import com.yangqiongai.ai.open.capability.controller.OpenCapabilityAdminController;
import com.yangqiongai.ai.open.capability.controller.OpenCapabilityController;
import com.yangqiongai.ai.open.capability.context.DataContextHub;
import com.yangqiongai.ai.open.capability.context.DataContextRepository;
import com.yangqiongai.ai.open.capability.engine.CapabilityEngine;
import com.yangqiongai.ai.open.capability.engine.CapabilityInvoker;
import com.yangqiongai.ai.open.capability.guard.JsonSchemaValidator;
import com.yangqiongai.ai.open.capability.guard.OutputRepairer;
import com.yangqiongai.ai.open.capability.guard.OutputSchemaGuard;
import com.yangqiongai.ai.open.capability.ingest.CapabilityDocumentIngestPort;
import com.yangqiongai.ai.open.capability.template.PromptTemplateEngine;
import com.yangqiongai.ai.open.capability.trace.CapabilityCallRepository;
import com.yangqiongai.ai.open.capability.trace.RequestDeduplicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 开放能力层自动配置
 * <p>
 * 当 ai.agent.open.capability.enabled=true 时自动装配开放能力层所有组件。
 * </p>
 * @author yangqiong
 */
@AutoConfiguration
@ConditionalOnClass(CapabilityEngine.class)
@ConditionalOnProperty(prefix = "ai.agent.open.capability", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(OpenCapabilityProperties.class)
public class OpenCapabilityAutoConfiguration {

    private final OpenCapabilityProperties properties;

    public OpenCapabilityAutoConfiguration(OpenCapabilityProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityCatalog capabilityCatalog(CapabilityDefinitionRepository definitionStore) {
        CapabilityCatalog catalog = new CapabilityCatalog();
        // 合并数据库定义（能力定义以数据库为唯一来源）
        for (CapabilityDefinition entity : definitionStore.findAll()) {
            catalog.mergeDatabaseDefinition(entity);
        }
        return catalog;
    }

    @Bean
    @ConditionalOnMissingBean
    public DataContextHub dataContextHub(DataContextRepository store) {
        return new DataContextHub(store, properties.getDataContextDefaultTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public JsonSchemaValidator jsonSchemaValidator() {
        return new JsonSchemaValidator();
    }

    @Bean
    @ConditionalOnMissingBean
    public OutputRepairer outputRepairer(AgentEngine agentEngine) {
        return new OutputRepairer(agentEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public OutputSchemaGuard outputSchemaGuard(JsonSchemaValidator validator,
                                                  OutputRepairer repairer) {
        return new OutputSchemaGuard(validator, repairer);
    }

    @Bean
    @ConditionalOnMissingBean
    public PromptTemplateEngine promptTemplateEngine() {
        return new PromptTemplateEngine();
    }

    @Bean
    @ConditionalOnMissingBean
    public RequestDeduplicator requestDeduplicator() {
        return new RequestDeduplicator(properties.getDedupTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityInvoker capabilityInvoker(AgentEngine agentEngine) {
        return new CapabilityInvoker(agentEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public CapabilityEngine capabilityEngine(CapabilityCatalog catalog,
                                               CapabilityInvoker invoker,
                                               DataContextHub dataContextHub,
                                               OutputSchemaGuard schemaGuard,
                                               JsonSchemaValidator jsonValidator,
                                               PromptTemplateEngine templateEngine,
                                               RequestDeduplicator deduplicator,
                                               CapabilityCallRepository callRepository) {
        return new CapabilityEngine(catalog, invoker, dataContextHub,
                schemaGuard, jsonValidator, templateEngine, deduplicator, callRepository,
                properties.getDedupTtlSeconds());
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenCapabilityController openCapabilityController(CapabilityCatalog catalog,
                                                               DataContextHub dataContextHub,
                                                               CapabilityEngine engine,
                                                               ObjectProvider<CapabilityDocumentIngestPort> ingestPortProvider) {
        return new OpenCapabilityController(catalog, dataContextHub, engine, ingestPortProvider);
    }

    @Bean
    @ConditionalOnMissingBean
    public OpenCapabilityAdminController openCapabilityAdminController(CapabilityCatalog catalog,
                                                                        CapabilityDefinitionRepository definitionRepository,
                                                                        CapabilityCategoryRepository categoryRepository,
                                                                        CapabilityCallRepository callRepository,
                                                                        CapabilityDefinitionHistoryRepository historyRepository) {
        return new OpenCapabilityAdminController(catalog, definitionRepository, categoryRepository, callRepository,
                historyRepository);
    }
}