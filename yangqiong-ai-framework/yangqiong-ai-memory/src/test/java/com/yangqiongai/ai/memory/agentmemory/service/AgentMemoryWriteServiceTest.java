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

import com.yangqiongai.ai.agent.runtime.guardrail.ContentModerationPolicy;
import com.yangqiongai.ai.agent.runtime.guardrail.ModerationVerdict;
import com.yangqiongai.ai.memory.agentmemory.event.AgentMemoryQuarantinedEvent;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryCandidate;
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
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent运行记忆写入单元测试
 * @author yangqiong
 */
@DisplayName("Agent运行记忆写入单元测试")
class AgentMemoryWriteServiceTest {

    @Mock
    private AgentMemoryEntryRepository entryRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private AgentMemoryVectorStore agentMemoryVectorStore;

    @Mock
    private ContentModerationPolicy contentModerationPolicy;

    private AgentMemoryWriteService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new AgentMemoryWriteService();
        injectField("entryRepository", entryRepository);
        injectField("eventPublisher", eventPublisher);
        injectField("agentMemoryVectorStore", agentMemoryVectorStore);
        injectField("contentModerationPolicy", contentModerationPolicy);
        injectField("writeEnabled", true);
        injectField("maxContentLength", 100);
    }

    @Test
    @DisplayName("写入开关关闭时返回null且不落库")
    void store_writeDisabled_returnsNull() {
        AgentMemoryWriteService disabled = new AgentMemoryWriteService();
        inject(disabled, "entryRepository", entryRepository);
        inject(disabled, "eventPublisher", eventPublisher);
        inject(disabled, "writeEnabled", false);

        AgentMemoryEntryInfo result = disabled.store(candidate("a1", "u1", "内容"));

        assertThat(result).isNull();
        verify(entryRepository, never()).insert(any());
    }

    @Test
    @DisplayName("候选为null返回null")
    void store_nullCandidate_returnsNull() {
        assertThat(service.store(null)).isNull();
    }

    @Test
    @DisplayName("空白内容返回null")
    void store_blankContent_returnsNull() {
        assertThat(service.store(candidate("a1", "u1", "   "))).isNull();
        assertThat(service.store(candidate("a1", "u1", null))).isNull();
    }

    @Test
    @DisplayName("缺少归属维度返回null")
    void store_missingAnchor_returnsNull() {
        assertThat(service.store(candidate(null, "u1", "内容"))).isNull();
        assertThat(service.store(candidate("a1", "  ", "内容"))).isNull();
    }

    @Test
    @DisplayName("超长内容按上限截断")
    void store_overlongContent_truncated() {
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(1L);

        String longContent = "字".repeat(150);
        AgentMemoryEntryInfo entry = service.store(candidate("a1", "u1", longContent));

        assertThat(entry.getContent()).hasSize(100);
    }

    @Test
    @DisplayName("内容审查拦截时写入隔离态并发布隔离事件")
    void store_moderationBlocked_quarantinedWithEvent() {
        when(contentModerationPolicy.moderate(any())).thenReturn(ModerationVerdict.block("敏感内容"));
        when(entryRepository.insert(any())).thenReturn(9L);

        AgentMemoryEntryInfo entry = service.store(candidate("a1", "u1", "敏感文本"));

        assertThat(entry.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_QUARANTINED);
        assertThat(entry.getEmbeddingRef()).isNull();
        verify(agentMemoryVectorStore, never()).embedAndIndex(any(), anyString(), anyString(), anyString(), anyString());
        ArgumentCaptor<AgentMemoryQuarantinedEvent> captor = ArgumentCaptor.forClass(AgentMemoryQuarantinedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().getEntryId()).isEqualTo(9L);
        assertThat(captor.getValue().getReason()).isEqualTo("敏感内容");
    }

    @Test
    @DisplayName("内容审查异常按放行处理")
    void store_moderationError_treatedAsPass() {
        when(contentModerationPolicy.moderate(any())).thenThrow(new RuntimeException("审查服务不可用"));
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(1L);

        AgentMemoryEntryInfo entry = service.store(candidate("a1", "u1", "普通内容"));

        assertThat(entry.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_ACTIVE);
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("重复内容命中去重返回既有条目不再插入")
    void store_duplicateContent_returnsExisting() {
        AgentMemoryEntryInfo existing = new AgentMemoryEntryInfo();
        existing.setId(7L);
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(existing);

        AgentMemoryEntryInfo entry = service.store(candidate("a1", "u1", "重复内容"));

        assertThat(entry).isSameAs(existing);
        verify(entryRepository, never()).insert(any());
    }

    @Test
    @DisplayName("正常写入默认类型与置信度并向量化成功")
    void store_normalWrite_activeWithEmbedding() {
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(66L);
        when(agentMemoryVectorStore.embedAndIndex(66L, "任务总结", "a1", "u1", AgentMemoryEntryInfo.TYPE_EPISODIC))
                .thenReturn(true);

        AgentMemoryEntryInfo entry = service.store(candidate("a1", "u1", "任务总结"));

        assertThat(entry.getId()).isEqualTo(66L);
        assertThat(entry.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_ACTIVE);
        assertThat(entry.getMemoryType()).isEqualTo(AgentMemoryEntryInfo.TYPE_EPISODIC);
        assertThat(entry.getConfidence()).isEqualTo(0.8);
        assertThat(entry.getEmbeddingRef()).isEqualTo("66");
        assertThat(entry.getAccessCount()).isZero();
        assertThat(entry.getVersionNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("向量化失败时embeddingRef为空但记录仍生效")
    void store_vectorIndexFailed_entryStillActive() {
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(66L);
        when(agentMemoryVectorStore.embedAndIndex(any(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(false);

        AgentMemoryEntryInfo entry = service.store(candidate("a1", "u1", "任务总结"));

        assertThat(entry.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_ACTIVE);
        assertThat(entry.getEmbeddingRef()).isNull();
    }

    @Test
    @DisplayName("向量库不可用时静默降级仍落库")
    void store_noVectorStore_entryActive() {
        AgentMemoryWriteService plain = new AgentMemoryWriteService();
        inject(plain, "entryRepository", entryRepository);
        inject(plain, "eventPublisher", eventPublisher);
        inject(plain, "writeEnabled", true);
        inject(plain, "maxContentLength", 100);
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(1L);

        AgentMemoryEntryInfo entry = plain.store(candidate("a1", "u1", "任务总结"));

        assertThat(entry.getStatus()).isEqualTo(AgentMemoryEntryInfo.STATUS_ACTIVE);
        assertThat(entry.getEmbeddingRef()).isNull();
    }

    @Test
    @DisplayName("置信度边界裁剪到0到1区间")
    void store_confidenceClamped() {
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(1L);

        AgentMemoryEntryInfo high = service.store(candidate("a1", "u1", "内容一", 1.5));
        AgentMemoryEntryInfo low = service.store(candidate("a1", "u1", "内容二", -0.5));

        assertThat(high.getConfidence()).isEqualTo(1.0);
        assertThat(low.getConfidence()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("TTL秒数有效时设置过期时间否则不设置")
    void store_ttlExpireTime() {
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(1L);

        AgentMemoryCandidate withTtl = candidate("a1", "u1", "带TTL内容");
        withTtl.setTtlSeconds(60L);
        AgentMemoryEntryInfo entryWithTtl = service.store(withTtl);

        AgentMemoryCandidate noTtl = candidate("a1", "u1", "永久内容");
        AgentMemoryEntryInfo entryNoTtl = service.store(noTtl);

        assertThat(entryWithTtl.getTtlExpireTime()).isNotNull();
        assertThat(entryNoTtl.getTtlExpireTime()).isNull();
    }

    @Test
    @DisplayName("批量写入过滤无效候选")
    void storeAll_filtersInvalid() {
        when(entryRepository.findActiveByInputHash(anyString(), anyString(), anyString())).thenReturn(null);
        when(entryRepository.insert(any())).thenReturn(1L);

        List<AgentMemoryEntryInfo> stored = service.storeAll(Arrays.asList(
                candidate("a1", "u1", "内容一"),
                candidate(null, "u1", "缺锚点"),
                candidate("a1", "u1", "   "),
                null));

        assertThat(stored).hasSize(1);
        assertThat(stored.get(0).getContent()).isEqualTo("内容一");
    }

    @Test
    @DisplayName("批量写入null列表返回空列表")
    void storeAll_nullList_returnsEmpty() {
        assertThat(service.storeAll(null)).isEmpty();
    }

    private AgentMemoryCandidate candidate(String agentCode, String userAnchor, String content) {
        AgentMemoryCandidate candidate = new AgentMemoryCandidate();
        candidate.setAgentCode(agentCode);
        candidate.setUserAnchor(userAnchor);
        candidate.setContent(content);
        return candidate;
    }

    private AgentMemoryCandidate candidate(String agentCode, String userAnchor, String content, Double confidence) {
        AgentMemoryCandidate candidate = candidate(agentCode, userAnchor, content);
        candidate.setConfidence(confidence);
        return candidate;
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
