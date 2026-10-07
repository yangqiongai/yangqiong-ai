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
package com.yangqiongai.ai.agent.core.processor;

import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.provider.GraphRetrievalProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Agent图谱检索配置解析与注入单元测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Agent图谱检索配置单元测试")
class AgentGraphRetrievalConfigTest {

    @Mock
    private AgentManager agentManager;

    @Mock
    private ObjectProvider<GraphRetrievalProvider> providerHolder;

    @Mock
    private GraphRetrievalProvider provider;

    private TestAgentProcessor processor;

    /**
     * 最小化测试处理器（仅暴露配置解析行为）
     */
    static class TestAgentProcessor extends AbstractAgentProcessor {

        @Override
        public String getAgentCode() {
            return "test-agent";
        }

        @Override
        protected List<com.yangqiongai.ai.agent.runtime.message.AgentMessage> buildInputMessages(AgentRequest request) {
            return List.of();
        }
    }

    /**
     * 构造带配置的Agent
     * @param configJson
     * @return
     */
    private Agent agentWithConfig(String configJson) {
        Agent agent = new Agent();
        agent.setAgentCode("agent-a");
        agent.setAgentConfig(configJson);
        return agent;
    }

    /**
     * 构造测试处理器（provider可用性由各用例按需stub）
     */
    private TestAgentProcessor processorWithProvider() {
        TestAgentProcessor p = new TestAgentProcessor();
        p.agentManager = agentManager;
        p.graphRetrievalProvider = providerHolder;
        return p;
    }

    /**
     * stub图谱检索提供者可用
     */
    private void stubProviderAvailable() {
        when(providerHolder.getIfAvailable()).thenReturn(provider);
    }

    @Test
    @DisplayName("agentConfig开启图谱检索时注入图谱上下文")
    void enabledConfig_retrievesAndInjects() {
        processor = processorWithProvider();
        stubProviderAvailable();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[\"kb-1\"],\"mode\":\"GLOBAL\",\"topK\":8}}"));
        when(provider.retrieve(eq("预算是多少"), eq(List.of("kb-1")), eq("GLOBAL"), eq(8)))
                .thenReturn("=== Entities ===\n- 预算 [concept]");

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        processor.enrichFromAgentConfig(request);

        assertThat(request.getKnowledgeContext())
                .isEqualTo("=== Entities ===\n- 预算 [concept]");
    }

    @Test
    @DisplayName("agentConfig未配置graphRetrieval时默认关闭不检索")
    void noConfig_skipsRetrieval() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig("{\"model\":\"qwen-max\"}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        processor.enrichFromAgentConfig(request);

        verifyNoInteractions(provider);
        assertThat(request.getKnowledgeContext()).isNull();
    }

    @Test
    @DisplayName("agent级开关为false时不检索")
    void agentLevelDisabled_skipsRetrieval() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":false,\"kbCodes\":[\"kb-1\"]}}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        processor.enrichFromAgentConfig(request);

        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("请求级覆盖优先于agent级配置（agent级关闭仍可被请求级开启）")
    void requestOverride_takesPrecedenceOverAgentConfig() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":false,\"kbCodes\":[\"kb-1\"],\"mode\":\"LOCAL\",\"topK\":5}}"));
        stubProviderAvailable();
        // append模式：请求级enabled/mode/topK覆盖agent级，kbCodes与agentConfig清单取并集
        when(provider.retrieve(eq("预算是多少"), eq(List.of("kb-1", "kb-2")), eq("HYBRID"), eq(20)))
                .thenReturn("图谱上下文");

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        request.getBody().put("_agentOverrides", Map.of("graphRetrieval",
                Map.of("enabled", true, "kbCodes", List.of("kb-2"), "mode", "HYBRID", "topK", 20)));
        processor.enrichFromAgentConfig(request);

        assertThat(request.getKnowledgeContext()).isEqualTo("图谱上下文");
    }

    @Test
    @DisplayName("agentConfig为空时请求级覆盖仍生效")
    void emptyAgentConfig_requestOverrideStillApplies() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(null);
        stubProviderAvailable();
        when(provider.retrieve(anyString(), anyList(), anyString(), anyInt())).thenReturn("图谱上下文");

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        request.getBody().put("_agentOverrides", Map.of("graphRetrieval",
                Map.of("enabled", true, "kbCodes", List.of("kb-9"))));
        processor.enrichFromAgentConfig(request);

        assertThat(request.getKnowledgeContext()).isEqualTo("图谱上下文");
    }

    @Test
    @DisplayName("追加模式下请求级kbCodes与agentConfig清单取并集")
    void appendMode_mergesKbCodes() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[\"kb-1\"]}}"));
        stubProviderAvailable();
        when(provider.retrieve(eq("预算是多少"), eq(List.of("kb-1", "kb-2")), eq(""), eq(10)))
                .thenReturn("图谱上下文");

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        request.getBody().put("_agentOverrides", Map.of("graphRetrieval",
                Map.of("kbCodes", List.of("kb-2"))));
        processor.enrichFromAgentConfig(request);

        assertThat(request.getKnowledgeContext()).isEqualTo("图谱上下文");
    }

    @Test
    @DisplayName("替换模式下请求级kbCodes替换agentConfig清单")
    void replaceMode_replacesKbCodes() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"bindingMode\":\"replace\",\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[\"kb-1\"]}}"));
        stubProviderAvailable();
        when(provider.retrieve(eq("预算是多少"), eq(List.of("kb-2")), eq(""), eq(10)))
                .thenReturn("图谱上下文");

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        request.getBody().put("_agentOverrides", Map.of("graphRetrieval",
                Map.of("kbCodes", List.of("kb-2"))));
        processor.enrichFromAgentConfig(request);

        assertThat(request.getKnowledgeContext()).isEqualTo("图谱上下文");
    }

    @Test
    @DisplayName("已有知识上下文时图谱上下文以空行分隔追加")
    void existingKnowledgeContext_appendsWithSeparator() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[\"kb-1\"]}}"));
        stubProviderAvailable();
        when(provider.retrieve(anyString(), anyList(), anyString(), anyInt())).thenReturn("图谱上下文");

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        request.getBody().put(AgentRequest.BodyKeys.KNOWLEDGE_CONTEXT, "知识上下文");
        processor.enrichFromAgentConfig(request);

        assertThat(request.getKnowledgeContext()).isEqualTo("知识上下文\n\n图谱上下文");
    }

    @Test
    @DisplayName("图谱检索提供者未装配时跳过且不抛异常")
    void providerMissing_skipsSilently() {
        processor = new TestAgentProcessor();
        processor.agentManager = agentManager;
        processor.graphRetrievalProvider = providerHolder;
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[\"kb-1\"]}}"));
        when(providerHolder.getIfAvailable()).thenReturn(null);

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");

        assertThatCode(() -> processor.enrichFromAgentConfig(request)).doesNotThrowAnyException();
        assertThat(request.getKnowledgeContext()).isNull();
    }

    @Test
    @DisplayName("kbCodes为空时不触发检索")
    void emptyKbCodes_skipsRetrieval() {
        processor = processorWithProvider();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[]}}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("预算是多少");
        processor.enrichFromAgentConfig(request);

        verifyNoInteractions(provider);
    }

    @Test
    @DisplayName("请求输入为空时不触发检索")
    void blankQuery_skipsRetrieval() {
        processor = processorWithProvider();
        stubProviderAvailable();
        when(agentManager.getByCode("agent-a")).thenReturn(agentWithConfig(
                "{\"graphRetrieval\":{\"enabled\":true,\"kbCodes\":[\"kb-1\"]}}"));

        AgentRequest request = new AgentRequest().agentCode("agent-a").input("   ");
        processor.enrichFromAgentConfig(request);

        verify(providerHolder).getIfAvailable();
        verify(provider, never()).retrieve(anyString(), anyList(), anyString(), anyInt());
    }
}
