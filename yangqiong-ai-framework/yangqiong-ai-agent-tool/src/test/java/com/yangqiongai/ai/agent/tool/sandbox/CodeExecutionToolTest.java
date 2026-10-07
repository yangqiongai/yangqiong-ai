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
package com.yangqiongai.ai.agent.tool.sandbox;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CodeExecutionTool 单元测试")
class CodeExecutionToolTest {

    @Mock
    private SandboxHttpClient sandboxHttpClient;

    @InjectMocks
    private CodeExecutionTool codeExecutionTool;

    @Nested
    @DisplayName("runCode 测试")
    class RunCodeTest {

        @Test
        @DisplayName("委托给sandboxHttpClient")
        void shouldDelegateToSandboxHttpClient() {
            when(sandboxHttpClient.submitCode("python", "print('hello')")).thenReturn("{\"status\":\"ok\"}");

            String result = codeExecutionTool.runCode("python", "print('hello')");

            assertThat(result).isEqualTo("{\"status\":\"ok\"}");
            verify(sandboxHttpClient).submitCode("python", "print('hello')");
        }
    }

    @Nested
    @DisplayName("SandboxHttpClient 测试")
    class SandboxHttpClientTest {

        @Test
        @DisplayName("空白URL返回不可用JSON")
        void shouldReturnUnavailableJsonForBlankUrl() {
            SandboxHttpClient client = new SandboxHttpClient();
            ReflectionTestUtils.setField(client, "sandboxUrl", "");

            String result = client.submitCode("python", "print('hello')");

            assertThat(result).contains("unavailable").contains("沙箱服务未配置");
        }

        @Test
        @DisplayName("异常时返回错误JSON")
        void shouldReturnErrorJsonForException() {
            SandboxHttpClient client = new SandboxHttpClient();
            ReflectionTestUtils.setField(client, "sandboxUrl", "http://invalid-sandbox-url");

            String result = client.submitCode("python", "print('hello')");

            assertThat(result).contains("error").contains("沙箱服务调用失败");
        }
    }
}
