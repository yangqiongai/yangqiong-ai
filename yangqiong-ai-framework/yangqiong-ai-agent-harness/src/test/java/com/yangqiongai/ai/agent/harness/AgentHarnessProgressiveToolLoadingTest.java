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

import com.yangqiongai.agent.harness.HarnessRuntimeBuilder;
import com.yangqiongai.agent.harness.engine.EngineConfig;
import com.yangqiongai.agent.harness.tool.HarnessToolkit;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.HarnessAgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.model.AgentChatResponse;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;
import com.yangqiongai.ai.agent.runtime.model.AgentModel;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工具渐进加载桥接装配测试
 * @author yangqiong
 */
class AgentHarnessProgressiveToolLoadingTest {

    /**
     * 桩模型，build阶段不会真正调用生成方法
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
     * 通过反射获取引擎运行时的EngineConfig
     * @param runtime
     * @return
     * @throws Exception
     */
    private EngineConfig configOf(AgentRuntime runtime) throws Exception {
        AgentHarnessRuntime frameworkRuntime = (AgentHarnessRuntime) runtime;
        com.yangqiongai.agent.harness.core.AgentRuntime engineRuntime = frameworkRuntime.getDelegate();
        Method m = engineRuntime.getClass().getDeclaredMethod("getEngineConfig");
        m.setAccessible(true);
        return (EngineConfig) m.invoke(engineRuntime);
    }

    /**
     * 枚举转换FULL与PROGRESSIVE双向正确，null透传
     */
    @Test
    void toolLoadingModeConversionBothDirections() {
        for (HarnessAgentRuntimeBuilder.ToolLoadingMode mode : HarnessAgentRuntimeBuilder.ToolLoadingMode.values()) {
            assertThat(RuntimeTypeConverter.toHarness(mode).name()).isEqualTo(mode.name());
            assertThat(RuntimeTypeConverter.toRuntime(RuntimeTypeConverter.toHarness(mode))).isEqualTo(mode);
        }
        assertThat(RuntimeTypeConverter.toHarness((HarnessAgentRuntimeBuilder.ToolLoadingMode) null)).isNull();
        assertThat(RuntimeTypeConverter
                .toRuntime((com.yangqiongai.agent.harness.config.ToolLoadingMode) null)).isNull();
    }

    /**
     * 桥接方法将模式与常驻名单透传到引擎构建器
     */
    @Test
    void bridgePropagatesModeAndAlwaysOnTools() {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        AgentHarnessRuntimeBuilder builder = (AgentHarnessRuntimeBuilder) factory.createBuilder();
        builder.toolLoadingMode(HarnessAgentRuntimeBuilder.ToolLoadingMode.PROGRESSIVE)
                .alwaysOnTools(Set.of("calculator", "date_time"));

        HarnessRuntimeBuilder delegate = builder.getDelegate();
        assertThat(delegate.getToolLoadingMode())
                .isEqualTo(com.yangqiongai.agent.harness.config.ToolLoadingMode.PROGRESSIVE);
        assertThat(delegate.getAlwaysOnTools()).containsExactlyInAnyOrder("calculator", "date_time");
    }

    /**
     * null模式不覆盖引擎默认FULL
     */
    @Test
    void bridgeKeepsFullDefaultOnNullMode() {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        AgentHarnessRuntimeBuilder builder = (AgentHarnessRuntimeBuilder) factory.createBuilder();
        builder.toolLoadingMode(null);

        assertThat(builder.getDelegate().getToolLoadingMode())
                .isEqualTo(com.yangqiongai.agent.harness.config.ToolLoadingMode.FULL);
    }

    /**
     * 默认配置下构建的运行时为全量下发（无渐进状态且不注册load_tool）
     */
    @Test
    void defaultFactoryBuildsFullLoadingRuntime() throws Exception {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        AgentRuntime runtime = ((HarnessAgentRuntimeBuilder) factory.createBuilder())
                .name("agent").model(stubModel()).build();
        EngineConfig config = configOf(runtime);
        assertThat(config.getToolLoadingState()).isNull();
        assertThat(config.getToolkit().find("load_tool")).isNull();
    }

    /**
     * PROGRESSIVE配置经工厂装配后构建的运行时注册load_tool并携带渐进状态
     */
    @Test
    void progressiveFactoryWiresStateAndMetaTool() throws Exception {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        factory.setToolLoadingMode(HarnessAgentRuntimeBuilder.ToolLoadingMode.PROGRESSIVE);
        factory.setAlwaysOnTools(Set.of("calculator"));
        AgentRuntime runtime = ((HarnessAgentRuntimeBuilder) factory.createBuilder())
                .name("agent").model(stubModel()).build();

        EngineConfig config = configOf(runtime);
        assertThat(config.getToolLoadingState()).isNotNull();
        assertThat(config.getToolLoadingState().isProgressive()).isTrue();
        assertThat(config.getToolLoadingState().getAlwaysOnTools()).containsExactly("calculator");
        HarnessToolkit toolkit = config.getToolkit();
        assertThat(toolkit.find("load_tool")).isNotNull();
        // 常驻工具仍可见，未配置的工具被裁剪
        assertThat(config.getToolLoadingState().isSchemaVisible("calculator")).isTrue();
        assertThat(config.getToolLoadingState().isSchemaVisible("load_tool")).isTrue();
    }

    /**
     * 显式FULL配置同样不产生渐进状态
     */
    @Test
    void explicitFullFactoryKeepsFullLoading() throws Exception {
        AgentHarnessRuntimeFactory factory = new AgentHarnessRuntimeFactory();
        factory.setToolLoadingMode(HarnessAgentRuntimeBuilder.ToolLoadingMode.FULL);
        AgentRuntime runtime = ((HarnessAgentRuntimeBuilder) factory.createBuilder())
                .name("agent").model(stubModel()).build();
        assertThat(configOf(runtime).getToolLoadingState()).isNull();
    }
}
