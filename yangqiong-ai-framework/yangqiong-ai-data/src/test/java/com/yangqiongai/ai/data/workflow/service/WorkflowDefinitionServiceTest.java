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
package com.yangqiongai.ai.data.workflow.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.data.workflow.entity.WorkflowDefinitionEntity;
import com.yangqiongai.ai.workflow.api.dto.WorkflowDefinitionUpdateRequest;
import com.yangqiongai.ai.workflow.event.WorkflowDefinitionSavedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * 工作流定义管理测试（updateDefinition版本分支）
 * @author yangqiong
 */
class WorkflowDefinitionServiceTest {

    private WorkflowDefinitionService service;

    private ApplicationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = mock(ApplicationEventPublisher.class);
        service = spy(new WorkflowDefinitionService(new ObjectMapper(), publisher));
    }

    /**
     * 构造最新版本实体（version=3）
     */
    private WorkflowDefinitionEntity existing() {
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity();
        entity.setId(100L);
        entity.setDefinitionName("demo");
        entity.setVersion(3);
        entity.setStatus(1);
        entity.setDisplayName("演示流程");
        entity.setDescription("描述");
        entity.setCategory("通用");
        entity.setRemark("备注");
        entity.setDefinitionJson("{\"nodes\":[],\"edges\":[]}");
        return entity;
    }

    private void stubLatest(WorkflowDefinitionEntity entity) {
        doReturn(entity).when(service).getOne(any(Wrapper.class), anyBoolean());
    }

    /**
     * asNewVersion=true时在最新版本基础上创建version+1新记录，不走原地更新
     */
    @Test
    @DisplayName("另存新版本：创建version+1新记录")
    void updateAsNewVersionTrueCreatesNextVersion() {
        stubLatest(existing());
        doReturn(true).when(service).save(any(WorkflowDefinitionEntity.class));
        WorkflowDefinitionUpdateRequest request = new WorkflowDefinitionUpdateRequest();
        request.setAsNewVersion(true);
        request.setDefinition(Map.of("name", "新内容"));

        boolean result = service.updateDefinition("demo", request);

        assertThat(result).isTrue();
        ArgumentCaptor<WorkflowDefinitionEntity> captor = ArgumentCaptor.forClass(WorkflowDefinitionEntity.class);
        verify(service).save(captor.capture());
        verify(service, never()).updateById(any(WorkflowDefinitionEntity.class));
        WorkflowDefinitionEntity saved = captor.getValue();
        assertThat(saved.getId()).isNull();
        assertThat(saved.getVersion()).isEqualTo(4);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getDisplayName()).isEqualTo("演示流程");
        assertThat(saved.getDefinitionJson()).contains("新内容");
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        verify(publisher).publishEvent(any(WorkflowDefinitionSavedEvent.class));
    }

    /**
     * asNewVersion=false时原地覆盖最新版本，版本号不变不新增记录
     */
    @Test
    @DisplayName("保存当前版本：原地覆盖不新增记录")
    void updateAsNewVersionFalseOverwritesInPlace() {
        WorkflowDefinitionEntity entity = existing();
        stubLatest(entity);
        doReturn(true).when(service).updateById(any(WorkflowDefinitionEntity.class));
        WorkflowDefinitionUpdateRequest request = new WorkflowDefinitionUpdateRequest();
        request.setAsNewVersion(false);
        request.setDisplayName("改名");
        request.setDefinition(Map.of("name", "新内容"));

        boolean result = service.updateDefinition("demo", request);

        assertThat(result).isTrue();
        ArgumentCaptor<WorkflowDefinitionEntity> captor = ArgumentCaptor.forClass(WorkflowDefinitionEntity.class);
        verify(service).updateById(captor.capture());
        verify(service, never()).save(any(WorkflowDefinitionEntity.class));
        WorkflowDefinitionEntity updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(100L);
        assertThat(updated.getVersion()).isEqualTo(3);
        assertThat(updated.getDisplayName()).isEqualTo("改名");
        assertThat(updated.getDefinitionJson()).contains("新内容");
        assertThat(updated.getUpdateTime()).isNotNull();
        verify(publisher).publishEvent(any(WorkflowDefinitionSavedEvent.class));
    }

    /**
     * asNewVersion不传时默认原地覆盖
     */
    @Test
    @DisplayName("asNewVersion缺省：默认原地覆盖")
    void updateAsNewVersionNullDefaultsToOverwrite() {
        stubLatest(existing());
        doReturn(true).when(service).updateById(any(WorkflowDefinitionEntity.class));
        WorkflowDefinitionUpdateRequest request = new WorkflowDefinitionUpdateRequest();
        request.setDefinition(Map.of("name", "新内容"));

        boolean result = service.updateDefinition("demo", request);

        assertThat(result).isTrue();
        verify(service).updateById(any(WorkflowDefinitionEntity.class));
        verify(service, never()).save(any(WorkflowDefinitionEntity.class));
    }

    /**
     * definition为空时原地覆盖保留原JSON，另存新版本时复制原JSON
     */
    @Test
    @DisplayName("definition为空：保留原定义JSON")
    void updateWithoutDefinitionKeepsExistingJson() {
        WorkflowDefinitionEntity entity = existing();
        stubLatest(entity);
        doReturn(true).when(service).updateById(any(WorkflowDefinitionEntity.class));
        WorkflowDefinitionUpdateRequest request = new WorkflowDefinitionUpdateRequest();
        request.setAsNewVersion(false);
        request.setDisplayName("仅改名");

        boolean result = service.updateDefinition("demo", request);

        assertThat(result).isTrue();
        ArgumentCaptor<WorkflowDefinitionEntity> captor = ArgumentCaptor.forClass(WorkflowDefinitionEntity.class);
        verify(service).updateById(captor.capture());
        assertThat(captor.getValue().getDefinitionJson()).isEqualTo("{\"nodes\":[],\"edges\":[]}");
    }

    /**
     * 定义不存在时返回false
     */
    @Test
    @DisplayName("定义不存在：返回false")
    void updateMissingDefinitionReturnsFalse() {
        stubLatest(null);
        WorkflowDefinitionUpdateRequest request = new WorkflowDefinitionUpdateRequest();
        request.setAsNewVersion(true);

        boolean result = service.updateDefinition("demo", request);

        assertThat(result).isFalse();
        verify(service, never()).save(any(WorkflowDefinitionEntity.class));
        verify(service, never()).updateById(any(WorkflowDefinitionEntity.class));
    }
}
