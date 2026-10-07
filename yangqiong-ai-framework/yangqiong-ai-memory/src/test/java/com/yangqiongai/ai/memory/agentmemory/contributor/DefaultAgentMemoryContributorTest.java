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
package com.yangqiongai.ai.memory.agentmemory.contributor;

import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryCandidate;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryEntryInfo;
import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryInjection;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunOutcome;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunQuery;
import com.yangqiongai.ai.memory.agentmemory.service.AgentMemoryRetrieveService;
import com.yangqiongai.ai.memory.agentmemory.service.AgentMemoryWriteService;
import com.yangqiongai.ai.memory.agentmemory.service.OrgContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent运行记忆缺省贡献者单元测试
 * @author yangqiong
 */
@DisplayName("Agent运行记忆缺省贡献者单元测试")
class DefaultAgentMemoryContributorTest {

    @Mock
    private AgentMemoryRetrieveService retrieveService;

    @Mock
    private AgentMemoryWriteService writeService;

    @Mock
    private OrgContextService orgContextService;

    private DefaultAgentMemoryContributor contributor;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        contributor = new DefaultAgentMemoryContributor();
        injectField("retrieveService", retrieveService);
        injectField("writeService", writeService);
        injectField("orgContextService", orgContextService);
        injectField("injectTokenBudget", 512);
        injectField("episodicMaxLength", 20);
    }

    @Test
    @DisplayName("运行前合并组织上下文块与运行记忆块")
    void beforeRun_combinesOrgAndMemory() {
        AgentMemoryInjection memory = AgentMemoryInjection.empty();
        memory.setText("记忆块");
        memory.setTokenEstimate(3);
        when(retrieveService.retrieve(any(MemoryRunQuery.class), anyInt())).thenReturn(memory);
        when(orgContextService.buildInjectionBlock()).thenReturn("组织上下文块");

        AgentMemoryInjection result = contributor.beforeRun(new MemoryRunQuery());

        assertThat(result.getText()).startsWith("组织上下文块");
        assertThat(result.getText()).contains("记忆块");
        assertThat(result.getTokenEstimate()).isGreaterThan(3);
    }

    @Test
    @DisplayName("组织上下文为空时仅保留记忆块")
    void beforeRun_emptyOrgBlock_keepsMemoryOnly() {
        AgentMemoryInjection memory = AgentMemoryInjection.empty();
        memory.setText("记忆块");
        memory.setTokenEstimate(3);
        when(retrieveService.retrieve(any(MemoryRunQuery.class), anyInt())).thenReturn(memory);
        when(orgContextService.buildInjectionBlock()).thenReturn("");

        AgentMemoryInjection result = contributor.beforeRun(new MemoryRunQuery());

        assertThat(result.getText()).isEqualTo("记忆块");
    }

    @Test
    @DisplayName("运行后产出情景记忆候选并关联任务来源")
    void afterRun_producesEpisodicCandidate() {
        MemoryRunOutcome outcome = new MemoryRunOutcome();
        outcome.setAgentCode("a1");
        outcome.setUserAnchor("u1");
        outcome.setSessionId("s1");
        outcome.setTaskId("t1");
        outcome.setInputText("任务输入");
        outcome.setOutputText("任务输出");

        contributor.afterRun(outcome);

        ArgumentCaptor<AgentMemoryCandidate> captor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(writeService).store(captor.capture());
        AgentMemoryCandidate candidate = captor.getValue();
        assertThat(candidate.getAgentCode()).isEqualTo("a1");
        assertThat(candidate.getUserAnchor()).isEqualTo("u1");
        assertThat(candidate.getSessionId()).isEqualTo("s1");
        assertThat(candidate.getSourceTaskId()).isEqualTo("t1");
        assertThat(candidate.getMemoryType()).isEqualTo(AgentMemoryEntryInfo.TYPE_EPISODIC);
        assertThat(candidate.getContent()).contains("任务输入").contains("任务输出");
    }

    @Test
    @DisplayName("运行输出为空白时不产出记忆")
    void afterRun_blankOutput_skips() {
        MemoryRunOutcome outcome = new MemoryRunOutcome();
        outcome.setAgentCode("a1");
        outcome.setUserAnchor("u1");
        outcome.setOutputText("   ");

        contributor.afterRun(outcome);
        contributor.afterRun(null);

        verifyNoInteractions(writeService);
    }

    @Test
    @DisplayName("长输入输出按上限截断")
    void afterRun_longText_abbreviated() {
        MemoryRunOutcome outcome = new MemoryRunOutcome();
        outcome.setAgentCode("a1");
        outcome.setUserAnchor("u1");
        outcome.setInputText("入".repeat(50));
        outcome.setOutputText("出".repeat(50));

        contributor.afterRun(outcome);

        ArgumentCaptor<AgentMemoryCandidate> captor = ArgumentCaptor.forClass(AgentMemoryCandidate.class);
        verify(writeService).store(captor.capture());
        String content = captor.getValue().getContent();
        assertThat(content).contains("...");
        assertThat(content.length()).isLessThan(120);
    }

    @Test
    @DisplayName("记忆产出异常不外抛影响主流程")
    void afterRun_storeError_swallowed() {
        MemoryRunOutcome outcome = new MemoryRunOutcome();
        outcome.setAgentCode("a1");
        outcome.setUserAnchor("u1");
        outcome.setOutputText("任务输出");
        when(writeService.store(any(AgentMemoryCandidate.class))).thenThrow(new RuntimeException("写入失败"));

        assertThatCode(() -> contributor.afterRun(outcome)).doesNotThrowAnyException();
    }

    private void injectField(String name, Object value) {
        try {
            Field field = findField(contributor.getClass(), name);
            field.setAccessible(true);
            field.set(contributor, value);
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
