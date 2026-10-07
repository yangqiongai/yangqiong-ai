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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A2A推送回调通知测试
 * @author yangqiong
 */
@ExtendWith(MockitoExtension.class)
class A2aPushNotifierTest {

    @Mock
    private A2aPushConfigService pushConfigService;

    @Mock
    private RestTemplate restTemplate;

    private A2aPushNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new A2aPushNotifier(pushConfigService, restTemplate);
    }

    private A2aPushConfig config() {
        A2aPushConfig config = new A2aPushConfig();
        config.setTaskId("t-1");
        config.setUrl("http://callback");
        config.setTokenHeader("Authorization");
        config.setToken("Bearer tk");
        config.setStatus(A2aPushConfig.STATUS_ENABLED);
        return config;
    }

    @Test
    void onTaskCompletedShouldPushWithToken() {
        when(pushConfigService.getByTaskId("t-1")).thenReturn(config());
        when(restTemplate.postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        notifier.onTaskCompleted(new TaskCompletedEvent("t-1", "u-1", "demo", "in", "out", 0, true));

        verify(restTemplate, times(1)).postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void onTaskCompletedShouldSkipWithoutConfig() {
        when(pushConfigService.getByTaskId("t-1")).thenReturn(null);

        notifier.onTaskCompleted(new TaskCompletedEvent("t-1", "u-1", "demo", "in", "out", 0, true));

        verify(restTemplate, times(0)).postForEntity(any(), any(), any());
    }

    @Test
    void onTaskCompletedShouldRetryThreeTimes() {
        when(pushConfigService.getByTaskId("t-1")).thenReturn(config());
        when(restTemplate.postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new IllegalStateException("网络不可达"))
                .thenThrow(new IllegalStateException("网络不可达"))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        notifier.onTaskCompleted(new TaskCompletedEvent("t-1", "u-1", "demo", "in", "out", 0, true));

        verify(restTemplate, times(3)).postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void onTaskCompletedShouldNotThrowWhenAllRetriesFail() {
        when(pushConfigService.getByTaskId("t-1")).thenReturn(config());
        when(restTemplate.postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new IllegalStateException("网络不可达"));

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                notifier.onTaskCompleted(new TaskCompletedEvent("t-1", "u-1", "demo", "in", "out", 0, true)));

        verify(restTemplate, times(3)).postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class));
    }

    @Test
    void payloadShouldCarryTerminalState() {
        when(pushConfigService.getByTaskId("t-1")).thenReturn(config());
        when(restTemplate.postForEntity(eq("http://callback"), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(HttpStatus.OK));

        notifier.onTaskCompleted(new TaskCompletedEvent("t-1", "u-1", "demo", "in", "输出内容\"引用\"", 0, true));

        org.mockito.ArgumentCaptor<HttpEntity<String>> captor =
                org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://callback"), captor.capture(), eq(String.class));
        String body = captor.getValue().getBody();
        assertThat(body).contains("\"state\":\"completed\"").contains("输出内容");
    }
}
