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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * WEBHOOK触发入口测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class WebhookTriggerControllerTest {

    @Mock
    private AgentTriggerService triggerService;

    private WebhookTriggerController controller;

    @BeforeEach
    void setUp() {
        controller = new WebhookTriggerController();
        ReflectionTestUtils.setField(controller, "triggerService", triggerService);
    }

    @Test
    void fireReturnsOkWhenFired() {
        when(triggerService.fireByWebhookToken(eq("whk-ok"), anyString(), eq("载荷"), eq("WEBHOOK")))
                .thenReturn(AgentTriggerFireResult.fired("task-1"));

        ResponseEntity<?> response = controller.fire("whk-ok", null, "载荷");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void fireUsesProvidedDedupKeyHeader() {
        when(triggerService.fireByWebhookToken(eq("whk-ok"), eq("my-key"), anyString(), eq("WEBHOOK")))
                .thenReturn(AgentTriggerFireResult.fired("task-1"));

        controller.fire("whk-ok", "my-key", "载荷");

        org.mockito.Mockito.verify(triggerService).fireByWebhookToken(eq("whk-ok"), eq("my-key"),
                eq("载荷"), eq("WEBHOOK"));
    }

    @Test
    void fireReturns404ForUnknownToken() {
        when(triggerService.fireByWebhookToken(eq("whk-none"), anyString(), anyString(), eq("WEBHOOK")))
                .thenReturn(AgentTriggerFireResult.rejected(AgentTriggerFireResult.NOT_FOUND, "触发器不存在"));

        ResponseEntity<?> response = controller.fire("whk-none", null, "载荷");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void fireReturns409ForGovernanceRejection() {
        when(triggerService.fireByWebhookToken(eq("whk-ok"), anyString(), anyString(), eq("WEBHOOK")))
                .thenReturn(AgentTriggerFireResult.rejected(AgentTriggerFireResult.QUOTA_EXCEEDED, "配额耗尽"));

        ResponseEntity<?> response = controller.fire("whk-ok", null, "载荷");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }
}
