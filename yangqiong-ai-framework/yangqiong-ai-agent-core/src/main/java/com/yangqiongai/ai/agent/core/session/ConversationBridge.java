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
package com.yangqiongai.ai.agent.core.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.core.model.content.OutputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextInputBlock;
import com.yangqiongai.ai.agent.core.model.content.TextOutputBlock;
import com.yangqiongai.ai.agent.runtime.AgentRuntime;
import com.yangqiongai.ai.agent.runtime.AgentRuntimeBuilder;
import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.core.model.Agent;
import com.yangqiongai.ai.agent.core.agent.AgentManager;
import com.yangqiongai.ai.common.util.TokenEstimator;
import com.yangqiongai.ai.common.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话桥接器，管理Agent会话的持久化和恢复
 * @author yangqiong
 */
@Service
public class ConversationBridge {

    private static final Logger log = LoggerFactory.getLogger(ConversationBridge.class);

    /**
     * 会话启用属性键
     */
    public static final String ATTR_SESSION_ENABLED = "sessionEnabled";

    /**
     * 会话命名空间属性键
     */
    public static final String ATTR_SESSION_NAMESPACE = "sessionNamespace";

    /**
     * 是否恢复历史消息（默认true），parallel子代理设为false避免污染
     */
    public static final String ATTR_SESSION_RESTORE_ENABLED = "sessionRestoreEnabled";

    /**
     * 是否持久化用户输入（默认true），parallel子代理设为false避免重复
     */
    public static final String ATTR_SESSION_PERSIST_USER_INPUT = "sessionPersistUserInput";

    /**
     * 是否执行ensureOrUpdateSession（默认true），子代理设为false避免并发创建/覆盖标题
     */
    public static final String ATTR_SESSION_ENSURE_ENABLED = "sessionEnsureEnabled";

    private static final String STATE_NAMESPACE = "conversation";

    private final ObjectMapper objectMapper;
    private final ObjectProvider<AgentSessionStore> sessionStoreProvider;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private AgentManager agentService;

    @Autowired
    public ConversationBridge(ObjectMapper objectMapper,
                              ObjectProvider<AgentSessionStore> sessionStoreProvider) {
        this.objectMapper = objectMapper;
        this.sessionStoreProvider = sessionStoreProvider;
    }

    /**
     * 准备会话，在Agent构建前配置Builder
     * @param context
     * @param builder
     */
    public void prepareSession(AgentContext context, AgentRuntimeBuilder builder) {
        if (builder == null || !isEnabled(context)) {
            return;
        }
        log.debug("会话准备完成: sessionId={}", resolveSessionId(context));
    }

    /**
     * 恢复会话状态，从数据库加载历史消息和摘要返回给调用方
     * <p>
     * 由于 agentscope SDK 的 {@code agent.call(inputs, runtimeContext)} 会根据 RuntimeContext 中的 sessionId
     * 解析出独立的 AgentState 实例，与无参 {@code agent.getAgentState()} 返回的不是同一个实例，
     * 因此历史消息不能仅写入 AgentState，还必须由调用方显式拼接到 inputs 中，确保 LLM 能看到上下文。
     * </p>
     * <p>
     * 同时加载会话摘要（summary_text），在长对话场景中摘要替代被裁剪的旧消息，
     * 以一条 USER 角色的上下文消息注入到历史消息最前面，确保 LLM 能感知之前对话的关键信息。
     * </p>
     * @param context
     * @return 历史消息列表（含摘要上下文），供调用方拼接到 inputs 中；无历史时返回空列表
     */
    public List<AgentMessage> restoreSession(AgentContext context) {
        if (!isEnabled(context)) {
            return List.of();
        }
        Object restoreFlag = context.getAttributes().get(ATTR_SESSION_RESTORE_ENABLED);
        if (restoreFlag instanceof Boolean bool && !bool) {
            log.debug("子代理跳过历史恢复: sessionId={}", resolveSessionId(context));
            return List.of();
        }
        AgentSessionStore store = getSessionStore();
        if (store == null) {
            return List.of();
        }
        String sessionId = resolveSessionId(context);
        String namespace = resolveNamespace(context);
        // 异步/流式线程无HTTP上下文，绑定请求scope保证会话与消息查询落在正确租户分区
        return bindRequestScope(context, () -> {
            try {
                List<AgentMessageRecord> historyRecords = store.findMessagesBySessionId(sessionId);
                if (historyRecords == null || historyRecords.isEmpty()) {
                    log.info("无历史消息可恢复: sessionId={}, namespace={}", sessionId, namespace);
                    return List.of();
                }
                List<AgentMessage> restoredMsgs = convertToMessages(historyRecords);
                // 加载会话摘要，作为上下文消息注入到历史最前面
                String summaryText = loadSummaryText(store, sessionId);
                if (summaryText != null && !summaryText.isBlank()) {
                    AgentMessage summaryMsg = buildSummaryContextMsg(summaryText);
                    restoredMsgs.add(0, summaryMsg);
                    log.info("会话摘要已注入上下文: sessionId={}, summaryLength={}", sessionId, summaryText.length());
                }
                log.info("会话历史恢复完成: sessionId={}, namespace={}, 消息数={}, 含摘要={}",
                        sessionId, namespace, restoredMsgs.size(), summaryText != null && !summaryText.isBlank());
                return restoredMsgs;
            } catch (Exception e) {
                log.warn("会话历史恢复失败，降级为空历史: sessionId={}, namespace={}", sessionId, namespace, e);
                return List.of();
            }
        });
    }

    /**
     * 从会话存储加载摘要文本
     * @param store
     * @param sessionId
     * @return 摘要文本，无摘要时返回null
     */
    private String loadSummaryText(AgentSessionStore store, String sessionId) {
        try {
            return store.findSession(sessionId)
                    .map(AgentSessionRecord::getSummaryText)
                    .filter(text -> !text.isBlank())
                    .orElse(null);
        } catch (Exception e) {
            log.warn("加载会话摘要失败: sessionId={}", sessionId, e);
            return null;
        }
    }

    /**
     * 构建摘要上下文消息，以USER角色注入，确保LLM能识别
     * @param summaryText
     * @return
     */
    private AgentMessage buildSummaryContextMsg(String summaryText) {
        String normalizedText = "Summary from previous conversation:\n" + summaryText;
        return AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(List.of(AgentTextBlock.builder().text(normalizedText).build()))
                .build();
    }

    /**
     * 恢复会话状态，从数据库加载历史消息注入Agent记忆
     * @param context
     * @param agent
     * @deprecated 使用 {@link #restoreSession(AgentContext)} 替代，由调用方将历史消息拼接到 inputs
     */
    @Deprecated
    public void restoreSession(AgentContext context, AgentRuntime agent) {
        if (agent == null || !isEnabled(context)) {
            return;
        }
        Object restoreFlag = context.getAttributes().get(ATTR_SESSION_RESTORE_ENABLED);
        if (restoreFlag instanceof Boolean bool && !bool) {
            log.debug("子代理跳过历史恢复: sessionId={}", resolveSessionId(context));
            return;
        }
        AgentSessionStore store = getSessionStore();
        if (store == null) {
            return;
        }
        String sessionId = resolveSessionId(context);
        String namespace = resolveNamespace(context);
        try {
            List<AgentMessageRecord> historyRecords = store.findMessagesBySessionId(sessionId);
            if (historyRecords == null || historyRecords.isEmpty()) {
                log.info("无历史消息可恢复: sessionId={}, namespace={}", sessionId, namespace);
                return;
            }
            // AgentRuntime不再支持直接操作AgentState，历史消息需由调用方拼接到inputs
            log.info("会话历史恢复完成: sessionId={}, namespace={}, 消息数={}", sessionId, namespace, historyRecords.size());
        } catch (Exception e) {
            log.warn("会话历史恢复失败，降级为空历史: sessionId={}, namespace={}", sessionId, namespace, e);
        }
    }

    /**
     * 持久化会话状态，将Agent执行结果和对话历史写入数据库
     * @param context
     * @param result
     */
    public void persistSession(AgentContext context, AgentMessage result) {
        if (!isEnabled(context)) {
            return;
        }
        AgentSessionStore store = getSessionStore();
        if (store == null) {
            return;
        }
        String sessionId = resolveSessionId(context);
        String namespace = resolveNamespace(context);
        // 异步/流式线程无HTTP上下文，绑定请求scope保证会话与消息写入正确租户分区
        bindRequestScope(context, () -> {
            try {
                Object ensureFlag = context.getAttributes().get(ATTR_SESSION_ENSURE_ENABLED);
                if (ensureFlag instanceof Boolean bool && !bool) {
                    log.debug("子代理跳过ensureOrUpdateSession: sessionId={}", sessionId);
                } else {
                    ensureOrUpdateSession(context, result, store);
                }
                Object persistUserFlag = context.getAttributes().get(ATTR_SESSION_PERSIST_USER_INPUT);
                if (persistUserFlag instanceof Boolean bool && !bool) {
                    log.debug("子代理跳过用户输入持久化: sessionId={}", sessionId);
                } else {
                    persistUserInput(context, store);
                }
                persistAssistantOutput(sessionId, context.getRequest().getUserId(),
                        context.getRequest().getScopeId(), result, store);
                store.triggerSummary(sessionId);
                log.info("会话状态持久化完成: sessionId={}, namespace={}", sessionId, namespace);
            } catch (Exception e) {
                log.warn("会话状态持久化失败: sessionId={}, namespace={}", sessionId, namespace, e);
            }
            return null;
        });
    }

    /**
     * 在请求scope上下文中执行数据库操作，执行前绑定ScopeContext、执行后清除
     * @param context
     * @param supplier
     * @return
     */
    private <T> T bindRequestScope(AgentContext context, java.util.function.Supplier<T> supplier) {
        String scopeId = context.getRequest().getScopeId();
        boolean bound = scopeId != null && !scopeId.isBlank();
        if (bound) {
            com.yangqiongai.ai.common.scope.ScopeContext.setScopeId(scopeId);
        }
        try {
            return supplier.get();
        } finally {
            if (bound) {
                com.yangqiongai.ai.common.scope.ScopeContext.clear();
            }
        }
    }

    /**
     * 判断会话是否启用
     * @param context
     * @return
     */
    public boolean isEnabled(AgentContext context) {
        if (context == null || context.getRequest() == null) {
            return false;
        }
        String sessionId = StringUtils.getOrDefault(context.getRequest().getSessionId()).trim();
        if (sessionId.isEmpty()) {
            return false;
        }
        Object flag = context.getAttributes().get(ATTR_SESSION_ENABLED);
        return flag instanceof Boolean bool && bool;
    }

    /**
     * 获取会话存储，不可用时返回null
     * @return
     */
    private AgentSessionStore getSessionStore() {
        AgentSessionStore store = sessionStoreProvider.getIfAvailable();
        if (store == null) {
            log.debug("AgentSessionStore不可用, 跳过会话操作");
        }
        return store;
    }

    /**
     * 将消息记录列表转换为AgentScope Msg列表
     * @param records
     * @return
     */
    private List<AgentMessage> convertToMessages(List<AgentMessageRecord> records) {
        List<AgentMessage> msgs = new ArrayList<>();
        for (AgentMessageRecord record : records) {
            try {
                AgentMessage msg = buildMsgFromRecord(record);
                if (msg != null) {
                    msgs.add(msg);
                }
            } catch (Exception e) {
                log.warn("历史消息反序列化异常，跳过该条: messageId={}", record.getMessageId(), e);
            }
        }
        return msgs;
    }

    /**
     * 根据消息记录构建Msg对象，支持多模态内容恢复
     * @param record
     * @return
     */
    private AgentMessage buildMsgFromRecord(AgentMessageRecord record) {
        String role = record.getMessageRole();
        String content = record.getMessageContent();
        if (role == null || role.isBlank()) {
            return null;
        }
        AgentMessageRole msgRole = mapRole(role);
        if (msgRole == null) {
            return null;
        }

        List<AgentContentBlock> contentBlocks;
        if (isMultimodalContent(content)) {
            contentBlocks = deserializeToContentBlocks(content);
        } else {
            String textContent = content != null ? content : "";
            contentBlocks = List.of(AgentTextBlock.builder().text(textContent).build());
        }

        return AgentMessage.builder()
                .name(role)
                .role(msgRole)
                .content(contentBlocks)
                .build();
    }

    /**
     * 映射消息角色字符串到MsgRole枚举
     * @param role
     * @return
     */
    private AgentMessageRole mapRole(String role) {
        if (role == null || role.isBlank()) {
            return null;
        }
        return switch (role.toLowerCase()) {
            case "system" -> AgentMessageRole.SYSTEM;
            case "assistant" -> AgentMessageRole.ASSISTANT;
            case "user" -> AgentMessageRole.USER;
            default -> AgentMessageRole.USER;
        };
    }

    /**
     * 持久化用户输入消息，支持多模态内容
     * @param context
     * @param store
     */
    private void persistUserInput(AgentContext context, AgentSessionStore store) {
        String sessionId = resolveSessionId(context);
        String userId = context.getRequest().getUserId();
        List<InputBlock> inputBlocks = context.getRequest().getInput();

        boolean isTextOnly = inputBlocks == null || inputBlocks.isEmpty()
                || (inputBlocks.size() == 1 && inputBlocks.get(0) instanceof TextInputBlock);

        String contentToSave;
        if (isTextOnly) {
            contentToSave = context.getRequest().getInputAsText();
        } else {
            List<OutputBlock> outputBlocks = convertInputBlocksToOutputBlocks(inputBlocks);
            contentToSave = serializeContentBlocks(outputBlocks);
        }

        if (contentToSave == null || contentToSave.isBlank()) {
            return;
        }
        AgentMessageRecord userMemory = new AgentMessageRecord();
        userMemory.setMessageId(generateMessageId());
        userMemory.setSessionId(sessionId);
        userMemory.setUserId(userId);
        userMemory.setScopeId(StringUtils.getOrDefault(context.getRequest().getScopeId()));
        userMemory.setMessageRole("user");
        userMemory.setMessageContent(contentToSave);
        userMemory.setTokenCount(TokenEstimator.estimateTokens(contentToSave));
        userMemory.setCreateUser(userId);
        userMemory.setCreateTime(LocalDateTime.now());
        store.saveMessage(userMemory);
    }

    /**
     * 持久化Agent响应消息，支持多模态内容
     * @param sessionId
     * @param userId
     * @param scopeId
     * @param result
     * @param store
     */
    private void persistAssistantOutput(String sessionId, String userId, String scopeId, AgentMessage result, AgentSessionStore store) {
        if (result == null) {
            return;
        }
        List<OutputBlock> outputBlocks = ContentBlockConverter.toOutputBlocks(result.getContent());

        boolean isTextOnly = outputBlocks == null || outputBlocks.isEmpty()
                || (outputBlocks.size() == 1 && outputBlocks.get(0) instanceof TextOutputBlock);

        String contentToSave;
        if (isTextOnly) {
            contentToSave = result.getTextContent();
        } else {
            contentToSave = serializeContentBlocks(outputBlocks);
        }

        if (contentToSave == null || contentToSave.isBlank()) {
            return;
        }
        AgentMessageRecord assistantMemory = new AgentMessageRecord();
        assistantMemory.setMessageId(generateMessageId());
        assistantMemory.setSessionId(sessionId);
        assistantMemory.setUserId(userId);
        assistantMemory.setScopeId(StringUtils.getOrDefault(scopeId));
        assistantMemory.setMessageRole("assistant");
        assistantMemory.setMessageContent(contentToSave);
        assistantMemory.setTokenCount(TokenEstimator.estimateTokens(contentToSave));
        // 从模型返回的ChatUsage中提取真实Token数据
        AgentChatUsage chatUsage = result.getChatUsage();
        if (chatUsage != null) {
            assistantMemory.setInputTokens(chatUsage.getPromptTokens());
            assistantMemory.setOutputTokens(chatUsage.getCompletionTokens());
            assistantMemory.setTotalTokens(chatUsage.getTotalTokens());
        }
        assistantMemory.setCreateTime(LocalDateTime.now());
        store.saveMessage(assistantMemory);
    }

    /**
     * 确保会话记录存在，不存在则创建，已存在则更新
     * @param context
     * @param result
     * @param store
     */
    private void ensureOrUpdateSession(AgentContext context, AgentMessage result, AgentSessionStore store) {
        String sessionId = resolveSessionId(context);
        String userId = context.getRequest().getUserId();
        String agentCode = context.getRequest().getAgentCode();

        AgentSessionRecord session = store.findSession(sessionId).orElse(null);

        if (session == null) {
            session = new AgentSessionRecord();
            session.setSessionId(sessionId);
            session.setUserId(StringUtils.getOrDefault(userId));
            session.setScopeId(StringUtils.getOrDefault(context.getRequest().getScopeId()));
            session.setAgentCode(StringUtils.getOrDefault(agentCode));
            session.setSessionType(resolveSessionType(agentCode));
            session.setSessionTitle(buildSessionTitle(context, result));
            session.setSessionStatus(1);
            session.setSummaryRound(0);
            session.setCreateUser(StringUtils.getOrDefault(userId));
            session.setCreateTime(LocalDateTime.now());
            session.setUpdateUser(StringUtils.getOrDefault(userId));
            session.setUpdateTime(LocalDateTime.now());
            store.createSession(session);
            log.debug("创建会话记录: sessionId={}, agentCode={}", sessionId, agentCode);
        } else {
            session.setUpdateUser(StringUtils.getOrDefault(userId));
            session.setUpdateTime(LocalDateTime.now());
            // 会话中途切换Agent时同步刷新，保证话题恢复的是最后使用的Agent
            if (agentCode != null && !agentCode.isBlank()) {
                session.setAgentCode(agentCode);
            }
            if (session.getSessionTitle() == null || session.getSessionTitle().isBlank()) {
                session.setSessionTitle(buildSessionTitle(context, result));
            }
            store.updateSession(session);
            log.debug("更新会话记录: sessionId={}", sessionId);
        }
    }

    /**
     * 根据agentCode从数据库查询会话类型
     * @param agentCode
     * @return
     */
    private String resolveSessionType(String agentCode) {
        try {
            Agent agent = agentService.getByCode(agentCode);
            if (agent != null && agent.getSessionType() != null) {
                return agent.getSessionType();
            }
        } catch (Exception e) {
            log.warn("无法查询Agent会话类型，使用默认值: agentCode={}", agentCode);
        }
        return "CHAT";
    }

    /**
     * 构建会话标题，优先取助手回复的前20个字符，其次取用户输入
     * @param context
     * @param result
     * @return
     */
    private String buildSessionTitle(AgentContext context, AgentMessage result) {
        // 优先使用助手回复内容作为标题，更直观反映对话内容
        if (result != null) {
            String response = result.getTextContent();
            if (response != null && !response.isBlank()) {
                return response.length() > 20 ? response.substring(0, 20) + "..." : response;
            }
        }
        // 降级使用用户输入
        String input = context.getRequest().getInputAsText();
        if (input != null && !input.isBlank()) {
            return input.length() > 20 ? input.substring(0, 20) + "..." : input;
        }
        return "新对话";
    }

    private String resolveSessionId(AgentContext context) {
        if (context == null || context.getRequest() == null) {
            return "";
        }
        return StringUtils.getOrDefault(context.getRequest().getSessionId()).trim();
    }

    private String resolveNamespace(AgentContext context) {
        if (context == null || context.getAttributes() == null) {
            return STATE_NAMESPACE;
        }
        Object value = context.getAttributes().get(ATTR_SESSION_NAMESPACE);
        String namespace = StringUtils.getOrDefault(value == null ? null : String.valueOf(value)).trim();
        if (!namespace.isEmpty()) {
            return namespace;
        }
        return StringUtils.getOrDefault(context.getRequest() == null ? null : context.getRequest().getAgentCode()).trim();
    }

    private String generateMessageId() {
        return StringUtils.generateCompactId();
    }

    /**
     * 判断消息内容是否为多模态JSON格式
     * @param content
     * @return
     */
    private boolean isMultimodalContent(String content) {
        if (content == null || !content.startsWith("[")) {
            return false;
        }
        try {
            objectMapper.readTree(content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 序列化OutputBlock列表为JSON字符串
     * @param blocks
     * @return
     */
    private String serializeContentBlocks(List<OutputBlock> blocks) {
        try {
            return objectMapper.writeValueAsString(blocks);
        } catch (Exception e) {
            log.warn("多模态内容序列化失败，降级为文本", e);
            return ContentBlockConverter.toOutputText(blocks);
        }
    }

    /**
     * 反序列化JSON字符串为ContentBlock列表
     * @param json
     * @return
     */
    private List<AgentContentBlock> deserializeToContentBlocks(String json) {
        try {
            List<OutputBlock> blocks = objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, OutputBlock.class));
            return ContentBlockConverter.fromOutputBlocks(blocks);
        } catch (Exception e) {
            log.warn("多模态内容反序列化失败，降级为文本", e);
            return List.of(AgentTextBlock.builder().text(json).build());
        }
    }

    /**
     * 将InputBlock列表转为OutputBlock列表（用于用户输入持久化）
     * @param inputBlocks
     * @return
     */
    private List<OutputBlock> convertInputBlocksToOutputBlocks(List<InputBlock> inputBlocks) {
        List<AgentContentBlock> contentBlocks = ContentBlockConverter.fromInputBlocks(inputBlocks);
        return ContentBlockConverter.toOutputBlocks(contentBlocks);
    }
}
