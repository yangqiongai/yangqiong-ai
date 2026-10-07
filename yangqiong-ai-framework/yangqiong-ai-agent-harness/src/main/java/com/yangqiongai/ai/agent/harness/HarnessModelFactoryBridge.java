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
package com.yangqiongai.ai.agent.harness;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.ai.agent.harness.RuntimeTypeConverter;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.agent.runtime.model.spi.ModelCredentialResolver;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.llm.event.ModelConfigChangedEvent;
import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Harness模型工厂桥接器
 * <p>
 * 将引擎的HarnessModelFactory适配为framework的AgentModelFactory，
 * 支持从数据库加载模型配置，作为配置文件的回退。
 * 解析顺序：配置文件注册表 → 数据库加载 → 缓存
 * </p>
 * @author yangqiong
 */
public class HarnessModelFactoryBridge implements AgentModelFactory {

    private static final Logger log = LoggerFactory.getLogger(HarnessModelFactoryBridge.class);

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 独立引擎模型工厂委托
     */
    private final HarnessModelFactory delegate;

    /**
     * 模型注册表，用于数据库加载后创建模型
     */
    private final AgentModelRegistry registry;

    /**
     * 模型配置属性
     */
    private final HarnessModelProperties properties;

    /**
     * 模型信息仓库（可选，用于从数据库加载模型配置）
     */
    private final ModelInfoRepository modelInfoRepository;

    /**
     * scope级模型凭据解析器
     */
    private final ModelCredentialResolver credentialResolver;

    /**
     * 功能集守卫（可选）
     */
    private final FeatureGuard featureGuard;

    /**
     * 数据库加载的模型缓存，避免重复查DB，key为scopeId:modelCode防跨租户串缓存
     */
    private final ConcurrentHashMap<String, com.yangqiongai.ai.agent.runtime.model.AgentModel> modelCache = new ConcurrentHashMap<>();

    public HarnessModelFactoryBridge(HarnessModelFactory delegate,
                                      AgentModelRegistry registry,
                                      HarnessModelProperties properties,
                                      ModelInfoRepository modelInfoRepository) {
        this(delegate, registry, properties, modelInfoRepository, null, null);
    }

    public HarnessModelFactoryBridge(HarnessModelFactory delegate,
                                      AgentModelRegistry registry,
                                      HarnessModelProperties properties,
                                      ModelInfoRepository modelInfoRepository,
                                      ModelCredentialResolver credentialResolver) {
        this(delegate, registry, properties, modelInfoRepository, credentialResolver, null);
    }

    public HarnessModelFactoryBridge(HarnessModelFactory delegate,
                                      AgentModelRegistry registry,
                                      HarnessModelProperties properties,
                                      ModelInfoRepository modelInfoRepository,
                                      ModelCredentialResolver credentialResolver,
                                      FeatureGuard featureGuard) {
        this.delegate = delegate;
        this.registry = registry;
        this.properties = properties;
        this.modelInfoRepository = modelInfoRepository;
        this.credentialResolver = credentialResolver;
        this.featureGuard = featureGuard;
    }

    /**
     * 按模型编码获取模型
     * <p>
     * 优先从数据库加载模型配置（含apiEndpoint/apiKey），
     * 数据库无配置时回退到delegate（配置文件注册表）。
     * </p>
     * @param modelCode
     * @param options
     * @return
     */
    @Override
    public com.yangqiongai.ai.agent.runtime.model.AgentModel getModel(String modelCode,
                                                                  AgentGenerateOptions options) {

        if (featureGuard != null) {
            featureGuard.checkModelAllowed(modelCode);
        }
        com.yangqiongai.agent.harness.core.model.AgentGenerateOptions harnessOptions =
                RuntimeTypeConverter.toHarness(options);
        // 数据库优先：查询ModelInfoRepository获取完整配置
        if (modelInfoRepository != null) {
            try {
                return resolveFromDatabase(modelCode, harnessOptions);
            } catch (Exception e) {
                log.debug("数据库加载模型失败, 回退到配置文件: modelCode={}", modelCode);
            }
        }
        // 回退到delegate（配置文件注册表）
        try {
            AgentModel harnessModel = delegate.getModel(modelCode, harnessOptions);
            if (harnessModel != null) {
                return new AgentHarnessModelBridge(harnessModel);
            }
        } catch (Exception e) {
            log.debug("配置文件解析失败, 无可用模型配置: modelCode={}", modelCode);
        }
        throw new IllegalArgumentException("未找到模型配置: " + modelCode
                + "（数据库和配置文件均无可用配置）");
    }

    /**
     * 按完整配置获取模型
     * <p>
     * 优先从数据库加载模型配置，数据库无配置时回退到delegate（配置文件注册表）。
     * </p>
     * @param provider
     * @param apiKey
     * @param modelCode
     * @param options
     * @return
     */
    @Override
    public com.yangqiongai.ai.agent.runtime.model.AgentModel getModelByConfig(String provider, String apiKey,
                                                                          String modelCode,
                                                                          AgentGenerateOptions options) {
        if (featureGuard != null) {
            featureGuard.checkModelAllowed(modelCode);
        }
        com.yangqiongai.agent.harness.core.model.AgentGenerateOptions harnessOptions =
                RuntimeTypeConverter.toHarness(options);
        // 数据库优先
        if (modelInfoRepository != null) {
            try {
                return resolveFromDatabase(modelCode, harnessOptions);
            } catch (Exception e) {
                log.debug("数据库加载模型失败, 回退到配置文件: modelCode={}", modelCode);
            }
        }
        // 回退到delegate（配置文件注册表）
        try {
            AgentModel harnessModel = delegate.getModelByConfig(provider, apiKey, modelCode, harnessOptions);
            if (harnessModel != null) {
                return new AgentHarnessModelBridge(harnessModel);
            }
        } catch (Exception e) {
            log.debug("配置文件解析失败, 无可用模型配置: modelCode={}", modelCode);
        }
        throw new IllegalArgumentException("未找到模型配置: " + modelCode
                + "（数据库和配置文件均无可用配置）");
    }

    /**
     * 从数据库加载模型配置并创建模型实例
     * @param modelCode
     * @param options
     * @return
     */
    private com.yangqiongai.ai.agent.runtime.model.AgentModel resolveFromDatabase(String modelCode,
                                                                              com.yangqiongai.agent.harness.core.model.AgentGenerateOptions options) {
        String scopeId = ScopeContext.getScopeId();
        // 缓存检查
        String cacheKey = scopeId + ":" + modelCode;
        com.yangqiongai.ai.agent.runtime.model.AgentModel cached = modelCache.get(cacheKey);
        if (cached != null) {
            log.debug("命中数据库模型缓存: {}", cacheKey);
            return cached;
        }
        if (modelInfoRepository == null) {
            throw new IllegalArgumentException("ModelInfoRepository未注入，无法从数据库加载模型: " + modelCode);
        }
        // 查询数据库
        ModelInfo modelInfo = modelInfoRepository.findByModelCode(modelCode)
                .orElseThrow(() -> new IllegalArgumentException("未找到模型配置: " + modelCode));
        if (modelInfo.getModelStatus() != null && modelInfo.getModelStatus() != 1) {
            throw new IllegalArgumentException("模型已禁用: " + modelCode);
        }
        log.info("从数据库加载模型配置, provider={}, modelName={}, modelCode={}, scopeId={}",
                modelInfo.getProvider(), modelInfo.getModelName(), modelCode, scopeId);
        // 构建创建上下文
        AgentModelCreationContext context = buildContext(modelCode, modelInfo, options);
        // 通过注册表创建模型
        String effectiveModelId = modelInfo.getProvider() + ":" + modelInfo.getModelName();
        AgentModel harnessModel = registry.resolve(effectiveModelId, context);
        com.yangqiongai.ai.agent.runtime.model.AgentModel frameworkModel = new AgentHarnessModelBridge(harnessModel);
        modelCache.put(cacheKey, frameworkModel);
        return frameworkModel;
    }

    /**
     * 从数据库模型信息和传入选项构建创建上下文
     * <p>
     * 密钥解析顺序：scope级租户凭据 → 数据库模型配置 → 默认配置。
     * </p>
     * @param modelCode
     * @param modelInfo
     * @param options
     * @return
     */
    private AgentModelCreationContext buildContext(String modelCode, ModelInfo modelInfo,
                                                    com.yangqiongai.agent.harness.core.model.AgentGenerateOptions options) {
        AgentModelCreationContext.Builder builder = AgentModelCreationContext.builder();
        // API密钥：scope级租户凭据优先，其次数据库配置，最后默认配置
        String scopeApiKey = resolveScopeApiKey(modelCode);
        if (scopeApiKey != null && !scopeApiKey.isBlank()) {
            builder.apiKey(scopeApiKey);
        } else if (modelInfo.getApiKey() != null && !modelInfo.getApiKey().isBlank()) {
            builder.apiKey(modelInfo.getApiKey());
        } else {
            builder.apiKey(properties.resolveApiKey(modelInfo.getProvider()));
        }
        // API地址：数据库配置优先，其次为默认配置
        if (modelInfo.getApiEndpoint() != null && !modelInfo.getApiEndpoint().isBlank()) {
            builder.baseUrl(modelInfo.getApiEndpoint());
        } else {
            builder.baseUrl(properties.resolveBaseUrl(modelInfo.getProvider()));
        }
        // 超时
        builder.timeoutSeconds(properties.getTimeoutSeconds());
        // 解析模型配置JSON作为扩展选项
        Map<String, Object> configOptions = parseModelConfig(modelInfo.getModelConfig());
        if (options != null) {
            builder.stream(options.isStream());
            if (options.getTemperature() != null) {
                builder.addOption("temperature", options.getTemperature());
            }
            if (options.getMaxTokens() != null) {
                builder.addOption("maxTokens", options.getMaxTokens());
            }
        }
        // 合并数据库配置中的选项
        if (configOptions != null && !configOptions.isEmpty()) {
            for (Map.Entry<String, Object> entry : configOptions.entrySet()) {
                builder.addOption(entry.getKey(), entry.getValue());
            }
        }
        return builder.build();
    }

    /**
     * 解析scope级凭据密钥
     * @param modelCode
     * @return 无scope级凭据或解析器未注入时返回null
     */
    private String resolveScopeApiKey(String modelCode) {
        if (credentialResolver == null) {
            return null;
        }
        try {
            return credentialResolver.resolveApiKey(modelCode, ScopeContext.getScopeId());
        } catch (Exception e) {
            log.warn("scope级凭据解析异常, 回退平台级配置: modelCode={}", modelCode, e);
            return null;
        }
    }

    /**
     * 解析模型配置JSON
     * @param jsonConfig
     * @return
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseModelConfig(String jsonConfig) {
        if (jsonConfig == null || jsonConfig.isBlank()) {
            return new HashMap<>();
        }
        try {
            return JSON_MAPPER.readValue(jsonConfig, Map.class);
        } catch (Exception e) {
            log.warn("解析模型配置JSON失败, 使用空配置: {}", jsonConfig, e);
            return new HashMap<>();
        }
    }

    /**
     * 清除指定模型编码的缓存（含所有scope）
     * @param modelCode
     */
    public void evictCache(String modelCode) {
        if (modelCode != null) {
            modelCache.keySet().removeIf(key -> key.endsWith(":" + modelCode));
        }
    }

    /**
     * 清除全部模型缓存
     */
    public void clearCache() {
        modelCache.clear();
    }

    /**
     * 监听模型配置变更事件，失效本层缓存与引擎注册表缓存
     * @param event
     */
    @EventListener
    public void onModelConfigChanged(ModelConfigChangedEvent event) {
        evictCache(event.getModelCode());
        registry.clearCache();
        log.info("模型配置变更，已失效模型缓存: modelCode={}", event.getModelCode());
    }

    /**
     * 获取被包装的独立引擎模型工厂
     * @return
     */
    HarnessModelFactory getDelegate() {
        return delegate;
    }
}