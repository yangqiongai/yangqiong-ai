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
package com.yangqiongai.ai.data.llm.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.common.scope.ScopeContext;
import com.yangqiongai.ai.data.llm.entity.ModelInfo;
import com.yangqiongai.ai.data.llm.mapper.ModelInfoMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型信息存储测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultModelInfoRepositoryTest {

    @Mock
    private ModelInfoMapper modelInfoMapper;

    private DefaultModelInfoRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ModelInfo.class);
        repository = new DefaultModelInfoRepository();
        java.lang.reflect.Field mapperField = DefaultModelInfoRepository.class.getDeclaredField("modelInfoMapper");
        mapperField.setAccessible(true);
        mapperField.set(repository, modelInfoMapper);
    }

    @AfterEach
    void tearDown() {
        ScopeContext.clear();
    }

    private ModelInfo entity(Long id, String scopeId) {
        ModelInfo entity = new ModelInfo();
        entity.setId(id);
        entity.setModelCode("deepseek");
        entity.setModelName("DeepSeek");
        entity.setProvider("openai");
        entity.setApiEndpoint("https://api.deepseek.com");
        entity.setApiKey("sk-test");
        entity.setScopeId(scopeId);
        return entity;
    }

    @Test
    @DisplayName("当前作用域存在模型配置时直接返回且不触发默认域回退查询")
    void findByModelCode_returnsScopedConfigWithoutFallback() {
        ScopeContext.setScopeId("t-100");
        when(modelInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(entity(1L, "t-100"));

        Optional<com.yangqiongai.ai.llm.model.ModelInfo> result = repository.findByModelCode("deepseek");

        assertThat(result).isPresent();
        assertThat(result.get().getModelCode()).isEqualTo("deepseek");
        verify(modelInfoMapper, never()).selectDefaultScopeByModelCode(anyString());
    }

    @Test
    @DisplayName("租户作用域无配置时回退平台默认域共享模型")
    void findByModelCode_fallsBackToDefaultScopeForTenant() {
        ScopeContext.setScopeId("t-100");
        when(modelInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(modelInfoMapper.selectDefaultScopeByModelCode("deepseek")).thenReturn(entity(2L, "default"));

        Optional<com.yangqiongai.ai.llm.model.ModelInfo> result = repository.findByModelCode("deepseek");

        assertThat(result).isPresent();
        assertThat(result.get().getApiEndpoint()).isEqualTo("https://api.deepseek.com");
        verify(modelInfoMapper).selectDefaultScopeByModelCode("deepseek");
    }

    @Test
    @DisplayName("默认作用域无配置时不重复执行默认域回退查询")
    void findByModelCode_skipsFallbackWhenAlreadyDefaultScope() {
        ScopeContext.clear();
        when(modelInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        Optional<com.yangqiongai.ai.llm.model.ModelInfo> result = repository.findByModelCode("deepseek");

        assertThat(result).isEmpty();
        verify(modelInfoMapper, never()).selectDefaultScopeByModelCode(anyString());
    }

    @Test
    @DisplayName("回退查询仍无结果时返回空")
    void findByModelCode_returnsEmptyWhenFallbackMisses() {
        ScopeContext.setScopeId("t-100");
        when(modelInfoMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(modelInfoMapper.selectDefaultScopeByModelCode("deepseek")).thenReturn(null);

        Optional<com.yangqiongai.ai.llm.model.ModelInfo> result = repository.findByModelCode("deepseek");

        assertThat(result).isEmpty();
    }
}
