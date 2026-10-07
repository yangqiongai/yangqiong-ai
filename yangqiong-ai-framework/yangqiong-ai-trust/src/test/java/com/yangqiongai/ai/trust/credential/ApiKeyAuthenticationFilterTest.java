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
package com.yangqiongai.ai.trust.credential;

import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.trust.credential.entity.OpenCredential;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 开放接口凭证鉴权过滤器测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ApiKeyAuthenticationFilter 单元测试")
class ApiKeyAuthenticationFilterTest {

    @Mock
    private OpenCredentialService credentialService;

    private ApiKeyAuthenticationFilter filter;

    private OpenCredential credential;

    @BeforeEach
    void setUp() {
        filter = new ApiKeyAuthenticationFilter(credentialService, List.of("/open/v1/**", "/mcp/**"));
        credential = new OpenCredential();
        credential.setId(1L);
        credential.setCredentialCode("okc0001");
        when(credentialService.verify(anyString())).thenReturn(credential);
        when(credentialService.tryAcquire(any(OpenCredential.class))).thenReturn(true);
    }

    @Test
    @DisplayName("未命中纳管路径直接放行且不校验凭证")
    void shouldPassThroughForUnmatchedPath() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/other");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(credentialService, never()).verify(anyString());
    }

    @Test
    @DisplayName("命中纳管路径但缺少凭证时返回401")
    void shouldReturn401WhenSecretMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open/v1/capabilities");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("缺少凭证");
    }

    @Test
    @DisplayName("凭证校验失败时返回401与业务错误码")
    void shouldReturn401WhenVerifyFailed() throws ServletException, IOException {
        when(credentialService.verify(anyString()))
                .thenThrow(new AiException(10003, "凭证校验失败"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open/v1/capabilities");
        request.addHeader("X-Api-Key", "ok-okc0001-wrong");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("凭证校验失败").contains("10003");
    }

    @Test
    @DisplayName("限流超额时返回429")
    void shouldReturn429WhenRateLimited() throws ServletException, IOException {
        when(credentialService.tryAcquire(any(OpenCredential.class))).thenReturn(false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open/v1/capabilities");
        request.addHeader("X-Api-Key", "ok-okc0001-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getContentAsString()).contains("请求频率超限");
    }

    @Test
    @DisplayName("X-Api-Key校验通过后放行并写入凭证属性")
    void shouldPassWithApiKeyHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/open/v1/capabilities");
        request.addHeader("X-Api-Key", "ok-okc0001-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(chain.getRequest().getAttribute(ApiKeyAuthenticationFilter.ATTR_CREDENTIAL))
                .isSameAs(credential);
        verify(credentialService).touchLastUsed(credential);
    }

    @Test
    @DisplayName("Bearer格式凭证兼容校验")
    void shouldPassWithBearerAuthorization() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/mcp/stream");
        request.addHeader("Authorization", "Bearer ok-okc0001-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(credentialService).verify("ok-okc0001-secret");
    }

    @Test
    @DisplayName("OPTIONS预检请求放行")
    void shouldPassOptionsRequest() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/open/v1/capabilities");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        verify(credentialService, never()).verify(anyString());
    }
}
