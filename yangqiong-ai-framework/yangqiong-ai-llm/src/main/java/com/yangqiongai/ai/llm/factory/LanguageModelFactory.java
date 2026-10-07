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
package com.yangqiongai.ai.llm.factory;

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.model.AgentChatResponse;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.llm.embed.DjlEmbeddingConfig;
import com.yangqiongai.ai.llm.embed.DjlTextEmbeddingClient;
import com.yangqiongai.ai.llm.embed.EmbeddingClient;
import com.yangqiongai.ai.llm.embed.LlmTextEmbeddingClient;
import com.yangqiongai.ai.llm.event.ModelConfigChangedEvent;
import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import jakarta.annotation.PreDestroy;
import org.apache.commons.collections4.MapUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 语言模型工厂
 * @author yangqiong
 */
@Service
public class LanguageModelFactory {

    private static final Logger log = LoggerFactory.getLogger(LanguageModelFactory.class);

    private static final String PROVIDER_DASHSCOPE = "dashscope";
    private static final String PROVIDER_OLLAMA = "ollama";

    private static final String PROVIDER_DJL = "djl";

    private static final String MODEL_TYPE_EMBEDDING = "EMBEDDING";

    private final ModelInfoRepository modelInfoRepository;

    /**
     * Agent模型工厂，由适配器模块提供实现
     */
    @Autowired(required = false)
    private AgentModelFactory agentModelFactory;

    /**
     * 功能集守卫
     */
    @Autowired
    private FeatureGuard featureGuard;

    private final ConcurrentHashMap<String, AgentModel> modelCache = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, EmbeddingClient> embeddingClientCache = new ConcurrentHashMap<>();

    public LanguageModelFactory(ModelInfoRepository modelInfoRepository) {
        this.modelInfoRepository = modelInfoRepository;
    }

    /**
     * 根据模型编码获取Model实例
     * @param modelCode
     * @return
     */
    public AgentModel getModel(String modelCode) {
        return getModel(modelCode, null);
    }

    /**
     * 根据模型编码获取Model实例
     * @param modelCode
     * @param config
     * @return
     */
    public AgentModel getModel(String modelCode, Map<String, Object> config) {
        featureGuard.checkModelAllowed(modelCode);
        return modelCache.computeIfAbsent(modelCode, code -> {
            if (agentModelFactory == null) {
                log.error("AgentModelFactory未注入，无法创建模型实例, modelCode={}", code);
                throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR,
                        "AgentModelFactory未注入，请确保适配器模块已启用, modelCode=" + code);
            }
            Optional<ModelInfo> optional = modelInfoRepository.findByModelCode(code);
            if (optional.isEmpty()) {
                log.error("模型配置不存在, modelCode={}", code);
                throw new AiException(AiErrorCode.MODEL_NOT_FOUND, "modelCode=" + code);
            }
            ModelInfo modelInfo = optional.get();
            if (modelInfo.getModelStatus() != null && modelInfo.getModelStatus() != 1) {
                log.warn("模型已禁用, modelCode={}", code);
                throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR, "模型已禁用, modelCode=" + code);
            }
            log.info("创建AgentModel实例, provider={}, modelName={}, modelCode={}",
                    modelInfo.getProvider(), modelInfo.getModelName(), code);
            return agentModelFactory.getModel(code, buildOptions(config));
        });
    }

    /**
     * 根据配置直接创建Model实例
     * @param provider
     * @param modelName
     * @param baseUrl
     * @param apiKey
     * @param config
     * @return
     */
    public AgentModel getModelByConfig(String provider, String modelName, String baseUrl,
                                       String apiKey, Map<String, Object> config) {
        if (!StringUtils.hasText(provider) || !StringUtils.hasText(modelName)) {
            log.error("provider和modelName不能为空");
            throw new AiException(AiErrorCode.PARAM_ERROR, "provider和modelName不能为空");
        }
        if (agentModelFactory == null) {
            log.error("AgentModelFactory未注入，无法按配置创建模型实例");
            throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR,
                    "AgentModelFactory未注入，请确保适配器模块已启用");
        }
        String cacheKey = buildCacheKey(provider, modelName, baseUrl);
        return modelCache.computeIfAbsent(cacheKey, key -> {
            log.info("按配置创建AgentModel实例, provider={}, modelName={}, baseUrl={}", provider, modelName, baseUrl);
            return agentModelFactory.getModelByConfig(provider, apiKey, modelName, buildOptions(config));
        });
    }

    /**
     * 清除指定模型的缓存
     * @param modelCode
     */
    public void evictCache(String modelCode) {
        AgentModel removed = modelCache.remove(modelCode);
        if (removed != null) {
            log.info("已清除模型缓存, modelCode={}", modelCode);
        }
        EmbeddingClient clientRemoved = embeddingClientCache.remove(modelCode);
        if (clientRemoved != null) {
            log.info("已清除嵌入客户端缓存, modelCode={}", modelCode);
        }
    }

    /**
     * 清除全部模型缓存
     */
    public void evictAllCache() {
        int modelSize = modelCache.size();
        int clientSize = embeddingClientCache.size();
        modelCache.clear();
        embeddingClientCache.clear();
        log.info("已清除全部模型缓存, chatModelCount={}, embeddingClientCount={}", modelSize, clientSize);
    }

    /**
     * 监听模型配置变更事件，失效本层模型与嵌入客户端缓存
     * @param event
     */
    @EventListener
    public void onModelConfigChanged(ModelConfigChangedEvent event) {
        evictCache(event.getModelCode());
    }

    /**
     * 容器销毁时释放嵌入客户端持有的native资源
     */
    @PreDestroy
    public void destroy() {
        embeddingClientCache.values().forEach(client -> {
            if (client instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception e) {
                    log.warn("关闭嵌入客户端失败", e);
                }
            }
        });
        embeddingClientCache.clear();
    }

    /**
     * 根据模型编码获取文本嵌入客户端
     * @param modelCode
     * @return
     */
    public EmbeddingClient getTextEmbeddingClient(String modelCode) {
        return embeddingClientCache.computeIfAbsent(modelCode, code -> {
            Optional<ModelInfo> optional = modelInfoRepository.findByModelCode(code);
            if (optional.isEmpty()) {
                log.error("嵌入模型配置不存在, modelCode={}", code);
                throw new AiException(AiErrorCode.MODEL_NOT_FOUND, "嵌入模型不存在, modelCode=" + code);
            }
            ModelInfo modelInfo = optional.get();
            if (!MODEL_TYPE_EMBEDDING.equals(modelInfo.getModelType())) {
                log.warn("模型类型非EMBEDDING, modelCode={}, modelType={}", code, modelInfo.getModelType());
            }
            if (modelInfo.getModelStatus() != null && modelInfo.getModelStatus() != 1) {
                log.warn("嵌入模型已禁用, modelCode={}", code);
                throw new AiException(AiErrorCode.MODEL_CONFIG_ERROR, "嵌入模型已禁用, modelCode=" + code);
            }
            log.info("创建TextEmbeddingClient, provider={}, modelName={}, modelCode={}",
                    modelInfo.getProvider(), modelInfo.getModelName(), code);
            return buildEmbeddingClient(modelInfo);
        });
    }

    /**
     * 根据ModelInfo构建文本嵌入客户端
     * @param modelInfo
     * @return
     */
    private EmbeddingClient buildEmbeddingClient(ModelInfo modelInfo) {
        String provider = modelInfo.getProvider();
        if (PROVIDER_DJL.equalsIgnoreCase(provider)) {
            return buildDjlEmbeddingClient(modelInfo);
        }
        String modelName = modelInfo.getModelName();
        String baseUrl = resolveEmbeddingBaseUrl(modelInfo);
        String apiKey = modelInfo.getApiKey();
        int dimensions = parseEmbeddingDimensions(modelInfo);

        if (!StringUtils.hasText(baseUrl)) {
            baseUrl = inferDefaultBaseUrl(provider);
        }

        return new LlmTextEmbeddingClient(baseUrl, apiKey, modelName, dimensions);
    }

    /**
     * 构建DJL本地嵌入客户端
     * @param modelInfo
     * @return
     */
    protected EmbeddingClient buildDjlEmbeddingClient(ModelInfo modelInfo) {
        DjlEmbeddingConfig config = DjlEmbeddingConfig.parse(modelInfo.getModelConfig());
        return new DjlTextEmbeddingClient(config, modelInfo.getModelCode());
    }

    /**
     * 解析嵌入API地址
     * @param modelInfo
     * @return
     */
    private String resolveEmbeddingBaseUrl(ModelInfo modelInfo) {
        String endpoint = modelInfo.getApiEndpoint();
        if (StringUtils.hasText(endpoint)) {
            return endpoint;
        }
        return "";
    }

    /**
     * 从模型配置中解析向量维度
     * @param modelInfo
     * @return
     */
    private int parseEmbeddingDimensions(ModelInfo modelInfo) {
        String config = modelInfo.getModelConfig();
        if (!StringUtils.hasText(config)) {
            return 1024;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node =
                    new com.fasterxml.jackson.databind.ObjectMapper().readTree(config);
            if (node.has("dimensions")) {
                return node.get("dimensions").asInt(1024);
            }
        } catch (Exception e) {
            log.warn("解析模型配置中的dimensions失败, 使用默认值1024, modelCode={}", modelInfo.getModelCode());
        }
        return 1024;
    }

    /**
     * 根据提供商推断默认嵌入API地址
     * @param provider
     * @return
     */
    private String inferDefaultBaseUrl(String provider) {
        switch (provider.toLowerCase()) {
            case PROVIDER_DASHSCOPE:
                return "https://dashscope.aliyuncs.com";
            case PROVIDER_OLLAMA:
                return "http://localhost:11434";
            default:
                return "";
        }
    }

    /**
     * 从配置Map构建AgentGenerateOptions
     * @param config
     * @return
     */
    private AgentGenerateOptions buildOptions(Map<String, Object> config) {
        if (config == null) {
            return null;
        }
        AgentGenerateOptions.Builder builder = AgentGenerateOptions.builder();
        if (MapUtils.getString(config, "temperature") != null) {
            builder.temperature(MapUtils.getDoubleValue(config, "temperature"));
        }
        Integer maxTokens = MapUtils.getInteger(config, "maxTokens");
        if (maxTokens == null) {
            maxTokens = MapUtils.getInteger(config, "max-tokens");
        }
        if (maxTokens != null) {
            builder.maxTokens(maxTokens);
        }
        if (MapUtils.getString(config, "topP") != null) {
            builder.topP(MapUtils.getDoubleValue(config, "topP"));
        }
        if (MapUtils.getString(config, "reasoningEffort") != null) {
            builder.reasoningEffort(MapUtils.getString(config, "reasoningEffort"));
        }
        if (MapUtils.getString(config, "thinkingBudget") != null) {
            builder.thinkingBudget(MapUtils.getInteger(config, "thinkingBudget"));
        }
        if (MapUtils.getString(config, "stream") != null) {
            builder.stream(MapUtils.getBoolean(config, "stream"));
        }
        return builder.build();
    }

    /**
     * 构建缓存Key
     * @param provider
     * @param modelName
     * @param baseUrl
     * @return
     */
    private String buildCacheKey(String provider, String modelName, String baseUrl) {
        if (StringUtils.hasText(baseUrl)) {
            return provider + ":" + modelName + ":" + baseUrl;
        }
        return provider + ":" + modelName;
    }

    /**
     * 使用指定模型生成文本（仅用户提示）
     * @param modelCode
     * @param prompt
     * @return
     */
    public String generateText(String modelCode, String prompt) {
        if (agentModelFactory == null) {
            log.warn("AgentModelFactory未注入，返回空字符串");
            return "";
        }
        AgentMessage userMsg = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder()
                        .text(com.yangqiongai.ai.common.util.StringUtils.getOrDefault(prompt))
                        .build()))
                .build();
        AgentChatResponse response = agentModelFactory.getModel(modelCode, null)
                .generate(List.of(userMsg), List.of(), null);
        return extractText(response);
    }

    /**
     * 使用指定模型流式生成文本（仅用户提示）
     * @param modelCode
     * @param prompt
     * @return
     */
    public Flux<String> generateStreamText(String modelCode, String prompt) {
        if (agentModelFactory == null) {
            log.warn("AgentModelFactory未注入，返回空流");
            return Flux.empty();
        }
        AgentMessage userMsg = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder()
                        .text(com.yangqiongai.ai.common.util.StringUtils.getOrDefault(prompt))
                        .build()))
                .build();
        return agentModelFactory.getModel(modelCode, null)
                .stream(List.of(userMsg), List.of(), null)
                .map(this::extractText);
    }

    /**
     * 使用指定模型生成文本（系统提示 + 用户提示）
     * @param modelCode
     * @param systemPrompt
     * @param userPrompt
     * @return
     */
    public String generateText(String modelCode, String systemPrompt, String userPrompt) {
        if (agentModelFactory == null) {
            log.warn("AgentModelFactory未注入，返回空字符串");
            return "";
        }
        AgentMessage systemMsg = AgentMessage.builder()
                .name("system")
                .role(AgentMessageRole.SYSTEM)
                .content(List.of(AgentTextBlock.builder()
                        .text(com.yangqiongai.ai.common.util.StringUtils.getOrDefault(systemPrompt))
                        .build()))
                .build();
        AgentMessage userMsg = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder()
                        .text(com.yangqiongai.ai.common.util.StringUtils.getOrDefault(userPrompt))
                        .build()))
                .build();
        AgentChatResponse response = agentModelFactory.getModel(modelCode, null)
                .generate(List.of(systemMsg, userMsg), List.of(), null);
        return extractText(response);
    }

    /**
     * 使用指定模型流式生成文本（系统提示 + 用户提示）
     * @param modelCode
     * @param systemPrompt
     * @param userPrompt
     * @return
     */
    public Flux<String> generateStreamText(String modelCode, String systemPrompt, String userPrompt) {
        if (agentModelFactory == null) {
            log.warn("AgentModelFactory未注入，返回空流");
            return Flux.empty();
        }
        AgentMessage systemMsg = AgentMessage.builder()
                .name("system")
                .role(AgentMessageRole.SYSTEM)
                .content(List.of(AgentTextBlock.builder()
                        .text(com.yangqiongai.ai.common.util.StringUtils.getOrDefault(systemPrompt))
                        .build()))
                .build();
        AgentMessage userMsg = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder()
                        .text(com.yangqiongai.ai.common.util.StringUtils.getOrDefault(userPrompt))
                        .build()))
                .build();
        return agentModelFactory.getModel(modelCode, null)
                .stream(List.of(systemMsg, userMsg), List.of(), null)
                .map(this::extractText);
    }

    /**
     * 使用指定模型和消息列表生成文本
     * @param modelCode
     * @param messages
     * @return
     */
    public String generateText(String modelCode, List<AgentMessage> messages) {
        if (agentModelFactory == null) {
            log.warn("AgentModelFactory未注入，返回空字符串");
            return "";
        }
        AgentChatResponse response = agentModelFactory.getModel(modelCode, null)
                .generate(messages, List.of(), null);
        return extractText(response);
    }

    /**
     * 使用指定模型和消息列表流式生成文本
     * @param modelCode
     * @param messages
     * @return
     */
    public Flux<String> generateStreamText(String modelCode, List<AgentMessage> messages) {
        if (agentModelFactory == null) {
            log.warn("AgentModelFactory未注入，返回空流");
            return Flux.empty();
        }
        return agentModelFactory.getModel(modelCode, null)
                .stream(messages, List.of(), null)
                .map(this::extractText);
    }

    /**
     * 从AgentChatResponse中抽取纯文本，拼接所有AgentTextBlock
     * @param response
     * @return
     */
    private String extractText(AgentChatResponse response) {
        if (response == null || response.getContent() == null || response.getContent().isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentContentBlock block : response.getContent()) {
            if (block instanceof AgentTextBlock textBlock && textBlock.getText() != null) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }
}
