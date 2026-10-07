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
package com.yangqiongai.ai.agent.runtime;

import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.event.ClarificationAnswer;
import com.yangqiongai.ai.agent.runtime.event.ConfirmResult;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent运行时默认方法边界单元测试
 * @author yangqiong
 */
class AgentRuntimeDefaultMethodTest {

    /**
     * 未实现resume系的测试桩运行时
     */
    private final AgentRuntime stubRuntime = new AgentRuntime() {

        @Override
        public Mono<AgentMessage> call(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Mono.empty();
        }

        @Override
        public Flux<AgentEvent> streamEvents(List<AgentMessage> inputs, AgentRuntimeContext context) {
            return Flux.empty();
        }

        @Override
        public String getName() {
            return "stub";
        }
    };

    @Test
    @DisplayName("未覆写resume时默认抛出UnsupportedOperationException")
    void resume_default_shouldThrowUnsupported() {
        assertThatThrownBy(() -> stubRuntime.resume(
                List.of(ConfirmResult.approve("search")), null).collectList().block())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("未覆写resumeWithClarification时默认抛出UnsupportedOperationException")
    void resumeWithClarification_default_shouldThrowUnsupported() {
        assertThatThrownBy(() -> stubRuntime.resumeWithClarification(
                List.of(new ClarificationAnswer("tc-1", "答案")), null).collectList().block())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("默认resume与resumeWithClarification传入空入参同样抛出UnsupportedOperationException")
    void resume_emptyInputs_shouldStillThrowUnsupported() {
        assertThatThrownBy(() -> stubRuntime.resume(null, null).collectList().block())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> stubRuntime.resumeWithClarification(null, null).collectList().block())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("测试桩运行时基础方法可用")
    void stubRuntime_basicMethods_shouldWork() {
        assertThat(stubRuntime.getName()).isEqualTo("stub");
        assertThat(stubRuntime.call(List.of(), null).block()).isNull();
        assertThat(stubRuntime.streamEvents(List.of(), null).collectList().block()).isEmpty();
    }
}
