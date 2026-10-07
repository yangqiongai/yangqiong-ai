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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.agent.harness.engine.EngineConfig;
import com.yangqiongai.agent.harness.memory.InMemoryLongTermMemory;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.model.AgentChatResponse;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Harness运行时工厂记忆装配测试
 * @author yangqiong
 */
class AgentHarnessRuntimeFactoryMemoryTest {

    /**
     * 桩模型，build 阶段不会真正调用生成方法
     */
    private AgentModel stubModel() {
        return new AgentModel() {
            @Override
            public AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                              AgentGenerateOptions options) {
                return null;
            }

            @Override
            public Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools,
                                                  AgentGenerateOptions options) {
                return Flux.empty();
            }
        };
    }

    /**
     * 通过反射获取引擎运行时的工具箱
     * @param runtime
     * @return
     * @throws Exception
     */
    private HarnessToolkit toolkitOf(AgentRuntime runtime) throws Exception {
        AgentHarnessRuntime frameworkRuntime = (AgentHarnessRuntime) runtime;
        com.yangqiongai.agent.harness.core.AgentRuntime engineRuntime = frameworkRuntime.getDelegate();
        Method m = engineRuntime.getClass().getDeclaredMethod("getEngineConfig");
        m.setAccessible(true);
        EngineConfig config = (EngineConfig) m.invoke(engineRuntime);
        return config.getToolkit();
    }

    /**
     * 注入长期记忆后构建的运行时携带三个记忆工具
     */
    @Test
    void longTermMemoryWiredRegistersMemoryTools() throws Exception {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        factory.setLongTermMemory(RuntimeSpiBridge.toRuntime(new InMemoryLongTermMemory()));
        AgentHarnessRuntimeBuilder builder = (AgentHarnessRuntimeBuilder) factory.createBuilder();
        AgentRuntime runtime = builder.name("agent").model(stubModel()).build();
        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNotNull();
        assertThat(toolkit.find("memory_store")).isNotNull();
        assertThat(toolkit.find("memory_delete")).isNotNull();
    }

    /**
     * 未注入长期记忆时不注册记忆工具
     */
    @Test
    void noLongTermMemoryRegistersNoMemoryTools() throws Exception {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        AgentHarnessRuntimeBuilder builder = (AgentHarnessRuntimeBuilder) factory.createBuilder();
        AgentRuntime runtime = builder.name("agent").model(stubModel()).build();
        HarnessToolkit toolkit = toolkitOf(runtime);
        assertThat(toolkit.find("memory_search")).isNull();
    }

    /**
     * 启用摘要压缩时构建成功且运行时名称正确
     */
    @Test
    void summaryCompactionBuildsWithoutError() {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        factory.setSummaryCompactionEnabled(true);
        AgentHarnessRuntimeBuilder builder = (AgentHarnessRuntimeBuilder) factory.createBuilder();
        AgentRuntime runtime = builder.name("agent").model(stubModel()).build();
        assertThat(runtime).isNotNull();
        assertThat(runtime.getName()).isEqualTo("agent");
    }
}
