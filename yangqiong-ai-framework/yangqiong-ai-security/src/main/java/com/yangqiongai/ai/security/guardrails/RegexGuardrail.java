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

import java.util.Set;
import java.util.regex.Pattern;

/**
 * 正则护栏（由DB规则动态构建）
 *
 * @author yangqiong
 */
public class RegexGuardrail implements Guardrail {

    private static final Logger log = LoggerFactory.getLogger(RegexGuardrail.class);

    /**
     * 规则名称
     */
    private final String guardrailName;

    /**
     * 挂载点集合
     */
    private final Set<HookPoint> guardrailHookPoints;

    /**
     * 正则模式
     */
    private final Pattern pattern;

    /**
     * 排序优先级
     */
    private final int sortOrder;

    /**
     * 拦截提示信息
     */
    private final String blockMessage;

    public RegexGuardrail(String guardrailName, Set<HookPoint> hookPoints,
                          Pattern pattern, int sortOrder, String blockMessage) {
        this.guardrailName = guardrailName;
        this.guardrailHookPoints = hookPoints;
        this.pattern = pattern;
        this.sortOrder = sortOrder;
        this.blockMessage = blockMessage;
    }

    @Override
    public Set<HookPoint> hookPoints() {
        return guardrailHookPoints;
    }

    @Override
    public String name() {
        return guardrailName;
    }

    @Override
    public GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context) {
        if (content == null || content.isBlank() || pattern == null) {
            return GuardrailResult.passed();
        }

        if (pattern.matcher(content).find()) {
            String reason = blockMessage != null && !blockMessage.isBlank()
                    ? blockMessage : "内容匹配到限制模式";
            log.warn("正则护栏拦截: name={}, pattern={}", guardrailName, pattern.pattern());
            return GuardrailResult.blocked(reason, guardrailName, hookPoint);
        }

        return GuardrailResult.passed();
    }

    @Override
    public int order() {
        return sortOrder;
    }
}
