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
package com.yangqiongai.ai.llm;

import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("DefaultLlmModelService 单元测试")
@ExtendWith(MockitoExtension.class)
class DefaultLlmModelServiceTest {

    @Mock
    private ModelInfoRepository modelInfoRepository;

    @InjectMocks
    private DefaultLlmModelService defaultLlmModelService;

    private ModelInfo buildModelInfo(String modelCode, String modelType, Integer isDefault, Integer modelStatus) {
        ModelInfo info = new ModelInfo();
        info.setModelCode(modelCode);
        info.setModelType(modelType);
        info.setIsDefault(isDefault);
        info.setModelStatus(modelStatus);
        return info;
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(defaultLlmModelService, "defaultModel", "qwen-plus");
    }

    @Nested
    @DisplayName("resolveModel 测试")
    class ResolveModelTest {

        @Test
        @DisplayName("按agentCode匹配到模型：返回匹配的模型编码")
        void shouldReturnMatchedModelCode() {
            List<ModelInfo> models = List.of(
                    buildModelInfo("qwen-coder", "CODE", 0, 1),
                    buildModelInfo("qwen-plus", "CHAT", 1, 1)
            );
            when(modelInfoRepository.findAll()).thenReturn(models);

            String result = defaultLlmModelService.resolveModel("CODE", "user1");

            assertThat(result).isEqualTo("qwen-coder");
        }

        @Test
        @DisplayName("未匹配agentCode：回退到默认模型")
        void shouldFallBackToDefaultModel() {
            List<ModelInfo> models = List.of(
                    buildModelInfo("qwen-plus", "CHAT", 1, 1),
                    buildModelInfo("qwen-turbo", "CHAT", 0, 1)
            );
            when(modelInfoRepository.findAll()).thenReturn(models);

            String result = defaultLlmModelService.resolveModel("CODE", "user1");

            assertThat(result).isEqualTo("qwen-plus");
        }

        @Test
        @DisplayName("无匹配且无默认模型：使用配置默认值qwen-plus")
        void shouldUseConfigDefaultWhenNoMatchAndNoDefault() {
            List<ModelInfo> models = List.of(
                    buildModelInfo("qwen-turbo", "CHAT", 0, 1)
            );
            when(modelInfoRepository.findAll()).thenReturn(models);

            String result = defaultLlmModelService.resolveModel("CODE", "user1");

            assertThat(result).isEqualTo("qwen-plus");
        }
    }

    @Nested
    @DisplayName("listAvailableModels 测试")
    class ListAvailableModelsTest {

        @Test
        @DisplayName("仅返回状态为1的模型")
        void shouldReturnOnlyEnabledModels() {
            List<ModelInfo> models = List.of(
                    buildModelInfo("qwen-plus", "CHAT", 1, 1),
                    buildModelInfo("qwen-turbo", "CHAT", 0, 1),
                    buildModelInfo("qwen-disabled", "CHAT", 0, 0)
            );
            when(modelInfoRepository.findAll()).thenReturn(models);

            List<String> result = defaultLlmModelService.listAvailableModels();

            assertThat(result).containsExactly("qwen-plus", "qwen-turbo");
            assertThat(result).doesNotContain("qwen-disabled");
        }

        @Test
        @DisplayName("无可用模型时返回空列表")
        void shouldReturnEmptyListWhenNoModelsAvailable() {
            when(modelInfoRepository.findAll()).thenReturn(Collections.emptyList());

            List<String> result = defaultLlmModelService.listAvailableModels();

            assertThat(result).isEmpty();
        }
    }
}
