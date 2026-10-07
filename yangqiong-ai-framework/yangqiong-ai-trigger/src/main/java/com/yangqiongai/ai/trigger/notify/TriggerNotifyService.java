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
package com.yangqiongai.ai.trigger.notify;

import com.yangqiongai.ai.trigger.entity.AgentTriggerEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 触发结果回投
 * <p>
 * 触发成功后异步POST JSON到触发器配置的notify_webhook地址，
 * 失败仅告警不重试（不影响触发主流程）。
 * </p>
 * @author yangqiong
 */
@Service
public class TriggerNotifyService {

    private static final Logger log = LoggerFactory.getLogger(TriggerNotifyService.class);

    @Value("${ai.agent.trigger.notify.timeout-seconds:5}")
    private int timeoutSeconds;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    /**
     * 异步回投触发结果（webhook未配置时跳过）
     * @param trigger
     * @param taskId
     * @param fireSource
     * @param payload
     */
    public void notifyFire(AgentTriggerEntity trigger, String taskId, String fireSource, String payload) {
        String webhook = trigger.getNotifyWebhook();
        if (webhook == null || webhook.isBlank()) {
            return;
        }
        String body = "{\"triggerCode\":\"" + escape(trigger.getTriggerCode())
                + "\",\"agentCode\":\"" + escape(trigger.getAgentCode())
                + "\",\"fireSource\":\"" + escape(fireSource)
                + "\",\"taskId\":\"" + escape(taskId)
                + "\",\"payload\":\"" + escape(payload == null ? "" : payload)
                + "\",\"status\":\"QUEUED\"}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 300) {
                    log.warn("触发回投响应异常: trigger={}, status={}", trigger.getTriggerCode(), response.statusCode());
                }
            } catch (Exception e) {
                log.warn("触发回投失败: trigger={}", trigger.getTriggerCode(), e);
            }
        });
    }

    private String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
