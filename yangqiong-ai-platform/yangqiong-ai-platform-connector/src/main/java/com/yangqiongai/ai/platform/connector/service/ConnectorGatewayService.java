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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yangqiongai.ai.agent.core.AgentEngine;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
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
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * 连接器入站网关服务
 * <p>
 * 渠道回调统一入口：按实例编码路由启用实例，验签解析后立即ack，
 * 消息以渠道messageId幂等落库（重复投递直接丢弃），异步执行实例绑定Agent
 * 并经渠道回复，全程落IntegrationRecord留痕。
 * </p>
 * @author yangqiong
 */
@Service
@ConditionalOnProperty(name = "ai.connector.enabled", havingValue = "true")
public class ConnectorGatewayService {

    private static final Logger log = LoggerFactory.getLogger(ConnectorGatewayService.class);

    /**
     * 渠道安全空应答（不暴露实例存在性）
     */
    public static final String SAFE_EMPTY_ACK = "{}";

    /**
     * 发送记录渠道前缀（连接器入站）
     */
    private static final String CHANNEL_PREFIX = "connector:";

    /**
     * 异步处理队列容量（有界队列，拒绝即ack丢弃+留痕）
     */
    private static final int QUEUE_CAPACITY = 200;

    /**
     * Agent执行失败兜底回复话术
     */
    private static final String FALLBACK_REPLY = "抱歉，处理您的消息时出现问题，请稍后重试";

    /**
     * 实例管理（路由键查询）
     */
    private final ConnectorInstanceService instanceService;

    /**
     * 凭证托管（解密凭证视图）
     */
    private final ConnectorCredentialService credentialService;

    /**
     * 提供商注册中心
     */
    private final ConnectorRegistry registry;

    /**
     * 集成发送记录（幂等与留痕）
     */
    private final IntegrationRecordMapper integrationRecordMapper;

    /**
     * 连接器配置
     */
    private final ConnectorProperties properties;

    /**
     * Agent引擎（运行时未启用时无bean）
     */
    private final ObjectProvider<AgentEngine> engineProvider;

    /**
     * 异步处理线程池（懒创建）
     */
    private volatile ThreadPoolExecutor workerExecutor;

    public ConnectorGatewayService(ConnectorInstanceService instanceService,
                                   ConnectorCredentialService credentialService,
                                   ConnectorRegistry registry,
                                   IntegrationRecordMapper integrationRecordMapper,
                                   ConnectorProperties properties,
                                   ObjectProvider<AgentEngine> engineProvider) {
        this.instanceService = instanceService;
        this.credentialService = credentialService;
        this.registry = registry;
        this.integrationRecordMapper = integrationRecordMapper;
        this.properties = properties;
        this.engineProvider = engineProvider;
    }

    /**
     * 处理渠道回调，返回即时应答报文
     * <p>
     * 实例不存在/停用/验签失败均返回渠道安全空响应，不暴露存在性；
     * 有效消息先幂等落库再转异步处理。
     * </p>
     * @param instanceCode 实例编码（路由键）
     * @param request 渠道回调原始请求
     * @return 即时应答报文（JSON）
     */
    public String handleCallback(String instanceCode, ConnectorCallbackRequest request) {
        ConnectorInstance instance = instanceService.findEnabledByCode(instanceCode);
        if (instance == null) {
            return SAFE_EMPTY_ACK;
        }
        ConnectorProvider provider = registry.getProvider(instance.getProviderCode());
        if (provider == null) {
            return SAFE_EMPTY_ACK;
        }
        ConnectorCredentialView credentialView;
        ConnectorInboundGateway gateway;
        try {
            credentialView = credentialService.loadCredentialView(instance.getCredentialId());
            gateway = provider.createGateway(instance, credentialView);
        } catch (Exception e) {
            log.warn("连接器网关构建失败: instance={}", instanceCode, e);
            return SAFE_EMPTY_ACK;
        }
        if (gateway == null) {
            return SAFE_EMPTY_ACK;
        }
        ConnectorCallbackReceipt receipt;
        try {
            receipt = gateway.handle(request);
        } catch (Exception e) {
            // 验签失败等：静默拒绝，不留处理痕迹也不回显错误细节
            log.warn("连接器回调验签失败: instance={}, uri={}", instanceCode, request.getQuery(), e);
            return SAFE_EMPTY_ACK;
        }
        String ack = StringUtils.hasText(receipt.getAckPayload())
                ? receipt.getAckPayload() : SAFE_EMPTY_ACK;
        ConnectorInboundMessage message = receipt.getMessage();
        if (message == null) {
            return ack;
        }
        submitAsync(instance, provider, credentialView, message);
        return ack;
    }

    /**
     * 幂等校验后提交异步处理任务，队列满时留痕丢弃
     * @param instance 实例
     * @param provider 提供商
     * @param credentialView 解密凭证
     * @param message 标准化入站消息
     */
    private void submitAsync(ConnectorInstance instance, ConnectorProvider provider,
                             ConnectorCredentialView credentialView, ConnectorInboundMessage message) {
        String channel = instance.getProviderCode().toUpperCase();
        String messageId = CHANNEL_PREFIX + instance.getInstanceCode() + ":" + message.getMessageId();
        Long duplicated = integrationRecordMapper.selectCount(new LambdaQueryWrapper<IntegrationRecord>()
                .eq(IntegrationRecord::getChannel, channel)
                .eq(IntegrationRecord::getMessageId, messageId));
        if (duplicated != null && duplicated > 0) {
            // 重复投递直接ack丢弃
            log.info("连接器消息重复投递，丢弃: instance={}, messageId={}",
                    instance.getInstanceCode(), message.getMessageId());
            return;
        }
        IntegrationRecord record = new IntegrationRecord();
        record.setChannel(channel);
        record.setMessageId(messageId);
        record.setStatus(IntegrationRecord.STATUS_SENDING);
        record.setScopeId(instance.getScopeId());
        record.setSendTime(java.time.LocalDateTime.now());
        try {
            integrationRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发重复投递兜底
            log.info("连接器消息并发重复投递，丢弃: instance={}, messageId={}",
                    instance.getInstanceCode(), message.getMessageId());
            return;
        }
        try {
            executor().execute(() -> processAsync(instance, provider, credentialView, message, record));
        } catch (RejectedExecutionException e) {
            log.warn("连接器处理队列已满，丢弃消息: instance={}, messageId={}",
                    instance.getInstanceCode(), message.getMessageId());
            record.setStatus(IntegrationRecord.STATUS_SKIPPED);
            record.setResponse("网关处理队列已满，消息被丢弃");
            integrationRecordMapper.updateById(record);
        }
    }

    /**
     * 异步执行Agent并经渠道回复，全程留痕
     * @param instance 实例
     * @param provider 提供商
     * @param credentialView 解密凭证
     * @param message 标准化入站消息
     * @param record 幂等留痕记录
     */
    void processAsync(ConnectorInstance instance, ConnectorProvider provider,
                      ConnectorCredentialView credentialView, ConnectorInboundMessage message,
                      IntegrationRecord record) {
        String replyText = null;
        try {
            AgentEngine engine = engineProvider.getIfAvailable();
            if (engine == null) {
                throw new IllegalStateException("Agent引擎未启用");
            }
            if (!StringUtils.hasText(instance.getAgentCode())) {
                throw new IllegalStateException("实例未绑定目标Agent");
            }
            AgentRequest request = buildAgentRequest(instance, message);
            AgentResult result = engine.run(request);
            replyText = result != null && result.isSuccess()
                    ? ContentBlockConverter.toOutputText(result.getOutput()) : null;
            if (!StringUtils.hasText(replyText)) {
                throw new IllegalStateException("Agent执行未产出回复"
                        + (result != null && !result.isSuccess() ? ": " + result.getErrorMessage() : ""));
            }
        } catch (Exception e) {
            log.error("连接器消息处理失败: instance={}, messageId={}",
                    instance.getInstanceCode(), message.getMessageId(), e);
            record.setStatus(IntegrationRecord.STATUS_FAILED);
            record.setResponse(e.getMessage());
            integrationRecordMapper.updateById(record);
            replyWithFallback(instance, provider, credentialView, message);
            return;
        }
        try {
            String receipt = provider.reply(instance, credentialView, message, replyText);
            record.setStatus(IntegrationRecord.STATUS_SUCCESS);
            record.setResponse(receipt);
        } catch (Exception e) {
            log.error("连接器回复发送失败: instance={}, messageId={}",
                    instance.getInstanceCode(), message.getMessageId(), e);
            record.setStatus(IntegrationRecord.STATUS_FAILED);
            record.setResponse("回复发送失败: " + e.getMessage());
        }
        integrationRecordMapper.updateById(record);
    }

    /**
     * 构造Agent执行请求（会话按会话标识/发送者维度延续）
     * @param instance 实例
     * @param message 标准化入站消息
     * @return
     */
    AgentRequest buildAgentRequest(ConnectorInstance instance, ConnectorInboundMessage message) {
        String sessionId = "connector:" + instance.getInstanceCode() + ":"
                + (StringUtils.hasText(message.getConversationId())
                ? message.getConversationId() : message.getSenderId());
        return new AgentRequest()
                .agentCode(instance.getAgentCode())
                .sessionId(sessionId)
                .input(message.getText())
                .userId(message.getSenderId())
                .scopeId(instance.getScopeId())
                .addBody("source", "connector")
                .addBody("instanceCode", instance.getInstanceCode());
    }

    /**
     * 失败兜底回复（渠道回复再失败仅记录日志，不再重试）
     * @param instance 实例
     * @param provider 提供商
     * @param credentialView 解密凭证
     * @param message 标准化入站消息
     */
    private void replyWithFallback(ConnectorInstance instance, ConnectorProvider provider,
                                   ConnectorCredentialView credentialView, ConnectorInboundMessage message) {
        try {
            provider.reply(instance, credentialView, message, FALLBACK_REPLY);
        } catch (Exception e) {
            log.warn("连接器兜底回复发送失败: instance={}, messageId={}",
                    instance.getInstanceCode(), message.getMessageId(), e);
        }
    }

    /**
     * 获取或创建异步处理线程池（有界队列）
     * @return
     */
    private ThreadPoolExecutor executor() {
        ThreadPoolExecutor executor = workerExecutor;
        if (executor == null) {
            synchronized (this) {
                executor = workerExecutor;
                if (executor == null) {
                    executor = new ThreadPoolExecutor(
                            Math.max(properties.getGatewayAsyncWorkers(), 1),
                            Math.max(properties.getGatewayAsyncWorkers(), 1),
                            60L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(QUEUE_CAPACITY),
                            r -> {
                                Thread t = new Thread(r, "connector-gateway-worker");
                                t.setDaemon(true);
                                return t;
                            });
                    workerExecutor = executor;
                }
            }
        }
        return executor;
    }

    /**
     * 优雅关闭处理线程池
     */
    @PreDestroy
    public void shutdown() {
        ThreadPoolExecutor executor = workerExecutor;
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
