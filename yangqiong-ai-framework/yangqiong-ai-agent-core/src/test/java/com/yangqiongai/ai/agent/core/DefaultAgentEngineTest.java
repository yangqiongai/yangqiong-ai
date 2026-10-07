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
package com.yangqiongai.ai.agent.core;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.context.SessionContext;
import com.yangqiongai.ai.agent.core.executor.ClarificationPendingRegistry;
import com.yangqiongai.ai.agent.core.executor.ConfirmPendingRegistry;
import com.yangqiongai.ai.agent.core.executor.InMemoryPendingResumeStore;
import com.yangqiongai.ai.agent.core.executor.PendingResumeStore;
import com.yangqiongai.ai.agent.core.executor.ReActAgentExecutor;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.processor.AgentProcessor;
import com.yangqiongai.ai.agent.core.task.AgentTaskTracker;
import com.yangqiongai.ai.agent.core.task.StepRecordingManager;
import com.yangqiongai.ai.common.exception.AiErrorCode;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.common.sse.StreamEvent;
import com.yangqiongai.ai.common.scope.ScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * DefaultAgentEngine单元测试
 */
@ExtendWith(MockitoExtension.class)
class DefaultAgentEngineTest {

    @Mock
    private AgentProcessor agentProcessor;

    @Mock
    private AgentTaskTracker taskTracker;

    @Mock
    private StepRecordingManager stepRecordingManager;

    @Mock
    private ReActAgentExecutor reActAgentExecutor;

    private PendingResumeStore pendingResumeStore;

    private DefaultAgentEngine engine;

    private static final String AGENT_CODE = "chat";

    @BeforeEach
    void setUp() {
        when(agentProcessor.getAgentCode()).thenReturn(AGENT_CODE);
        pendingResumeStore = new InMemoryPendingResumeStore();
        engine = new DefaultAgentEngine(
                List.of(agentProcessor),
                taskTracker,
                stepRecordingManager,
                new ClarificationPendingRegistry(pendingResumeStore),
                new ConfirmPendingRegistry(pendingResumeStore),
                reActAgentExecutor
        );
    }

    @AfterEach
    void tearDown() {
        ScopeContext.clear();
        SessionContext.clear();
    }

    private AgentRequest buildRequest(String agentCode) {
        return new AgentRequest()
                .agentCode(agentCode)
                .sessionId("session-1")
                .input("hello")
                .userId("user-1");
    }

    @Test
    @DisplayName("execute() 路由到正确的TaskProcessor")
    void execute_routesToCorrectProcessor() {
        
        AgentContext context = new AgentContext(buildRequest(AGENT_CODE));
        when(agentProcessor.createAgentContext(any(AgentRequest.class))).thenReturn(context);
        when(agentProcessor.process(any(AgentContext.class))).thenReturn(AgentResult.success("ok"));

        AgentResult result = engine.run(buildRequest(AGENT_CODE));

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutputAsText()).isEqualTo("ok");
        verify(agentProcessor).createAgentContext(any(AgentRequest.class));
        verify(agentProcessor).process(any(AgentContext.class));
    }

    @Test
    @DisplayName("execute() 请求携带知识证据时挂载到结果finalPayload")
    void execute_withKnowledgeEvidences_attachesToFinalPayload() {
        AgentRequest request = buildRequest(AGENT_CODE);
        Map<String, Object> evidence = Map.of(
                "content", "shell是命令行解释器",
                "kbId", "kb-1",
                "kbName", "E2E资源管理模式验证库",
                "sourceDocId", "doc-1",
                "sourceDocName", "TLCL-25.12A.pdf",
                "sliceId", "slice-1",
                "score", 0.72
        );
        request.addBody(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES, List.of(evidence));
        AgentContext context = new AgentContext(request);
        when(agentProcessor.createAgentContext(any(AgentRequest.class))).thenReturn(context);
        when(agentProcessor.process(any(AgentContext.class))).thenReturn(AgentResult.success("ok"));

        AgentResult result = engine.run(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getFinalPayload()).isNotNull();
        assertThat(result.getFinalPayload()).containsKey(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES);
        assertThat((List<?>) result.getFinalPayload().get(AgentRequest.BodyKeys.KNOWLEDGE_EVIDENCES)).hasSize(1);
    }

    @Test
    @DisplayName("execute() 无知识证据时finalPayload保持为null")
    void execute_withoutKnowledgeEvidences_finalPayloadNull() {
        AgentContext context = new AgentContext(buildRequest(AGENT_CODE));
        when(agentProcessor.createAgentContext(any(AgentRequest.class))).thenReturn(context);
        when(agentProcessor.process(any(AgentContext.class))).thenReturn(AgentResult.success("ok"));

        AgentResult result = engine.run(buildRequest(AGENT_CODE));

        assertThat(result.getFinalPayload()).isNull();
    }

    @Test
    @DisplayName("execute() 未知agentCode抛出AiException(AGENT_TASK_NOT_FOUND)")
    void execute_unknownAgentCode_throwsAiException() {
        assertThatThrownBy(() -> engine.run(buildRequest("unknown")))
                .isInstanceOf(AiException.class)
                .satisfies(ex -> {
                    AiException aiEx = (AiException) ex;
                    assertThat(aiEx.getCode()).isEqualTo(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode());
                });
    }

    @Test
    @DisplayName("stream() 返回AgentProcessor的Flux")
    void stream_returnsFluxFromProcessor() {
        AgentContext context = new AgentContext(buildRequest(AGENT_CODE));
        when(agentProcessor.createAgentContext(any(AgentRequest.class))).thenReturn(context);

        StreamEvent event = StreamEvent.textDelta("hello");
        when(agentProcessor.stream(any(AgentContext.class))).thenReturn(Flux.just(event));

        Flux<StreamEvent> flux = engine.stream(buildRequest(AGENT_CODE));

        List<StreamEvent> events = flux.collectList().block();
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getPayload()).isEqualTo("hello");
        verify(agentProcessor).stream(any(AgentContext.class));
    }

    @Test
    @DisplayName("submit() 生成taskId并委托给tracker")
    void submit_generatesTaskIdAndDelegates() {
        String taskId = engine.submitTask(buildRequest(AGENT_CODE));

        assertThat(taskId).isNotBlank();
        verify(taskTracker).submit(eq(taskId), any(AgentRequest.class), any(), eq(true));
    }

    @Test
    @DisplayName("submit() 捕获主线程ScopeContext并在异步Callable内恢复scopeId")
    void submit_capturesScopeContextForAsyncTask() throws Exception {
        ScopeContext.setScopeId("scope-from-context");
        AgentRequest request = buildRequest(AGENT_CODE);

        AgentContext context = new AgentContext(request);
        AtomicReference<String> scopeIdDuringProcess = new AtomicReference<>();
        when(agentProcessor.createAgentContext(any(AgentRequest.class))).thenReturn(context);
        when(agentProcessor.process(any(AgentContext.class))).thenAnswer(invocation -> {
            // 在processor执行期间捕获当前线程的scopeId
            scopeIdDuringProcess.set(ScopeContext.getScopeId());
            return AgentResult.success("ok");
        });

        engine.submitTask(request);

        ArgumentCaptor<Callable<AgentResult>> captor = ArgumentCaptor.forClass(Callable.class);
        verify(taskTracker).submit(any(String.class), any(AgentRequest.class), captor.capture(), eq(true));

        CountDownLatch latch = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                captor.getValue().call();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });
        worker.start();
        latch.await(2, TimeUnit.SECONDS);

        // 异步Callable执行期间，ScopeContext应为主线程捕获的值
        assertThat(scopeIdDuringProcess.get()).isEqualTo("scope-from-context");
    }

    @Test
    @DisplayName("submit() 优先使用AgentRequest.scopeId覆盖ScopeContext")
    void submit_prefersRequestScopeIdOverContext() throws Exception {
        ScopeContext.setScopeId("scope-from-context");
        AgentRequest request = buildRequest(AGENT_CODE)
                .scopeId("scope-from-request");

        AgentContext context = new AgentContext(request);
        AtomicReference<String> scopeIdDuringProcess = new AtomicReference<>();
        when(agentProcessor.createAgentContext(any(AgentRequest.class))).thenReturn(context);
        when(agentProcessor.process(any(AgentContext.class))).thenAnswer(invocation -> {
            scopeIdDuringProcess.set(ScopeContext.getScopeId());
            return AgentResult.success("ok");
        });

        engine.submitTask(request);

        ArgumentCaptor<Callable<AgentResult>> captor = ArgumentCaptor.forClass(Callable.class);
        verify(taskTracker).submit(any(String.class), any(AgentRequest.class), captor.capture(), eq(true));

        CountDownLatch latch = new CountDownLatch(1);
        Thread worker = new Thread(() -> {
            try {
                captor.getValue().call();
            } catch (Exception ignored) {
            } finally {
                latch.countDown();
            }
        });
        worker.start();
        latch.await(2, TimeUnit.SECONDS);

        // AgentRequest.scopeId优先级高于ScopeContext
        assertThat(scopeIdDuringProcess.get()).isEqualTo("scope-from-request");
    }

    @Test
    @DisplayName("空processorMap时任何agentCode都失败")
    void emptyProcessorMap_anyAgentCodeFails() {
        DefaultAgentEngine emptyEngine = new DefaultAgentEngine(
                Collections.emptyList(),
                taskTracker,
                stepRecordingManager,
                new ClarificationPendingRegistry(pendingResumeStore),
                new ConfirmPendingRegistry(pendingResumeStore),
                reActAgentExecutor
        );

        assertThatThrownBy(() -> emptyEngine.run(buildRequest("any")))
                .isInstanceOf(AiException.class)
                .satisfies(ex -> {
                    AiException aiEx = (AiException) ex;
                    assertThat(aiEx.getCode()).isEqualTo(AiErrorCode.AGENT_TASK_NOT_FOUND.getCode());
                });
    }
}
