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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Prompt注入检测护栏
 *
 * @author yangqiong
 */
@Component
public class PromptInjectionGuardrail implements Guardrail {

    private static final Logger log = LoggerFactory.getLogger(PromptInjectionGuardrail.class);

    /**
     * Prompt注入检测模式列表
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("(?i)忽略.{0,4}(之前|以上|上面|前面|所有).{0,4}(指令|提示|规则|约束)"),
            Pattern.compile("(?i)ignore.{0,4}(previous|above|prior|all).{0,4}(instructions|prompts|rules|constraints)"),
            Pattern.compile("(?i)扮演.{0,4}(一个|一名|角色)"),
            Pattern.compile("(?i)pretend.{0,4}(to be|you are|you're)"),
            Pattern.compile("(?i)输出.{0,4}(系统|初始|原始).{0,4}(提示|指令|prompt)"),
            Pattern.compile("(?i)reveal.{0,4}(your|the|system).{0,4}(prompt|instructions|initial)"),
            Pattern.compile("(?i)你(现在|已经)?(不再|不用).{0,6}(遵守|遵循|受限于)"),
            Pattern.compile("(?i)you.{0,4}are.{0,4}no.{0,4}longer.{0,4}(bound|constrained|limited)"),
            Pattern.compile("(?i)(jailbreak|越狱|突破限制|解除限制)"),
            Pattern.compile("(?i)DAN\\s+mode|developer\\s+mode|god\\s+mode")
    );

    @Override
    public Set<HookPoint> hookPoints() {
        return Set.of(HookPoint.INPUT);
    }

    @Override
    public String name() {
        return "builtin-prompt-injection";
    }

    @Override
    public GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context) {
        if (content == null || content.isBlank()) {
            return GuardrailResult.passed();
        }

        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(content).find()) {
                log.warn("Prompt注入护栏拦截: 检测到注入攻击模式, pattern={}", pattern.pattern());
                return GuardrailResult.blocked("输入包含疑似Prompt注入内容", name(), hookPoint);
            }
        }

        return GuardrailResult.passed();
    }

    @Override
    public int order() {
        return 10;
    }
}
