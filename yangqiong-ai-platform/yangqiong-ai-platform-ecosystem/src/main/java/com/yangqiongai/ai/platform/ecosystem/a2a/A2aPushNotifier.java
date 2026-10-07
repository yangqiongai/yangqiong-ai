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
package com.yangqiongai.ai.platform.ecosystem.a2a;

import com.yangqiongai.ai.agent.core.event.TaskCompletedEvent;
import com.yangqiongai.ai.platform.ecosystem.a2a.entity.A2aPushConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * A2A推送回调通知
 * <p>
 * 任务完成事件联动推送配置回调，失败重试3次，异常不阻断主流程。
 * </p>
 * @author yangqiong
 */
public class A2aPushNotifier {

    private static final Logger log = LoggerFactory.getLogger(A2aPushNotifier.class);

    private static final com.fasterxml.jackson.databind.ObjectMapper OBJECT_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper();

    /**
     * 回调失败重试次数
     */
    private static final int MAX_RETRIES = 3;

    /**
     * 默认鉴权头
     */
    private static final String DEFAULT_TOKEN_HEADER = "Authorization";

    private final A2aPushConfigService pushConfigService;

    private final RestTemplate restTemplate;

    public A2aPushNotifier(A2aPushConfigService pushConfigService) {
        this(pushConfigService, new RestTemplate());
    }

    A2aPushNotifier(A2aPushConfigService pushConfigService, RestTemplate restTemplate) {
        this.pushConfigService = pushConfigService;
        this.restTemplate = restTemplate;
    }

    /**
     * 任务完成后回调推送(仅成功完成的任务回调终态)
     * @param event
     */
    @EventListener
    public void onTaskCompleted(TaskCompletedEvent event) {
        A2aPushConfig config = pushConfigService.getByTaskId(event.getTaskId());
        if (config == null) {
            return;
        }
        String payload = buildPayload(event);
        boolean delivered = postWithRetry(config, payload);
        if (!delivered) {
            log.error("A2A推送回调失败(已重试{}次): taskId={}, url={}", MAX_RETRIES, event.getTaskId(), config.getUrl());
        }
    }

    /**
     * 构建回调载荷
     * @param event
     * @return
     */
    private String buildPayload(TaskCompletedEvent event) {
        try {
            Map<String, Object> payload = new java.util.LinkedHashMap<>();
            payload.put("taskId", event.getTaskId());
            payload.put("state", event.isSuccess() ? "completed" : "failed");
            payload.put("artifacts", event.isSuccess() ? new Object[]{
                    Map.of("kind", "text", "text", event.getOutputText() != null ? event.getOutputText() : "")
            } : null);
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("A2A回调载荷序列化失败", e);
        }
    }

    /**
     * 带重试的推送
     * @param config
     * @param payload
     * @return 是否投递成功
     */
    private boolean postWithRetry(A2aPushConfig config, String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (config.getToken() != null && !config.getToken().isBlank()) {
            headers.set(config.getTokenHeader() != null && !config.getTokenHeader().isBlank()
                    ? config.getTokenHeader() : DEFAULT_TOKEN_HEADER, config.getToken());
        }
        HttpEntity<String> entity = new HttpEntity<>(payload, headers);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                restTemplate.postForEntity(config.getUrl(), entity, String.class);
                return true;
            } catch (Exception e) {
                log.warn("A2A推送回调第{}次失败: taskId={}, url={}, error={}",
                        attempt, config.getTaskId(), config.getUrl(), e.getMessage());
            }
        }
        return false;
    }
}
