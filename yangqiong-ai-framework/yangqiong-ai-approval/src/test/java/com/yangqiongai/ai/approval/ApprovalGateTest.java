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
package com.yangqiongai.ai.approval;

import com.yangqiongai.ai.approval.model.PendingRequestInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ApprovalGate 单元测试")
class ApprovalGateTest {

    @Mock
    private PendingRequestStore pendingRequestStore;

    @Mock
    private ApprovalTokenGenerator tokenGenerator;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ApprovalGate approvalGate;

    @Nested
    @DisplayName("requestApproval 测试")
    class RequestApprovalTest {

        @Test
        @DisplayName("创建PENDING状态的请求并设置正确字段")
        void shouldCreatePendingRequestWithCorrectFields() {
            when(tokenGenerator.generateRequestId()).thenReturn("req_test123");
            when(tokenGenerator.generateToken()).thenReturn("valid_token_abc");

            PendingRequestInfo result = approvalGate.requestApproval(
                    "session-1", "user-1", "TOOL", "toolA", "测试原因", Map.of("key", "value"), null);

            assertThat(result).isNotNull();
            assertThat(result.getRequestId()).isEqualTo("req_test123");
            assertThat(result.getApprovalToken()).isEqualTo("valid_token_abc");
            assertThat(result.getSessionId()).isEqualTo("session-1");
            assertThat(result.getUserId()).isEqualTo("user-1");
            assertThat(result.getTargetName()).isEqualTo("toolA");
            assertThat(result.getStatus()).isEqualTo(ApprovalStatus.PENDING.name());
            assertThat(result.getExpireTime()).isNotNull();
        }

        @Test
        @DisplayName("持久化请求到store")
        void shouldPersistRequestToStore() {
            when(tokenGenerator.generateRequestId()).thenReturn("req_test123");
            when(tokenGenerator.generateToken()).thenReturn("valid_token_abc");

            approvalGate.requestApproval("session-1", "user-1", "TOOL", "toolA", "测试原因", Map.of(), null);

            verify(pendingRequestStore).save(any(PendingRequestInfo.class));
        }

        @Test
        @DisplayName("inputSchema正确设置到请求")
        void shouldSetInputSchemaToRequest() {
            when(tokenGenerator.generateRequestId()).thenReturn("req_test123");
            when(tokenGenerator.generateToken()).thenReturn("valid_token_abc");
            String schema = "{\"options\":[\"A\",\"B\"]}";

            PendingRequestInfo result = approvalGate.requestApproval(
                    "session-1", "user-1", "TOOL", "toolA", "测试原因", Map.of(), schema);

            assertThat(result.getInputSchema()).isEqualTo(schema);
        }
    }

    @Nested
    @DisplayName("checkApprovalStatus 测试")
    class CheckApprovalStatusTest {

        @Test
        @DisplayName("PENDING请求返回PENDING状态")
        void shouldReturnPendingForPendingRequest() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setStatus(ApprovalStatus.PENDING.name());
            request.setExpireTime(LocalDateTime.now().plusMinutes(30));
            when(pendingRequestStore.findByRequestId("req-1")).thenReturn(Optional.of(request));

            ApprovalStatus status = approvalGate.checkApprovalStatus("req-1");

            assertThat(status).isEqualTo(ApprovalStatus.PENDING);
        }

        @Test
        @DisplayName("过期请求返回TIMEOUT状态")
        void shouldReturnTimeoutForExpiredRequest() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setStatus(ApprovalStatus.PENDING.name());
            request.setExpireTime(LocalDateTime.now().minusMinutes(1));
            when(pendingRequestStore.findByRequestId("req-1")).thenReturn(Optional.of(request));

            ApprovalStatus status = approvalGate.checkApprovalStatus("req-1");

            assertThat(status).isEqualTo(ApprovalStatus.TIMEOUT);
        }

        @Test
        @DisplayName("不存在的请求返回TIMEOUT状态")
        void shouldReturnTimeoutForNonExistentRequest() {
            when(pendingRequestStore.findByRequestId("req-nonexistent")).thenReturn(Optional.empty());

            ApprovalStatus status = approvalGate.checkApprovalStatus("req-nonexistent");

            assertThat(status).isEqualTo(ApprovalStatus.TIMEOUT);
        }
    }

    @Nested
    @DisplayName("approve 测试")
    class ApproveTest {

        @Test
        @DisplayName("审批通过更新状态为APPROVED并释放latch，携带responsePayload")
        void shouldApproveAndUpdateStatusWithPayload() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setRequestId("req-1");
            request.setStatus(ApprovalStatus.PENDING.name());
            when(tokenGenerator.isTokenValid("valid-token")).thenReturn(true);
            when(pendingRequestStore.findByApprovalToken("valid-token")).thenReturn(Optional.of(request));

            String payload = "{\"selectedOption\":\"A\"}";
            approvalGate.approve("valid-token", "admin", payload);

            verify(pendingRequestStore).updateStatus("req-1", ApprovalStatus.APPROVED.name(), "admin", null, payload);
        }

        @Test
        @DisplayName("无responsePayload时通过审批")
        void shouldApproveWithoutPayload() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setRequestId("req-1");
            request.setStatus(ApprovalStatus.PENDING.name());
            when(tokenGenerator.isTokenValid("valid-token")).thenReturn(true);
            when(pendingRequestStore.findByApprovalToken("valid-token")).thenReturn(Optional.of(request));

            approvalGate.approve("valid-token", "admin", null);

            verify(pendingRequestStore).updateStatus("req-1", ApprovalStatus.APPROVED.name(), "admin", null, null);
        }

        @Test
        @DisplayName("无效令牌不做任何操作")
        void shouldDoNothingForInvalidToken() {
            when(tokenGenerator.isTokenValid("invalid")).thenReturn(false);

            approvalGate.approve("invalid", "admin", null);

            verify(pendingRequestStore, never()).updateStatus(anyString(), anyString(), anyString(), anyString(), anyString());
        }

        @Test
        @DisplayName("非PENDING状态请求不做任何操作")
        void shouldDoNothingForNonPendingRequest() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setRequestId("req-1");
            request.setStatus(ApprovalStatus.APPROVED.name());
            when(tokenGenerator.isTokenValid("valid-token")).thenReturn(true);
            when(pendingRequestStore.findByApprovalToken("valid-token")).thenReturn(Optional.of(request));

            approvalGate.approve("valid-token", "admin", null);

            verify(pendingRequestStore, never()).updateStatus(anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("reject 测试")
    class RejectTest {

        @Test
        @DisplayName("拒绝请求更新状态为REJECTED")
        void shouldRejectAndUpdateStatus() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setRequestId("req-1");
            request.setStatus(ApprovalStatus.PENDING.name());
            when(tokenGenerator.isTokenValid("valid-token")).thenReturn(true);
            when(pendingRequestStore.findByApprovalToken("valid-token")).thenReturn(Optional.of(request));

            approvalGate.reject("valid-token", "admin", "不合规");

            verify(pendingRequestStore).updateStatus("req-1", ApprovalStatus.REJECTED.name(), "admin", "不合规", null);
        }

        @Test
        @DisplayName("无效令牌不做任何操作")
        void shouldDoNothingForInvalidToken() {
            when(tokenGenerator.isTokenValid("invalid")).thenReturn(false);

            approvalGate.reject("invalid", "admin", "reason");

            verify(pendingRequestStore, never()).updateStatus(anyString(), anyString(), anyString(), anyString(), anyString());
        }
    }

    @Nested
    @DisplayName("waitForApproval 测试")
    class WaitForApprovalTest {

        @Test
        @DisplayName("审批通过后返回已审批请求")
        void shouldReturnApprovedRequest() throws InterruptedException {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setRequestId("req-1");
            request.setStatus(ApprovalStatus.APPROVED.name());

            Thread approverThread = new Thread(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(100);
                    java.lang.reflect.Field latchMapField = ApprovalGate.class.getDeclaredField("latchMap");
                    latchMapField.setAccessible(true);
                    @SuppressWarnings("unchecked")
                    Map<String, CountDownLatch> latchMap = (Map<String, CountDownLatch>) latchMapField.get(approvalGate);
                    CountDownLatch latch = latchMap.get("req-1");
                    if (latch != null) {
                        latch.countDown();
                    }
                } catch (Exception e) {
                    // ignore
                }
            });

            when(pendingRequestStore.findByRequestId("req-1")).thenReturn(Optional.of(request));
            approverThread.start();

            PendingRequestInfo result = approvalGate.waitForApproval("req-1", Duration.ofSeconds(5));
            approverThread.join(1000);

            assertThat(result).isNotNull();
            assertThat(result.getRequestId()).isEqualTo("req-1");
        }

        @Test
        @DisplayName("超时后返回TIMEOUT状态请求")
        void shouldReturnTimeoutRequestAfterTimeout() {
            PendingRequestInfo request = new PendingRequestInfo();
            request.setRequestId("req-timeout");
            request.setStatus(ApprovalStatus.PENDING.name());
            when(pendingRequestStore.findByRequestId("req-timeout")).thenReturn(Optional.of(request));

            PendingRequestInfo result = approvalGate.waitForApproval("req-timeout", Duration.ofMillis(50));

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(ApprovalStatus.TIMEOUT.name());
            verify(pendingRequestStore).updateStatus("req-timeout", ApprovalStatus.TIMEOUT.name(), null, null, null);
        }
    }
}
