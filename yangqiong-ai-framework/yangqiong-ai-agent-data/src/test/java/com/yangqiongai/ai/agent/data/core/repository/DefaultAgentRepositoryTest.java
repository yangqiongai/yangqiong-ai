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
package com.yangqiongai.ai.agent.data.core.repository;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.yangqiongai.ai.agent.data.core.entity.AgentEntity;
import com.yangqiongai.ai.agent.data.core.mapper.AgentMapper;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent配置仓库测试（作用域感知缓存隔离）
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class DefaultAgentRepositoryTest {

    @Mock
    private AgentMapper agentMapper;

    private DefaultAgentRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AgentEntity.class);
        repository = new DefaultAgentRepository();
        Field mapperField = DefaultAgentRepository.class.getDeclaredField("agentTypeMapper");
        mapperField.setAccessible(true);
        mapperField.set(repository, agentMapper);
        setField("cacheTtlMinutes", 5L);
        setField("cacheMaxSize", 1000L);
        repository.init();
    }

    @AfterEach
    void tearDown() {
        ScopeContext.clear();
    }

    /**
     * 反射设置字段值
     * @param fieldName
     * @param value
     */
    private void setField(String fieldName, Object value) throws Exception {
        Field field = DefaultAgentRepository.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(repository, value);
    }

    /**
     * 构建Agent实体
     * @param agentCode
     * @param agentName
     * @param status
     * @return
     */
    private AgentEntity buildEntity(String agentCode, String agentName, int status) {
        AgentEntity entity = new AgentEntity();
        entity.setId(1L);
        entity.setAgentCode(agentCode);
        entity.setAgentName(agentName);
        entity.setStatus(status);
        entity.setShared(0);
        entity.setScopeId(ScopeContext.getScopeId());
        return entity;
    }

    @Test
    void getByCodeIsolatesCacheBetweenScopes() {
        // 复制模式下同一agentCode可存在于多个作用域，缓存必须按scope隔离
        ScopeContext.setScopeId("tenant-1");
        AgentEntity tenantCopy = buildEntity("report-writer", "租户副本", 1);
        when(agentMapper.selectOne(any())).thenReturn(tenantCopy);
        assertThat(repository.getByCode("report-writer").getAgentName()).isEqualTo("租户副本");

        ScopeContext.setScopeId("default");
        AgentEntity platformTemplate = buildEntity("report-writer", "平台模板", 1);
        when(agentMapper.selectOne(any())).thenReturn(platformTemplate);
        assertThat(repository.getByCode("report-writer").getAgentName()).isEqualTo("平台模板");

        // 两次查询处于不同作用域，均应命中数据库
        verify(agentMapper, times(2)).selectOne(any());
    }

    @Test
    void getByCodeHitsCacheWithinSameScope() {
        ScopeContext.setScopeId("tenant-1");
        when(agentMapper.selectOne(any())).thenReturn(buildEntity("report-writer", "租户副本", 1));

        assertThat(repository.getByCode("report-writer").getAgentName()).isEqualTo("租户副本");
        assertThat(repository.getByCode("report-writer").getAgentName()).isEqualTo("租户副本");

        verify(agentMapper, times(1)).selectOne(any());
    }

    @Test
    void toggleStatusInvalidatesScopedCache() {
        ScopeContext.setScopeId("tenant-1");
        when(agentMapper.selectOne(any())).thenReturn(buildEntity("report-writer", "租户副本", 1));
        when(agentMapper.update(any(), any())).thenReturn(1);

        assertThat(repository.toggleStatus("report-writer")).isTrue();
        repository.getByCode("report-writer");

        // 状态切换后同scope缓存已失效，再次查询应重新加载
        verify(agentMapper, times(2)).selectOne(any());
    }

    @Test
    void getByCodeMapsSharedOriginFields() {
        ScopeContext.setScopeId("tenant-1");
        AgentEntity entity = buildEntity("report-writer", "租户副本", 1);
        entity.setOriginAgentCode("report-writer");
        entity.setOriginVersionId(9L);
        when(agentMapper.selectOne(any())).thenReturn(entity);

        repository.getByCode("report-writer");

        // 复制来源字段需完整映射到领域模型
        assertThat(repository.getByCode("report-writer").getOriginAgentCode()).isEqualTo("report-writer");
        assertThat(repository.getByCode("report-writer").getOriginVersionId()).isEqualTo(9L);
    }
}
