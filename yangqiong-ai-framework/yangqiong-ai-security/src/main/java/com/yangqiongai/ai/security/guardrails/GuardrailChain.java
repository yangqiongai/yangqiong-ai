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
package com.yangqiongai.ai.security.guardrails;

import com.yangqiongai.ai.security.spi.GuardrailTriggerLog;
import com.yangqiongai.ai.security.spi.TriggerAuditStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 护栏调度链
 * <p>
 * 按挂载点获取有效护栏，按order升序逐一执行，
 * 遇到首个block即短路返回。
 * </p>
 *
 * @author yangqiong
 */
@Component
public class GuardrailChain {

    private static final Logger log = LoggerFactory.getLogger(GuardrailChain.class);

    @Autowired
    private GuardrailManager guardrailManager;

    @Autowired(required = false)
    private ApplicationEventPublisher eventPublisher;

    /**
     * 触发审计存储SPI（社区默认内存实现，商业触发审计JDBC覆盖；单元测试时不注入）
     */
    @Autowired(required = false)
    private TriggerAuditStore triggerAuditStore;

    /**
     * 对content在给定挂载点执行所有护栏
     * <p>
     * 任意一个返回block时立即返回该结果；全部通过则返回passed。
     * MASK动作时返回masked结果但继续执行后续护栏。
     * </p>
     * @param hookPoint
     * @param content
     * @param context
     * @return
     */
    public GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context) {
        List<Guardrail> guardrails = guardrailManager.getEffectiveGuardrails(hookPoint);
        if (guardrails.isEmpty()) {
            return GuardrailResult.passed();
        }

        // 累积脱敏内容（MASK动作会替换content供后续护栏检查）
        String currentContent = content;

        for (Guardrail guardrail : guardrails) {
            try {
                GuardrailResult result = guardrail.check(hookPoint, currentContent, context);
                if (result != null) {
                    if (result.getAction() == GuardrailAction.BLOCK && !result.isPassed()) {
                        log.warn("护栏阻断: hookPoint={}, guardrail={}, reason={}",
                                hookPoint, guardrail.name(), result.getReason());
                        publishTriggerEvent(guardrail.name(), hookPoint, GuardrailAction.BLOCK,
                                currentContent, result.getReason(), context);
                        return result;
                    }
                    if (result.getAction() == GuardrailAction.MASK && result.getMaskedContent() != null) {
                        // MASK：更新content供后续护栏检查脱敏后的内容
                        currentContent = result.getMaskedContent();
                        log.debug("护栏脱敏: hookPoint={}, guardrail={}", hookPoint, guardrail.name());
                        publishTriggerEvent(guardrail.name(), hookPoint, GuardrailAction.MASK,
                                currentContent, result.getReason(), context);
                    }
                }
            } catch (Exception e) {
                log.error("护栏执行异常，跳过: hookPoint={}, guardrail={}", hookPoint, guardrail.name(), e);
            }
        }

        // 如果经过MASK后内容有变化，返回最终脱敏结果
        if (currentContent != null && !currentContent.equals(content)) {
            return GuardrailResult.masked(currentContent, "guardrail-chain", hookPoint);
        }

        return GuardrailResult.passed();
    }

    /**
     * 检查指定挂载点是否有护栏注册
     * @param hookPoint
     * @return
     */
    public boolean hasGuardrails(HookPoint hookPoint) {
        return !guardrailManager.getEffectiveGuardrails(hookPoint).isEmpty();
    }

    /**
     * 发布护栏触发事件
     * @param guardrailName
     * @param hookPoint
     * @param action
     * @param content
     * @param reason
     * @param context
     */
    private void publishTriggerEvent(String guardrailName, HookPoint hookPoint, GuardrailAction action,
                                     String content, String reason, GuardrailContext context) {
        saveTriggerLog(guardrailName, hookPoint, action, content, context);
        if (eventPublisher == null) {
            return;
        }
        String agentCode = context != null ? context.agentCode() : null;
        eventPublisher.publishEvent(new GuardrailTriggeredEvent(
                this, guardrailName, hookPoint, action, content, reason, agentCode));
    }

    /**
     * 记录护栏触发审计
     * @param guardrailName
     * @param hookPoint
     * @param action
     * @param content
     * @param context
     */
    private void saveTriggerLog(String guardrailName, HookPoint hookPoint, GuardrailAction action,
                                String content, GuardrailContext context) {
        if (triggerAuditStore == null) {
            return;
        }
        try {
            GuardrailTriggerLog triggerLog = new GuardrailTriggerLog();
            triggerLog.setRuleName(guardrailName);
            triggerLog.setHookPoint(hookPoint.name());
            triggerLog.setInputSummary(truncate(content, 500));
            triggerLog.setAction(action.name());
            triggerLog.setScopeId(context != null ? context.scopeId() : null);
            triggerLog.setAgentCode(context != null ? context.agentCode() : null);
            triggerLog.setUserId(context != null ? context.userId() : null);
            triggerLog.setCreateTime(LocalDateTime.now());
            triggerAuditStore.save(triggerLog);
        } catch (Exception e) {
            log.error("护栏触发审计记录失败: guardrail={}, hookPoint={}", guardrailName, hookPoint, e);
        }
    }

    /**
     * 截断文本到指定长度
     * @param text
     * @param max
     * @return
     */
    private String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}
