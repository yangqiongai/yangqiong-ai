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

import com.yangqiongai.agent.harness.core.model.AgentModel;
import com.yangqiongai.agent.harness.core.model.registry.AgentModelRegistry;
import com.yangqiongai.agent.harness.core.model.spi.AgentModelCreationContext;
import com.yangqiongai.agent.harness.model.HarnessModelFactory;
import com.yangqiongai.agent.harness.model.HarnessModelProperties;
import com.yangqiongai.ai.agent.runtime.model.spi.ModelCredentialResolver;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.scope.FeatureGuard;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.llm.model.ModelInfo;
import com.yangqiongai.ai.llm.repository.ModelInfoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Harness模型工厂桥接器测试
 * <p>
 * 验证scope级租户密钥解析优先级：租户凭据 > 数据库模型配置 > 默认配置，以及模型缓存按scope隔离。
 * </p>
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class HarnessModelFactoryBridgeTest {

    private static final String MODEL_A = "model-a";

    @Mock
    private HarnessModelFactory delegate;

    @Mock
    private AgentModelRegistry registry;

    @Mock
    private ModelInfoRepository modelInfoRepository;

    @Mock
    private ModelCredentialResolver credentialResolver;

    /**
     * 捕获注册表收到的API密钥
     */
    private String capturedApiKey;

    private HarnessModelProperties properties;

    @BeforeEach
    void setUp() {
        ScopeContext.setScopeId("tenant-a");
        properties = new HarnessModelProperties();
        capturedApiKey = null;
    }

    @AfterEach
    void tearDown() {
        ScopeContext.clear();
    }

    private ModelInfo modelInfo(String apiKey) {
        ModelInfo modelInfo = new ModelInfo();
        modelInfo.setModelCode(MODEL_A);
        modelInfo.setProvider("openai");
        modelInfo.setModelName("gpt-test");
        modelInfo.setModelStatus(1);
        modelInfo.setApiKey(apiKey);
        return modelInfo;
    }

    /**
     * stub注册表解析，捕获创建上下文中的API密钥
     */
    private void stubRegistryCaptureKey() {
        when(registry.resolve(anyString(), any())).thenAnswer(invocation -> {
            AgentModelCreationContext context = invocation.getArgument(1);
            capturedApiKey = context.getApiKey();
            return mock(AgentModel.class);
        });
    }

    @Test
    void 解析器未注入时走数据库模型配置密钥() {
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.of(modelInfo("sk-db-key")));
        stubRegistryCaptureKey();
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, null);

        bridge.getModel(MODEL_A, null);

        verify(credentialResolver, never()).resolveApiKey(anyString(), anyString());
        assertThat(capturedApiKey).isEqualTo("sk-db-key");
    }

    @Test
    void scope级租户凭据优先于数据库模型配置() {
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.of(modelInfo("sk-db-key")));
        when(credentialResolver.resolveApiKey(MODEL_A, "tenant-a")).thenReturn("sk-scope-key");
        stubRegistryCaptureKey();
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, credentialResolver);

        bridge.getModel(MODEL_A, null);

        assertThat(capturedApiKey).isEqualTo("sk-scope-key");
    }

    @Test
    void scope级凭据缺失时保留数据库模型配置密钥() {
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.of(modelInfo("sk-db-key")));
        when(credentialResolver.resolveApiKey(MODEL_A, "tenant-a")).thenReturn(null);
        stubRegistryCaptureKey();
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, credentialResolver);

        bridge.getModel(MODEL_A, null);

        assertThat(capturedApiKey).isEqualTo("sk-db-key");
    }

    @Test
    void 模型缓存按scope隔离() {
        when(credentialResolver.resolveApiKey(MODEL_A, "tenant-a")).thenReturn("sk-scope-a");
        when(credentialResolver.resolveApiKey(MODEL_A, "tenant-b")).thenReturn("sk-scope-b");
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.of(modelInfo(null)));
        stubRegistryCaptureKey();
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, credentialResolver);

        bridge.getModel(MODEL_A, null);
        assertThat(capturedApiKey).isEqualTo("sk-scope-a");

        // 切换scope后不得命中tenant-a的缓存
        ScopeContext.setScopeId("tenant-b");
        bridge.getModel(MODEL_A, null);
        assertThat(capturedApiKey).isEqualTo("sk-scope-b");
    }

    @Test
    void evictCache清除全部scope的模型缓存() {
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.of(modelInfo(null)));
        when(registry.resolve(anyString(), any())).thenReturn(mock(AgentModel.class));
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, null);

        bridge.getModel(MODEL_A, null);
        bridge.evictCache(MODEL_A);
        bridge.getModel(MODEL_A, null);

        verify(modelInfoRepository, times(2)).findByModelCode(MODEL_A);
    }

    @Test
    void 数据库无配置时回退配置文件工厂() {
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.empty());
        when(delegate.getModel(eq(MODEL_A), any()))
                .thenReturn(mock(com.yangqiongai.agent.harness.core.model.AgentModel.class));
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, null);

        assertThat(bridge.getModel(MODEL_A, null)).isNotNull();
    }

    @Test
    void 白名单守卫拒绝时直接抛出不创建模型() {
        FeatureGuard featureGuard = mock(FeatureGuard.class);
        doThrow(new AiException(AiErrorCode.PLAN_MODEL_NOT_ALLOWED, MODEL_A))
                .when(featureGuard).checkModelAllowed(MODEL_A);
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, null, featureGuard);

        assertThatThrownBy(() -> bridge.getModel(MODEL_A, null))
                .isInstanceOf(AiException.class)
                .satisfies(ex -> assertThat(((AiException) ex).getCode())
                        .isEqualTo(AiErrorCode.PLAN_MODEL_NOT_ALLOWED.getCode()));
        verify(modelInfoRepository, never()).findByModelCode(anyString());
    }

    @Test
    void 白名单守卫放行时正常创建模型并透传校验() {
        FeatureGuard featureGuard = mock(FeatureGuard.class);
        when(modelInfoRepository.findByModelCode(MODEL_A))
                .thenReturn(Optional.of(modelInfo(null)));
        when(registry.resolve(anyString(), any())).thenReturn(mock(AgentModel.class));
        HarnessModelFactoryBridge bridge = new HarnessModelFactoryBridge(
                delegate, registry, properties, modelInfoRepository, null, featureGuard);

        bridge.getModel(MODEL_A, null);

        verify(featureGuard, times(1)).checkModelAllowed(MODEL_A);
        verify(modelInfoRepository, times(1)).findByModelCode(MODEL_A);
    }
}
