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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 沙箱HTTP客户端
 * @author yangqiong
 */
@Service
public class SandboxHttpClient {

    private static final Logger log = LoggerFactory.getLogger(SandboxHttpClient.class);

    @Value("${ai.sandbox.url:}")
    private String sandboxUrl;

    @Value("${ai.sandbox.timeout:30}")
    private int timeoutSeconds;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 调用沙箱服务执行代码
     * @param language
     * @param code
     * @return
     */
    public String submitCode(String language, String code) {
        log.debug("提交代码到沙箱服务: language={}", language);

        if (sandboxUrl == null || sandboxUrl.isBlank()) {
            log.warn("沙箱服务未配置(ai.sandbox.url)，返回降级响应");
            return "{\"status\":\"unavailable\",\"output\":\"沙箱服务未配置\"}";
        }

        try {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("language", language);
            requestBody.put("code", code);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    sandboxUrl, entity, String.class);
            return response.getBody();
        } catch (Exception e) {
            log.error("沙箱服务调用失败: url={}, error={}", sandboxUrl, e.getMessage(), e);
            return "{\"status\":\"error\",\"output\":\"沙箱服务调用失败: " + e.getMessage() + "\"}";
        }
    }
}
