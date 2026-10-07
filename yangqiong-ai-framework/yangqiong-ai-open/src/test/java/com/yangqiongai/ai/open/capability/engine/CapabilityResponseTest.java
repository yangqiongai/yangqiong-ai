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
package com.yangqiongai.ai.open.capability.engine;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 能力调用响应
 * @author yangqiong
 */
class CapabilityResponseTest {

    @Test
    void testSuccessCreatesResponseWithSuccessTrue() {
        CapabilityResponse response = CapabilityResponse.success("输出文本", Map.of("key", "value"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutput()).isEqualTo("输出文本");
        assertThat(response.getStructuredOutput()).isEqualTo(Map.of("key", "value"));
        assertThat(response.getErrorMessage()).isNull();
    }

    @Test
    void testFailureCreatesResponseWithSuccessFalse() {
        CapabilityResponse response = CapabilityResponse.failure("发生错误");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEqualTo("发生错误");
        assertThat(response.getOutput()).isNull();
        assertThat(response.getStructuredOutput()).isNull();
    }

    @Test
    void testGettersAndSetters() {
        CapabilityResponse response = new CapabilityResponse();

        response.setSuccess(true);
        response.setOutput("输出内容");
        response.setStructuredOutput("结构化输出");
        response.setErrorMessage("错误信息");
        response.setCallId("call-001");
        response.setTokenMetrics(Map.of("promptTokens", 100, "completionTokens", 50));
        response.setDurationMillis(1500L);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutput()).isEqualTo("输出内容");
        assertThat(response.getStructuredOutput()).isEqualTo("结构化输出");
        assertThat(response.getErrorMessage()).isEqualTo("错误信息");
        assertThat(response.getCallId()).isEqualTo("call-001");
        assertThat(response.getTokenMetrics()).hasSize(2)
                .containsEntry("promptTokens", 100)
                .containsEntry("completionTokens", 50);
        assertThat(response.getDurationMillis()).isEqualTo(1500L);
    }

    @Test
    void testDefaultValues() {
        CapabilityResponse response = new CapabilityResponse();

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getOutput()).isNull();
        assertThat(response.getStructuredOutput()).isNull();
        assertThat(response.getErrorMessage()).isNull();
        assertThat(response.getCallId()).isNull();
        assertThat(response.getTokenMetrics()).isNull();
        assertThat(response.getDurationMillis()).isZero();
    }

    @Test
    void testSuccessWithNullStructuredOutput() {
        CapabilityResponse response = CapabilityResponse.success("仅文本输出", null);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getOutput()).isEqualTo("仅文本输出");
        assertThat(response.getStructuredOutput()).isNull();
    }

    @Test
    void testFailureWithEmptyErrorMessage() {
        CapabilityResponse response = CapabilityResponse.failure("");

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getErrorMessage()).isEmpty();
    }
}