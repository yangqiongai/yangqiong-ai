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
package com.yangqiongai.ai.platform.connector.service;

import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.platform.connector.config.ConnectorProperties;
import com.yangqiongai.ai.platform.connector.entity.ConnectorInstance;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCallbackReceipt;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCallbackRequest;
import com.yangqiongai.ai.platform.connector.spi.ConnectorCredentialView;
import com.yangqiongai.ai.platform.connector.spi.ConnectorInboundGateway;
import com.yangqiongai.ai.platform.connector.spi.ConnectorInboundMessage;
import com.yangqiongai.ai.platform.connector.spi.ConnectorProvider;
import com.yangqiongai.ai.platform.connector.entity.IntegrationRecord;
import com.yangqiongai.ai.platform.connector.mapper.IntegrationRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("连接器入站网关服务单元测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ConnectorGatewayServiceTest {

    @Mock
    private ConnectorInstanceService instanceService;

    @Mock
    private ConnectorCredentialService credentialService;

    @Mock
    private ConnectorRegistry registry;

    @Mock
    private IntegrationRecordMapper integrationRecordMapper;

    @Mock
    private ObjectProvider<AgentEngine> engineProvider;

    @Mock
    private ConnectorProvider provider;

    @Mock
    private ConnectorInboundGateway gateway;

    @Mock
    private AgentEngine engine;

    /**
     * 被测网关服务
     */
    private ConnectorGatewayService gatewayService;

    /**
     * 启用实例
     */
    private ConnectorInstance instance;

    @BeforeEach
    void setUp() {
        gatewayService = new ConnectorGatewayService(instanceService, credentialService, registry,
                integrationRecordMapper, new ConnectorProperties(), engineProvider);

        instance = new ConnectorInstance();
        instance.setInstanceCode("dd-main");
        instance.setProviderCode("dingtalk");
        instance.setAgentCode("agent-1");
        instance.setScopeId("default");

        lenient().when(instanceService.findEnabledByCode("dd-main")).thenReturn(instance);
        lenient().when(registry.getProvider("dingtalk")).thenReturn(provider);
        lenient().when(credentialService.loadCredentialView(any(Long.class)))
                .thenReturn(new ConnectorCredentialView(Map.of("appSecret", "s")));
        lenient().when(provider.createGateway(any(), any())).thenReturn(gateway);
        lenient().when(provider.providerCode()).thenReturn("dingtalk");
        lenient().when(engineProvider.getIfAvailable()).thenReturn(engine);
    }

    /**
     * 构建标准化入站消息
     * @param messageId
     * @return
     */
    private ConnectorInboundMessage message(String messageId) {
        return new ConnectorInboundMessage(messageId, "staff-1", "张三", "cid-1", "帮我查订单", "{}");
    }

    @Test
    @DisplayName("实例不存在或停用时返回安全空响应")
    void shouldAckSafeWhenInstanceMissing() {
        when(instanceService.findEnabledByCode("unknown")).thenReturn(null);

        String ack = gatewayService.handleCallback("unknown",
                new ConnectorCallbackRequest("POST", Map.of(), Map.of(), "{}"));

        assertThat(ack).isEqualTo("{}");
        verify(integrationRecordMapper, never()).insert(any(IntegrationRecord.class));
    }

    @Test
    @DisplayName("提供商不支持入站网关时返回安全空响应")
    void shouldAckSafeWhenGatewayNull() {
        when(provider.createGateway(any(), any())).thenReturn(null);

        String ack = gatewayService.handleCallback("dd-main",
                new ConnectorCallbackRequest("POST", Map.of(), Map.of(), "{}"));

        assertThat(ack).isEqualTo("{}");
        verify(integrationRecordMapper, never()).insert(any(IntegrationRecord.class));
    }

    @Test
    @DisplayName("验签失败等网关异常返回安全空响应且不留痕")
    void shouldAckSafeWhenGatewayReject() {
        when(gateway.handle(any())).thenThrow(new IllegalStateException("签名校验失败"));

        String ack = gatewayService.handleCallback("dd-main",
                new ConnectorCallbackRequest("POST", Map.of(), Map.of(), "{}"));

        assertThat(ack).isEqualTo("{}");
        verify(integrationRecordMapper, never()).insert(any(IntegrationRecord.class));
    }

    @Test
    @DisplayName("无需异步处理的消息（url验证）直接返回ack")
    void shouldReturnAckWhenNoAsyncMessage() {
        when(gateway.handle(any()))
                .thenReturn(ConnectorCallbackReceipt.ackOnly("echo-1"));

        String ack = gatewayService.handleCallback("dd-main",
                new ConnectorCallbackRequest("GET", Map.of(), Map.of("echostr", "echo-1"), null));

        assertThat(ack).isEqualTo("echo-1");
        verify(integrationRecordMapper, never()).insert(any(IntegrationRecord.class));
    }

    @Test
    @DisplayName("重复投递的消息幂等丢弃")
    void shouldDropDuplicatedMessage() {
        when(gateway.handle(any()))
                .thenReturn(ConnectorCallbackReceipt.async("{}", message("msg-1")));
        when(integrationRecordMapper.selectCount(any())).thenReturn(1L);

        String ack = gatewayService.handleCallback("dd-main",
                new ConnectorCallbackRequest("POST", Map.of(), Map.of(), "{}"));

        assertThat(ack).isEqualTo("{}");
        verify(integrationRecordMapper, never()).insert(any(IntegrationRecord.class));
    }

    @Test
    @DisplayName("并发重复投递经唯一键冲突兜底丢弃")
    void shouldDropOnDuplicateKeyRace() {
        when(gateway.handle(any()))
                .thenReturn(ConnectorCallbackReceipt.async("{}", message("msg-1")));
        when(integrationRecordMapper.selectCount(any())).thenReturn(0L);
        when(integrationRecordMapper.insert(any(IntegrationRecord.class)))
                .thenThrow(new DuplicateKeyException("uk conflict"));

        String ack = gatewayService.handleCallback("dd-main",
                new ConnectorCallbackRequest("POST", Map.of(), Map.of(), "{}"));

        assertThat(ack).isEqualTo("{}");
        verify(integrationRecordMapper, never()).updateById(any(IntegrationRecord.class));
    }

    @Test
    @DisplayName("有效消息幂等落库SENDING并返回渠道ack")
    void shouldRecordAndAckNewMessage() {
        when(gateway.handle(any()))
                .thenReturn(ConnectorCallbackReceipt.async("{}", message("msg-1")));
        when(integrationRecordMapper.selectCount(any())).thenReturn(0L);
        // 异步任务内engine缺失走FAILED留痕，不影响本断言
        when(engineProvider.getIfAvailable()).thenReturn(null);
        // 在insert时点捕获状态，避免异步更新与断言竞争同一对象
        AtomicReference<String> statusAtInsert = new AtomicReference<>();
        when(integrationRecordMapper.insert(any(IntegrationRecord.class))).thenAnswer(invocation -> {
            IntegrationRecord inserted = invocation.getArgument(0);
            statusAtInsert.set(inserted.getStatus());
            return 1;
        });

        String ack = gatewayService.handleCallback("dd-main",
                new ConnectorCallbackRequest("POST", Map.of(), Map.of(), "{}"));

        assertThat(ack).isEqualTo("{}");
        ArgumentCaptor<IntegrationRecord> captor = ArgumentCaptor.forClass(IntegrationRecord.class);
        verify(integrationRecordMapper, timeout(2000)).insert(captor.capture());
        IntegrationRecord record = captor.getValue();
        assertThat(record.getChannel()).isEqualTo("DINGTALK");
        assertThat(record.getMessageId()).isEqualTo("connector:dd-main:msg-1");
        assertThat(statusAtInsert.get()).isEqualTo("SENDING");
    }

    @Test
    @DisplayName("Agent执行成功经渠道回复并留痕SUCCESS")
    void shouldProcessAsyncSuccess() {
        IntegrationRecord record = new IntegrationRecord();
        record.setChannel("DINGTALK");
        record.setMessageId("connector:dd-main:msg-1");
        when(engine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("订单已完成"));
        when(provider.reply(any(), any(), any(), eq("订单已完成"))).thenReturn("receipt-1");

        gatewayService.processAsync(instance, provider,
                new ConnectorCredentialView(Map.of()), message("msg-1"), record);

        verify(provider).reply(any(), any(), any(), eq("订单已完成"));
        assertThat(record.getStatus()).isEqualTo("SUCCESS");
        assertThat(record.getResponse()).isEqualTo("receipt-1");
        verify(integrationRecordMapper).updateById(record);
    }

    @Test
    @DisplayName("Agent执行失败回复兜底话术并留痕FAILED")
    void shouldProcessAsyncFailureWithFallbackReply() {
        IntegrationRecord record = new IntegrationRecord();
        when(engine.run(any(AgentRequest.class))).thenThrow(new RuntimeException("模型超时"));

        gatewayService.processAsync(instance, provider,
                new ConnectorCredentialView(Map.of()), message("msg-1"), record);

        verify(provider).reply(any(), any(), any(),
                eq("抱歉，处理您的消息时出现问题，请稍后重试"));
        assertThat(record.getStatus()).isEqualTo("FAILED");
        assertThat(record.getResponse()).contains("模型超时");
    }

    @Test
    @DisplayName("实例未绑定Agent或引擎缺失时按失败兜底")
    void shouldFailWhenAgentUnavailable() {
        IntegrationRecord record = new IntegrationRecord();
        when(engineProvider.getIfAvailable()).thenReturn(null);

        gatewayService.processAsync(instance, provider,
                new ConnectorCredentialView(Map.of()), message("msg-1"), record);

        assertThat(record.getStatus()).isEqualTo("FAILED");
        assertThat(record.getResponse()).contains("Agent引擎未启用");

        IntegrationRecord recordWithoutAgent = new IntegrationRecord();
        when(engineProvider.getIfAvailable()).thenReturn(engine);
        instance.setAgentCode(null);
        gatewayService.processAsync(instance, provider,
                new ConnectorCredentialView(Map.of()), message("msg-1"), recordWithoutAgent);
        assertThat(recordWithoutAgent.getStatus()).isEqualTo("FAILED");
        assertThat(recordWithoutAgent.getResponse()).contains("未绑定目标Agent");
    }

    @Test
    @DisplayName("渠道回复发送失败时留痕FAILED")
    void shouldRecordFailureWhenReplyThrows() {
        IntegrationRecord record = new IntegrationRecord();
        when(engine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("ok"));
        when(provider.reply(any(), any(), any(), eq("ok")))
                .thenThrow(new IllegalStateException("发送失败"));

        gatewayService.processAsync(instance, provider,
                new ConnectorCredentialView(Map.of()), message("msg-1"), record);

        assertThat(record.getStatus()).isEqualTo("FAILED");
        assertThat(record.getResponse()).contains("回复发送失败");
    }

    @Test
    @DisplayName("Agent请求按实例与会话维度构造")
    void shouldBuildAgentRequest() {
        ConnectorInboundMessage groupMessage = message("msg-1");
        AgentRequest request = gatewayService.buildAgentRequest(instance, groupMessage);

        assertThat(request.getAgentCode()).isEqualTo("agent-1");
        assertThat(request.getUserId()).isEqualTo("staff-1");
        assertThat(request.getScopeId()).isEqualTo("default");
        assertThat(request.getSessionId()).isEqualTo("connector:dd-main:cid-1");
        assertThat(request.getInputAsText()).isEqualTo("帮我查订单");
        assertThat(request.getBody().get("source")).isEqualTo("connector");
        assertThat(request.getBody().get("instanceCode")).isEqualTo("dd-main");

        ConnectorInboundMessage singleMessage = new ConnectorInboundMessage(
                "msg-2", "staff-1", "张三", null, "你好", "{}");
        AgentRequest singleRequest = gatewayService.buildAgentRequest(instance, singleMessage);
        assertThat(singleRequest.getSessionId()).isEqualTo("connector:dd-main:staff-1");
    }

    @Test
    @DisplayName("Agent返回空内容时按失败兜底")
    void shouldFailWhenAgentOutputEmpty() {
        IntegrationRecord record = new IntegrationRecord();
        when(engine.run(any(AgentRequest.class))).thenReturn(AgentResult.success("  "));

        gatewayService.processAsync(instance, provider,
                new ConnectorCredentialView(Map.of()), message("msg-1"), record);

        assertThat(record.getStatus()).isEqualTo("FAILED");
        assertThat(record.getResponse()).contains("Agent执行未产出回复");
        verify(provider).reply(any(), any(), any(), anyString());
    }
}
