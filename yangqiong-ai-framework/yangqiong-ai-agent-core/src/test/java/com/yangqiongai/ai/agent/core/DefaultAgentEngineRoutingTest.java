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

import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.core.processor.AgentProcessor;
import com.yangqiongai.ai.common.sse.StreamEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 引擎处理器路由测试（agentConfig.processor > agentCode匹配 > default）
 * @author yangqiong
 */
class DefaultAgentEngineRoutingTest {

    private DefaultAgentEngine engine;

    private AgentManager agentService;

    private AgentProcessor defaultProcessor;

    private AgentProcessor directProcessor;

    private AgentProcessor text2SqlProcessor;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        defaultProcessor = mockProcessor("default");
        directProcessor = mockProcessor("directLlm");
        text2SqlProcessor = mockProcessor("text2sql");
        engine = new DefaultAgentEngine(List.of(defaultProcessor, directProcessor, text2SqlProcessor),
                null, null, null, null, null);
        agentService = mock(AgentManager.class);
        ReflectionTestUtils.setField(engine, "agentService", agentService);
    }

    /**
     * 构建指定编码的mock处理器
     * @param agentCode
     * @return
     */
    private AgentProcessor mockProcessor(String agentCode) {
        AgentProcessor processor = mock(AgentProcessor.class);
        when(processor.getAgentCode()).thenReturn(agentCode);
        return processor;
    }

    /**
     * 配置指定编码Agent的数据库记录
     * @param agentCode
     * @param agentConfig
     * @param status
     */
    private void stubAgent(String agentCode, String agentConfig, Integer status) {
        if (agentConfig == null && status == null) {
            when(agentService.getByCode(agentCode)).thenReturn(null);
            return;
        }
        Agent agent = new Agent();
        agent.setAgentCode(agentCode);
        agent.setAgentConfig(agentConfig);
        agent.setStatus(status);
        when(agentService.getByCode(agentCode)).thenReturn(agent);
    }

    /**
     * 反射调用私有路由方法
     * @param agentCode
     * @return
     */
    private AgentProcessor resolve(String agentCode) {
        return ReflectionTestUtils.invokeMethod(engine, "resolveProcessor", agentCode);
    }

    @Test
    @DisplayName("agentCode精确匹配注册码时路由到对应处理器")
    void agentCodeMatchShouldRoute() {
        stubAgent("text2sql", null, 1);

        assertThat(resolve("text2sql")).isSameAs(text2SqlProcessor);
        assertThat(resolve("directLlm")).isSameAs(directProcessor);
    }

    @Test
    @DisplayName("agentConfig.processor显式指定时优先于agentCode匹配")
    void configuredProcessorShouldWin() {
        stubAgent("text2sql", "{\"processor\":\"directLlm\"}", 1);

        assertThat(resolve("text2sql")).isSameAs(directProcessor);
    }

    @Test
    @DisplayName("agentConfig指定未注册处理器时忽略配置走agentCode匹配")
    void unknownConfiguredProcessorShouldFallback() {
        stubAgent("text2sql", "{\"processor\":\"not-exists\"}", 1);

        assertThat(resolve("text2sql")).isSameAs(text2SqlProcessor);
    }

    @Test
    @DisplayName("agentCode不在注册表且无配置时回退默认处理器")
    void unmatchedAgentCodeShouldFallbackDefault() {
        stubAgent("biz-agent", null, 1);

        assertThat(resolve("biz-agent")).isSameAs(defaultProcessor);
    }

    @Test
    @DisplayName("agentConfig.processor指定的任意Agent可路由到text2sql处理器")
    void businessAgentCanConfigureProcessor() {
        stubAgent("biz-agent", "{\"processor\":\"text2sql\"}", 1);

        assertThat(resolve("biz-agent")).isSameAs(text2SqlProcessor);
    }

    @Test
    @DisplayName("agentCode为空时路由默认处理器")
    void nullAgentCodeShouldRouteDefault() {
        stubAgent("default", null, 1);

        assertThat(resolve(null)).isSameAs(defaultProcessor);
    }

    @Test
    @DisplayName("agentConfig非法JSON时回退agentCode匹配")
    void brokenAgentConfigShouldFallback() {
        stubAgent("text2sql", "not-json", 1);

        assertThat(resolve("text2sql")).isSameAs(text2SqlProcessor);
    }

    @Test
    @DisplayName("processor为空串或非字符串时走默认路由")
    void blankProcessorShouldIgnored() {
        stubAgent("text2sql", "{\"processor\":\"\"}", 1);

        assertThat(resolve("text2sql")).isSameAs(text2SqlProcessor);
    }

    @Test
    @DisplayName("Agent禁用状态时拒绝执行")
    void disabledAgentShouldReject() {
        stubAgent("text2sql", null, 0);

        assertThatThrownBy(() -> resolve("text2sql"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("已禁用");
    }

    @Test
    @DisplayName("数据库不可用时降级跳过配置与状态校验走agentCode匹配")
    void dbUnavailableShouldDegrade() {
        when(agentService.getByCode("text2sql")).thenThrow(new RuntimeException("db down"));

        assertThat(resolve("text2sql")).isSameAs(text2SqlProcessor);
    }

    @Test
    @DisplayName("AgentManager处理器清单来自内存注册不查库")
    void listProcessorOptionsShouldFromMemory() {
        // mock类简名带Mockito代理后缀，清单name取真实类简名需用桩类验证
        AgentManager manager = new AgentManager();
        ReflectionTestUtils.setField(manager, "processors",
                List.of(new StubProcessor("default"), new StubProcessor("directLlm"), new StubProcessor("text2sql")));

        List<Map<String, Object>> options = manager.listProcessorOptions();

        assertThat(options).hasSize(3);
        assertThat(options.get(2).get("code")).isEqualTo("text2sql");
        assertThat(options.get(2).get("name")).isEqualTo("StubProcessor");
    }

    /**
     * 处理器清单测试桩
     */
    private static class StubProcessor implements AgentProcessor {

        /**
         * 注册编码
         */
        private final String code;

        /**
         * @param code
         */
        private StubProcessor(String code) {
            this.code = code;
        }

        @Override
        public String getAgentCode() {
            return code;
        }

        @Override
        public AgentContext createAgentContext(AgentRequest request) {
            return null;
        }

        @Override
        public AgentResult process(AgentContext context) {
            return null;
        }

        @Override
        public Flux<StreamEvent> stream(AgentContext context) {
            return Flux.empty();
        }
    }
}
