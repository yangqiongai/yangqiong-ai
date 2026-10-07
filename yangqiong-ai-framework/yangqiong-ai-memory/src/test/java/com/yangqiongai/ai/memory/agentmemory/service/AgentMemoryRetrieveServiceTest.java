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

import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryInjection;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunQuery;
import com.yangqiongai.ai.memory.agentmemory.repository.AgentMemoryEntryRepository;
import com.yangqiongai.ai.memory.agentmemory.vector.AgentMemoryVectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent运行记忆检索单元测试
 * @author yangqiong
 */
@DisplayName("Agent运行记忆检索单元测试")
class AgentMemoryRetrieveServiceTest {

    @Mock
    private AgentMemoryEntryRepository entryRepository;

    @Mock
    private AgentMemoryVectorStore agentMemoryVectorStore;

    private AgentMemoryRetrieveService service;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        service = new AgentMemoryRetrieveService();
        injectField("entryRepository", entryRepository);
        injectField("agentMemoryVectorStore", agentMemoryVectorStore);
        injectField("vectorTopK", 10);
        injectField("fallbackLimit", 20);
    }

    @Test
    @DisplayName("查询为null或预算非正时返回空注入")
    void retrieve_invalidInput_returnsEmpty() {
        assertThat(service.retrieve(null, 100).getEntries()).isEmpty();
        assertThat(service.retrieve(query("任务"), 0).getEntries()).isEmpty();
        assertThat(service.retrieve(query("任务"), -5).getEntries()).isEmpty();
    }

    @Test
    @DisplayName("向量命中与兜底锚点合并去重")
    void retrieve_vectorHitMergedWithFallback() {
        AgentMemoryEntryInfo vectorHit = activeEntry(1L, 0.9, "向量命中记忆");
        AgentMemoryEntryInfo fallbackHit = activeEntry(2L, 0.7, "兜底记忆");
        when(agentMemoryVectorStore.searchScored("任务", "a1", "u1", 10))
                .thenReturn(List.of(new AgentMemoryVectorStore.ScoredMemoryId(1L, 0.92)));
        when(entryRepository.findByIds(List.of(1L))).thenReturn(List.of(vectorHit));
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(fallbackHit));

        AgentMemoryInjection injection = service.retrieve(query("任务"), 1000);

        assertThat(injection.getEntries()).hasSize(2);
        assertThat(injection.getText()).startsWith("<memory>");
        assertThat(injection.getText()).endsWith("</memory>");
        assertThat(injection.getText()).contains("向量命中记忆").contains("兜底记忆");
        assertThat(injection.getText()).contains("[EPISODIC]");
    }

    @Test
    @DisplayName("向量检索异常降级兜底锚点检索")
    void retrieve_vectorError_fallback() {
        when(agentMemoryVectorStore.searchScored(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("向量服务超时"));
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(activeEntry(2L, 0.7, "兜底记忆")));

        AgentMemoryInjection injection = service.retrieve(query("任务"), 1000);

        assertThat(injection.getEntries()).hasSize(1);
        assertThat(injection.getEntries().get(0).getContent()).isEqualTo("兜底记忆");
    }

    @Test
    @DisplayName("非生效状态与过期TTL条目不参与检索")
    void retrieve_filtersNonActiveAndExpired() {
        AgentMemoryEntryInfo quarantined = activeEntry(1L, 0.9, "隔离记忆");
        quarantined.setStatus(AgentMemoryEntryInfo.STATUS_QUARANTINED);
        AgentMemoryEntryInfo expired = activeEntry(2L, 0.8, "过期记忆");
        expired.setTtlExpireTime(LocalDateTime.now().minusSeconds(60));
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(quarantined, expired, activeEntry(3L, 0.6, "有效记忆")));

        AgentMemoryInjection injection = service.retrieve(query("任务"), 1000);

        assertThat(injection.getEntries()).hasSize(1);
        assertThat(injection.getEntries().get(0).getContent()).isEqualTo("有效记忆");
    }

    @Test
    @DisplayName("候选按置信度降序排列")
    void retrieve_sortedByConfidenceDesc() {
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(activeEntry(1L, 0.5, "低置信"),
                        activeEntry(2L, 0.95, "高置信"),
                        activeEntry(3L, 0.7, "中置信")));

        AgentMemoryInjection injection = service.retrieve(query("任务"), 1000);

        assertThat(injection.getEntries()).extracting(AgentMemoryEntryInfo::getConfidence)
                .containsExactly(0.95, 0.7, 0.5);
    }

    @Test
    @DisplayName("Token预算不足时跳过超限条目纳入后续小条目")
    void retrieve_tokenBudgetTruncates() {
        String longContent = "长".repeat(400);
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(activeEntry(1L, 0.95, longContent),
                        activeEntry(2L, 0.6, "短记忆")));

        AgentMemoryInjection injection = service.retrieve(query("任务"), 100);

        assertThat(injection.getEntries()).hasSize(1);
        assertThat(injection.getEntries().get(0).getContent()).isEqualTo("短记忆");
        assertThat(injection.getTokenEstimate()).isGreaterThan(0);
        assertThat(injection.getTokenEstimate()).isLessThanOrEqualTo(100);
    }

    @Test
    @DisplayName("全部条目超预算时返回空且不更新访问信息")
    void retrieve_allOverBudget_returnsEmpty() {
        String longContent = "长".repeat(400);
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(activeEntry(1L, 0.95, longContent)));

        AgentMemoryInjection injection = service.retrieve(query("任务"), 10);

        assertThat(injection.getEntries()).isEmpty();
        assertThat(injection.getText()).isEmpty();
        verify(entryRepository, never()).batchUpdateAccessInfo(any());
    }

    @Test
    @DisplayName("命中条目批量记录访问信息")
    void retrieve_recordsAccessInfo() {
        when(entryRepository.findActiveByAgentAndUser("a1", "u1", 20))
                .thenReturn(List.of(activeEntry(1L, 0.9, "记忆一"), activeEntry(2L, 0.8, "记忆二")));

        service.retrieve(query("任务"), 1000);

        verify(entryRepository).batchUpdateAccessInfo(List.of(1L, 2L));
    }

    @Test
    @DisplayName("Token估算中文按两字符一Token")
    void estimateTokens() {
        assertThat(service.estimateTokens(null)).isZero();
        assertThat(service.estimateTokens("")).isZero();
        assertThat(service.estimateTokens("abcd")).isEqualTo(2);
        assertThat(service.estimateTokens("abcde")).isEqualTo(3);
    }

    private MemoryRunQuery query(String text) {
        MemoryRunQuery query = new MemoryRunQuery();
        query.setAgentCode("a1");
        query.setUserAnchor("u1");
        query.setQuery(text);
        return query;
    }

    private AgentMemoryEntryInfo activeEntry(Long id, double confidence, String content) {
        AgentMemoryEntryInfo entry = new AgentMemoryEntryInfo();
        entry.setId(id);
        entry.setAgentCode("a1");
        entry.setUserAnchor("u1");
        entry.setMemoryType(AgentMemoryEntryInfo.TYPE_EPISODIC);
        entry.setContent(content);
        entry.setConfidence(confidence);
        entry.setStatus(AgentMemoryEntryInfo.STATUS_ACTIVE);
        return entry;
    }

    private void injectField(String name, Object value) {
        try {
            Field field = findField(service.getClass(), name);
            field.setAccessible(true);
            field.set(service, value);
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
