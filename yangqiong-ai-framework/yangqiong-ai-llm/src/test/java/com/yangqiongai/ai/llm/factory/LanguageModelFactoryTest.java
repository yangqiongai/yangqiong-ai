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

import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.DefaultFeatureGuard;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import com.yangqiongai.ai.agent.runtime.model.AgentModelFactory;
import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("LanguageModelFactory 单元测试")
@ExtendWith(MockitoExtension.class)
class LanguageModelFactoryTest {

    @Mock
    private ModelInfoRepository modelInfoRepository;

    @Mock
    private AgentModelFactory agentModelFactory;

    @Mock
    private AgentModel agentModel;

    private LanguageModelFactory factory;

    private ModelInfo buildModelInfo(String modelCode, String provider, String modelName, Integer modelStatus) {
        ModelInfo info = new ModelInfo();
        info.setModelCode(modelCode);
        info.setProvider(provider);
        info.setModelName(modelName);
        info.setModelStatus(modelStatus);
        info.setApiEndpoint("https://api.example.com");
        info.setApiKey("test-controller-key");
        return info;
    }

    @BeforeEach
    void setUp() {
        factory = new LanguageModelFactory(modelInfoRepository);
        ReflectionTestUtils.setField(factory, "featureGuard", new DefaultFeatureGuard());
        ReflectionTestUtils.setField(factory, "agentModelFactory", agentModelFactory);
    }

    @Nested
    @DisplayName("getModel 测试")
    class GetModelTest {

        @Test
        @DisplayName("缓存未命中：创建新Model并缓存")
        void shouldCreateAndCacheOnMiss() {
            ModelInfo modelInfo = buildModelInfo("qwen-plus", "dashscope", "qwen-plus", 1);
            when(modelInfoRepository.findByModelCode("qwen-plus")).thenReturn(Optional.of(modelInfo));
            when(agentModelFactory.getModel(eq("qwen-plus"), any())).thenReturn(agentModel);

            AgentModel model1 = factory.getModel("qwen-plus");
            AgentModel model2 = factory.getModel("qwen-plus");

            assertThat(model1).isNotNull();
            assertThat(model2).isSameAs(model1);
        }

        @Test
        @DisplayName("缓存命中：返回缓存的Model")
        void shouldReturnCachedModelOnHit() {
            ModelInfo modelInfo = buildModelInfo("qwen-plus", "dashscope", "qwen-plus", 1);
            when(modelInfoRepository.findByModelCode("qwen-plus")).thenReturn(Optional.of(modelInfo));
            when(agentModelFactory.getModel(eq("qwen-plus"), any())).thenReturn(agentModel);

            AgentModel first = factory.getModel("qwen-plus");
            // 第二次调用不应再查数据库（缓存命中）
            AgentModel second = factory.getModel("qwen-plus");

            assertThat(second).isSameAs(first);
            verify(agentModelFactory, times(1)).getModel(eq("qwen-plus"), any());
        }

        @Test
        @DisplayName("模型不存在：抛出AiException(MODEL_NOT_FOUND)")
        void shouldThrowWhenModelNotFound() {
            when(modelInfoRepository.findByModelCode("not-exist")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> factory.getModel("not-exist"))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode()).isEqualTo(AiErrorCode.MODEL_NOT_FOUND.getCode()));
        }

        @Test
        @DisplayName("模型已禁用：抛出AiException(MODEL_CONFIG_ERROR)")
        void shouldThrowWhenModelDisabled() {
            ModelInfo modelInfo = buildModelInfo("qwen-disabled", "dashscope", "qwen-disabled", 0);
            when(modelInfoRepository.findByModelCode("qwen-disabled")).thenReturn(Optional.of(modelInfo));

            assertThatThrownBy(() -> factory.getModel("qwen-disabled"))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode()).isEqualTo(AiErrorCode.MODEL_CONFIG_ERROR.getCode()));
        }
    }

    @Nested
    @DisplayName("evictCache 测试")
    class EvictCacheTest {

        @Test
        @DisplayName("移除指定模型的缓存")
        void shouldRemoveCachedModel() {
            ModelInfo modelInfo = buildModelInfo("qwen-plus", "dashscope", "qwen-plus", 1);
            when(modelInfoRepository.findByModelCode("qwen-plus")).thenReturn(Optional.of(modelInfo));
            when(agentModelFactory.getModel(eq("qwen-plus"), any())).thenReturn(agentModel);

            // 先缓存
            factory.getModel("qwen-plus");
            // 清除缓存
            factory.evictCache("qwen-plus");
            // 再次获取应重新创建（需要再次mock）
            when(modelInfoRepository.findByModelCode("qwen-plus")).thenReturn(Optional.of(modelInfo));
            AgentModel model = factory.getModel("qwen-plus");

            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("evictAllCache 测试")
    class EvictAllCacheTest {

        @Test
        @DisplayName("清除所有缓存")
        void shouldClearAllCache() {
            ModelInfo modelInfo1 = buildModelInfo("qwen-plus", "dashscope", "qwen-plus", 1);
            ModelInfo modelInfo2 = buildModelInfo("qwen-turbo", "openai", "gpt-3.5-turbo", 1);
            when(modelInfoRepository.findByModelCode("qwen-plus")).thenReturn(Optional.of(modelInfo1));
            when(modelInfoRepository.findByModelCode("qwen-turbo")).thenReturn(Optional.of(modelInfo2));
            when(agentModelFactory.getModel(eq("qwen-plus"), any())).thenReturn(agentModel);
            when(agentModelFactory.getModel(eq("qwen-turbo"), any())).thenReturn(agentModel);

            // 缓存两个模型
            factory.getModel("qwen-plus");
            factory.getModel("qwen-turbo");

            // 清除全部
            factory.evictAllCache();

            // 再次获取应重新创建
            when(modelInfoRepository.findByModelCode("qwen-plus")).thenReturn(Optional.of(modelInfo1));
            AgentModel model = factory.getModel("qwen-plus");
            assertThat(model).isNotNull();
        }
    }

    @Nested
    @DisplayName("generateText 测试")
    class GenerateTextTest {

        @Test
        @DisplayName("模型不存在时抛出AiException")
        void shouldThrowWhenModelNotFound() {
            when(agentModelFactory.getModel(eq("not-exist"), any()))
                    .thenThrow(new AiException(AiErrorCode.MODEL_NOT_FOUND, "modelCode=not-exist"));

            assertThatThrownBy(() -> factory.generateText("not-exist", "hello"))
                    .isInstanceOf(AiException.class)
                    .satisfies(ex -> assertThat(((AiException) ex).getCode())
                            .isEqualTo(AiErrorCode.MODEL_NOT_FOUND.getCode()));
        }
    }
}
