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
package com.yangqiongai.ai.memory.agentmemory.service;

import com.yangqiongai.ai.memory.agentmemory.event.AgentMemoryErasedEvent;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.vector.AgentMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent运行记忆合规擦除单元测试
 * @author yangqiong
 */
@DisplayName("Agent运行记忆合规擦除单元测试")
class AgentMemoryEraseServiceTest {

    @Mock
    private AgentMemoryEntryRepository entryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AgentMemoryVectorStore agentMemoryVectorStore;

    private AgentMemoryEraseService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new AgentMemoryEraseService();
        injectField("entryRepository", entryRepository);
        injectField("eventPublisher", eventPublisher);
        injectField("agentMemoryVectorStore", agentMemoryVectorStore);
    }

    @Test
    @DisplayName("按用户锚点擦除清理向量与记录并发布事件")
    void eraseByUser_deletesVectorAndRecords() {
        when(entryRepository.findByUserAnchor("u1"))
                .thenReturn(List.of(entry(1L), entry(2L)));
        when(agentMemoryVectorStore.deleteByMemoryIds(List.of(1L, 2L))).thenReturn(2);

        int erased = service.eraseByUser("u1");

        assertThat(erased).isEqualTo(2);
        verify(agentMemoryVectorStore).deleteByMemoryIds(List.of(1L, 2L));
        verify(entryRepository).deleteByUserAnchor("u1");
        ArgumentCaptor<AgentMemoryErasedEvent> captor = ArgumentCaptor.forClass(AgentMemoryErasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getScope()).isEqualTo(AgentMemoryErasedEvent.SCOPE_USER);
        assertThat(captor.getValue().getTarget()).isEqualTo("u1");
        assertThat(captor.getValue().getErasedCount()).isEqualTo(2);
        assertThat(captor.getValue().getVectorDeleted()).isEqualTo(2);
    }

    @Test
    @DisplayName("按Agent编码擦除并发布AGENT维度事件")
    void eraseByAgent_deletesVectorAndRecords() {
        when(entryRepository.findByAgentCode("a1")).thenReturn(List.of(entry(5L)));
        when(agentMemoryVectorStore.deleteByMemoryIds(List.of(5L))).thenReturn(1);

        int erased = service.eraseByAgent("a1");

        assertThat(erased).isEqualTo(1);
        verify(agentMemoryVectorStore).deleteByMemoryIds(List.of(5L));
        verify(entryRepository).deleteByAgentCode("a1");
        ArgumentCaptor<AgentMemoryErasedEvent> captor = ArgumentCaptor.forClass(AgentMemoryErasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getScope()).isEqualTo(AgentMemoryErasedEvent.SCOPE_AGENT);
        assertThat(captor.getValue().getErasedCount()).isEqualTo(1);
        assertThat(captor.getValue().getVectorDeleted()).isEqualTo(1);
    }

    @Test
    @DisplayName("向量删除失败时关系记录仍删除且事件如实上报")
    void erase_vectorDeleteFailed_eventReportsFailure() {
        when(entryRepository.findByUserAnchor("u1")).thenReturn(List.of(entry(1L)));
        when(agentMemoryVectorStore.deleteByMemoryIds(List.of(1L))).thenReturn(-1);

        int erased = service.eraseByUser("u1");

        assertThat(erased).isEqualTo(1);
        verify(entryRepository).deleteByUserAnchor("u1");
        ArgumentCaptor<AgentMemoryErasedEvent> captor = ArgumentCaptor.forClass(AgentMemoryErasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getErasedCount()).isEqualTo(1);
        assertThat(captor.getValue().getVectorDeleted()).isEqualTo(-1);
    }

    @Test
    @DisplayName("无匹配条目时仅发布零擦除事件")
    void erase_noEntries_publishesZeroEvent() {
        when(entryRepository.findByUserAnchor("nobody")).thenReturn(List.of());

        int erased = service.eraseByUser("nobody");

        assertThat(erased).isZero();
        verifyNoInteractions(agentMemoryVectorStore);
        verify(entryRepository, never()).deleteByUserAnchor(anyString());
        ArgumentCaptor<AgentMemoryErasedEvent> captor = ArgumentCaptor.forClass(AgentMemoryErasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getErasedCount()).isZero();
        assertThat(captor.getValue().getVectorDeleted()).isEqualTo(-1);
    }

    @Test
    @DisplayName("向量库不可用时仍删除记录且事件标记未清向量")
    void erase_noVectorStore_recordsStillDeleted() {
        AgentMemoryEraseService plain = new AgentMemoryEraseService();
        inject(plain, "entryRepository", entryRepository);
        inject(plain, "eventPublisher", eventPublisher);
        when(entryRepository.findByUserAnchor("u1")).thenReturn(List.of(entry(1L)));

        int erased = plain.eraseByUser("u1");

        assertThat(erased).isEqualTo(1);
        verify(entryRepository).deleteByUserAnchor("u1");
        ArgumentCaptor<AgentMemoryErasedEvent> captor = ArgumentCaptor.forClass(AgentMemoryErasedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getVectorDeleted()).isEqualTo(-1);
    }

    private AgentMemoryEntryInfo entry(Long id) {
        AgentMemoryEntryInfo entry = new AgentMemoryEntryInfo();
        entry.setId(id);
        entry.setAgentCode("a1");
        entry.setUserAnchor("u1");
        entry.setMemoryType(AgentMemoryEntryInfo.TYPE_EPISODIC);
        entry.setContent("记忆内容");
        entry.setStatus(AgentMemoryEntryInfo.STATUS_ACTIVE);
        return entry;
    }

    private void injectField(String name, Object value) {
        inject(service, name, value);
    }

    private void inject(Object target, String name, Object value) {
        try {
            Field field = findField(target.getClass(), name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("字段注入失败: " + name, e);
        }
    }

    private Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // 继续向父类查找
            }
        }
        throw new NoSuchFieldException(name);
    }
}
