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
package com.yangqiongai.ai.agent.core.middleware;

import com.yangqiongai.ai.agent.runtime.guardrail.ContentModerationPolicy;
import com.yangqiongai.ai.agent.runtime.guardrail.ModerationVerdict;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.security.guardrails.GuardrailContext;
import com.yangqiongai.ai.security.guardrails.GuardrailResult;
import com.yangqiongai.ai.security.GuardrailsManager;
import com.yangqiongai.ai.security.guardrails.HookPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内容审查策略适配器
 * <p>
 * 将security模块护栏链适配为框架内容审查SPI，挂载MODERATION点，
 * 由引擎在每次模型调用前对进入LLM的消息执行审查。
 * </p>
 * @author yangqiong
 */
public class SecurityModerationPolicyAdapter implements ContentModerationPolicy {

    private static final Logger log = LoggerFactory.getLogger(SecurityModerationPolicyAdapter.class);

    /**
     * 护栏服务门面
     */
    private final GuardrailsManager guardrailsManager;

    /**
     * 构造内容审查策略适配器
     * @param guardrailsManager
     */
    public SecurityModerationPolicyAdapter(GuardrailsManager guardrailsManager) {
        this.guardrailsManager = guardrailsManager;
    }

    /**
     * 审查消息内容
     * @param message 待审查消息
     * @return 裁决结果
     */
    @Override
    public ModerationVerdict moderate(AgentMessage message) {
        String text = message == null ? null : message.getTextContent();
        if (text == null || text.isBlank()) {
            return ModerationVerdict.pass();
        }
        GuardrailResult result = guardrailsManager.check(HookPoint.MODERATION, text, GuardrailContext.empty());
        if (!result.isPassed()) {
            log.info("模型调用消息被审查策略拦截: reason={}", result.getReason());
            return ModerationVerdict.block(result.getReason());
        }
        // MASK动作用于文本脱敏，审查裁决无脱敏内容载体，文本脱敏已由输入侧中间件处理，此处统一放行
        return ModerationVerdict.pass();
    }
}
