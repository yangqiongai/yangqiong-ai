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
import com.yangqiongai.agent.harness.paradigms.PlanExecuteEngine;
import com.yangqiongai.agent.harness.paradigms.ReWooEngine;
import com.yangqiongai.agent.harness.paradigms.ReflexionEngine;
import com.yangqiongai.agent.harness.paradigms.RouterEngine;
import com.yangqiongai.agent.harness.paradigms.SelfAskEngine;
import com.yangqiongai.agent.harness.paradigms.SelfRefineEngine;
import com.yangqiongai.ai.agent.runtime.spi.ParadigmSpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 执行范式接线单元测试
 * @author yangqiong
 */
class AgentHarnessRuntimeParadigmTest {

    /**
     * 读取委托构建器中被替换的执行循环，未替换时返回null
     * @param builder
     * @return
     */
    private com.yangqiongai.agent.harness.engine.AgentLoop readInjectedLoop(AgentHarnessRuntimeBuilder builder)
            throws Exception {
        Field field = HarnessRuntimeBuilder.class.getDeclaredField("agentLoop");
        field.setAccessible(true);
        return (com.yangqiongai.agent.harness.engine.AgentLoop) field.get(builder.getDelegate());
    }

    /**
     * 构建适配器与委托构建器
     * @return
     */
    private AgentHarnessRuntimeBuilder newBuilder() {
        return new AgentHarnessRuntimeBuilder(new HarnessRuntimeBuilder());
    }

    @Test
    @DisplayName("reflexion范式应替换为ReflexionEngine并透传步数与反思次数")
    void executionParadigm_reflexion_shouldInjectEngineWithOptions() throws Exception {
        AgentHarnessRuntimeBuilder builder = newBuilder();
        builder.executionParadigm(ParadigmSpec.of("reflexion", 8, 3, null));
        com.yangqiongai.agent.harness.engine.AgentLoop loop = readInjectedLoop(builder);
        assertThat(loop).isInstanceOf(ReflexionEngine.class);
        Field optionsField = ReflexionEngine.class.getDeclaredField("options");
        optionsField.setAccessible(true);
        com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions options =
                (com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions) optionsField.get(loop);
        assertThat(options.getMaxSteps()).isEqualTo(8);
        assertThat(options.getMaxReflections()).isEqualTo(3);
    }

    @Test
    @DisplayName("self-refine范式应替换为SelfRefineEngine并透传修订次数")
    void executionParadigm_selfRefine_shouldInjectEngineWithRefinements() throws Exception {
        AgentHarnessRuntimeBuilder builder = newBuilder();
        builder.executionParadigm(ParadigmSpec.of("self-refine", null, null, 4));
        com.yangqiongai.agent.harness.engine.AgentLoop loop = readInjectedLoop(builder);
        assertThat(loop).isInstanceOf(SelfRefineEngine.class);
        Field optionsField = SelfRefineEngine.class.getDeclaredField("options");
        optionsField.setAccessible(true);
        com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions options =
                (com.yangqiongai.agent.harness.paradigms.support.ParadigmOptions) optionsField.get(loop);
        assertThat(options.getMaxRefinements()).isEqualTo(4);
        assertThat(options.getMaxSteps()).isEqualTo(10);
    }

    @Test
    @DisplayName("规划类范式应按类型映射对应引擎")
    void executionParadigm_planningTypes_shouldMapEngines() throws Exception {
        AgentHarnessRuntimeBuilder planBuilder = newBuilder();
        planBuilder.executionParadigm(ParadigmSpec.ofType("plan-execute"));
        assertThat(readInjectedLoop(planBuilder)).isInstanceOf(PlanExecuteEngine.class);

        AgentHarnessRuntimeBuilder rewooBuilder = newBuilder();
        rewooBuilder.executionParadigm(ParadigmSpec.ofType("rewoo"));
        assertThat(readInjectedLoop(rewooBuilder)).isInstanceOf(ReWooEngine.class);

        AgentHarnessRuntimeBuilder selfAskBuilder = newBuilder();
        selfAskBuilder.executionParadigm(ParadigmSpec.ofType("self-ask"));
        assertThat(readInjectedLoop(selfAskBuilder)).isInstanceOf(SelfAskEngine.class);

        AgentHarnessRuntimeBuilder autoBuilder = newBuilder();
        autoBuilder.executionParadigm(ParadigmSpec.ofType("auto"));
        assertThat(readInjectedLoop(autoBuilder)).isInstanceOf(RouterEngine.class);
    }

    @Test
    @DisplayName("react/null/未识别/空类型均不替换默认ReAct")
    void executionParadigm_invalidTypes_shouldFallbackToDefault() throws Exception {
        AgentHarnessRuntimeBuilder reactBuilder = newBuilder();
        reactBuilder.executionParadigm(ParadigmSpec.ofType("react"));
        assertThat(readInjectedLoop(reactBuilder)).isNull();

        AgentHarnessRuntimeBuilder nullBuilder = newBuilder();
        nullBuilder.executionParadigm(null);
        assertThat(readInjectedLoop(nullBuilder)).isNull();

        AgentHarnessRuntimeBuilder unknownBuilder = newBuilder();
        unknownBuilder.executionParadigm(ParadigmSpec.ofType("whatever"));
        assertThat(readInjectedLoop(unknownBuilder)).isNull();

        AgentHarnessRuntimeBuilder blankBuilder = newBuilder();
        blankBuilder.executionParadigm(ParadigmSpec.ofType("  "));
        assertThat(readInjectedLoop(blankBuilder)).isNull();
    }

    @Test
    @DisplayName("范式类型大小写不敏感")
    void executionParadigm_caseInsensitive_shouldMapEngine() throws Exception {
        AgentHarnessRuntimeBuilder builder = newBuilder();
        builder.executionParadigm(ParadigmSpec.ofType("Reflexion"));
        assertThat(readInjectedLoop(builder)).isInstanceOf(ReflexionEngine.class);
    }
}
