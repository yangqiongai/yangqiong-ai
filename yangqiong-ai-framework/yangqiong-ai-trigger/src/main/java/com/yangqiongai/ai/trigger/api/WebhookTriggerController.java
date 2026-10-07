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
package com.yangqiongai.ai.trigger.api;

import com.yangqiongai.ai.trigger.model.AgentTriggerFireResult;
import com.yangqiongai.ai.trigger.service.AgentTriggerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * WEBHOOK回调触发入口
 * <p>
 * 外部系统按令牌回调唤起Agent（企业增强，ai.agent.trigger.webhook.enabled开启后生效）。
 * 令牌即凭证，配合去重窗口/每日配额治理风暴。
 * </p>
 * @author yangqiong
 */
@Tag(name = "WEBHOOK触发入口")
@RestController
@RequestMapping("/open/v1/triggers")
@ConditionalOnProperty(prefix = "ai.agent.trigger.webhook", name = "enabled", havingValue = "true")
public class WebhookTriggerController {

    private static final Logger log = LoggerFactory.getLogger(WebhookTriggerController.class);

    private static final String FIRE_SOURCE = "WEBHOOK";

    @Autowired
    private AgentTriggerService triggerService;

    /**
     * WEBHOOK回调触发
     * @param token 回调令牌
     * @param dedupKey 幂等键(可选,缺省按载荷摘要去重)
     * @param payload 回调载荷
     * @return
     */
    @Operation(summary = "WEBHOOK回调触发Agent")
    @PostMapping("/{token}")
    public ResponseEntity<Map<String, Object>> fire(@PathVariable("token") String token,
                                                    @RequestHeader(value = "X-Dedup-Key", required = false) String dedupKey,
                                                    @RequestBody(required = false) String payload) {
        String body = payload == null ? "" : payload;
        // 缺省幂等键取载荷摘要，防止外部重发风暴
        String key = dedupKey == null || dedupKey.isBlank()
                ? AgentTriggerService.sha256(body) : dedupKey;
        AgentTriggerFireResult result = triggerService.fireByWebhookToken(token, key, body, FIRE_SOURCE);
        Map<String, Object> response = new HashMap<>();
        response.put("status", result.getStatus());
        response.put("taskId", result.getTaskId());
        response.put("message", result.getMessage());
        if (result.isFired()) {
            return ResponseEntity.ok(response);
        }
        // 令牌不存在/已停用不暴露细节，统一404；治理拒绝返回409
        log.info("WEBHOOK触发未入队: token={}, status={}", token, result.getStatus());
        if (AgentTriggerFireResult.NOT_FOUND.equals(result.getStatus())) {
            return ResponseEntity.status(404).body(response);
        }
        return ResponseEntity.status(409).body(response);
    }
}
