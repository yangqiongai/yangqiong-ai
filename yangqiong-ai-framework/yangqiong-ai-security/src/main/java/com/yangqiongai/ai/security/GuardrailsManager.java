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
package com.yangqiongai.ai.security;

import com.yangqiongai.ai.security.guardrails.GuardrailChain;
import com.yangqiongai.ai.security.guardrails.GuardrailContext;
import com.yangqiongai.ai.security.guardrails.GuardrailResult;
import com.yangqiongai.ai.security.guardrails.HookPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 护栏服务
 *
 * @author yangqiong
 */
@Service
public class GuardrailsManager {

    @Autowired
    private GuardrailChain guardrailChain;

    /**
     * 按挂载点执行护栏检查
     * @param hookPoint
     * @param content
     * @param context
     * @return
     */
    public GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context) {
        return guardrailChain.check(hookPoint, content, context);
    }

    /**
     * 检查输入内容
     * @param input
     * @return
     */
    public GuardrailResult checkInput(String input) {
        return guardrailChain.check(HookPoint.INPUT, input, GuardrailContext.empty());
    }

    /**
     * 检查输入内容（带上下文）
     * @param input
     * @param context
     * @return
     */
    public GuardrailResult checkInput(String input, GuardrailContext context) {
        return guardrailChain.check(HookPoint.INPUT, input, context);
    }

    /**
     * 检查输出内容
     * @param output
     * @return
     */
    public GuardrailResult checkOutput(String output) {
        return guardrailChain.check(HookPoint.OUTPUT, output, GuardrailContext.empty());
    }

    /**
     * 检查输出内容（带上下文）
     * @param output
     * @param context
     * @return
     */
    public GuardrailResult checkOutput(String output, GuardrailContext context) {
        return guardrailChain.check(HookPoint.OUTPUT, output, context);
    }

    /**
     * 检查工具调用参数
     * @param toolCallArgs
     * @param context
     * @return
     */
    public GuardrailResult checkToolCall(String toolCallArgs, GuardrailContext context) {
        return guardrailChain.check(HookPoint.TOOL_CALL, toolCallArgs, context);
    }

    /**
     * 检查System Prompt
     * @param systemPrompt
     * @param context
     * @return
     */
    public GuardrailResult checkSystemPrompt(String systemPrompt, GuardrailContext context) {
        return guardrailChain.check(HookPoint.SYSTEM_PROMPT, systemPrompt, context);
    }
}
