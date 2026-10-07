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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.common.bean.ApiResult;
import com.yangqiongai.ai.common.exception.AiException;
import com.yangqiongai.ai.trust.credential.entity.OpenCredential;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 开放接口凭证鉴权过滤器
 * <p>
 * 默认关闭，ai.trust.credential.enabled=true 后对纳管路径生效；
 * 校验通过后限流、记录最近使用时间，并将凭证写入请求属性供下游读取。
 * </p>
 * @author yangqiong
 */
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    /**
     * 凭证请求头名称
     */
    private static final String HEADER_API_KEY = "X-Api-Key";

    /**
     * Authorization 头部名称(Bearer 凭证兼容)
     */
    private static final String HEADER_AUTHORIZATION = "Authorization";

    /**
     * Bearer 前缀
     */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 凭证请求属性名
     */
    public static final String ATTR_CREDENTIAL = "trust.credential";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final OpenCredentialService credentialService;

    private final List<String> includePaths;

    public ApiKeyAuthenticationFilter(OpenCredentialService credentialService, List<String> includePaths) {
        this.credentialService = credentialService;
        this.includePaths = includePaths;
    }

    /**
     * 纳管路径外直接放行，命中路径校验凭证、限流后放行
     * @param request
     * @param response
     * @param filterChain
     * @throws ServletException
     * @throws IOException
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!match(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        String secret = extractSecret(request);
        if (secret == null) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, 10003, "缺少凭证");
            return;
        }
        OpenCredential credential;
        try {
            credential = credentialService.verify(secret);
            if (!credentialService.tryAcquire(credential)) {
                writeError(response, HttpStatus.TOO_MANY_REQUESTS.value(), 10001, "请求频率超限");
                return;
            }
        } catch (AiException e) {
            writeError(response, HttpServletResponse.SC_UNAUTHORIZED, e.getCode(), e.getMessage());
            return;
        }
        credentialService.touchLastUsed(credential);
        request.setAttribute(ATTR_CREDENTIAL, credential);
        filterChain.doFilter(request, response);
    }

    /**
     * 判断请求是否命中纳管路径(OPTIONS预检放行)
     * @param request
     * @return
     */
    private boolean match(HttpServletRequest request) {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        for (String pattern : includePaths) {
            if (PATH_MATCHER.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提取凭证密钥(优先X-Api-Key头,兼容Bearer格式)
     * @param request
     * @return
     */
    private String extractSecret(HttpServletRequest request) {
        String secret = request.getHeader(HEADER_API_KEY);
        if (secret == null || secret.isBlank()) {
            String authHeader = request.getHeader(HEADER_AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                secret = authHeader.substring(BEARER_PREFIX.length()).trim();
            }
        }
        if (secret != null && secret.isBlank()) {
            return null;
        }
        return secret;
    }

    /**
     * 写出统一响应体错误
     * @param response
     * @param status
     * @param code
     * @param message
     * @throws IOException
     */
    private void writeError(HttpServletResponse response, int status, int code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ApiResult.fail(message, code, status)));
    }
}
