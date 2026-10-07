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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * 敏感词护栏
 *
 * @author yangqiong
 */
@Component
public class SensitiveWordGuardrail implements Guardrail {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordGuardrail.class);

    /**
     * 敏感词列表（可配置）
     */
    @Value("${ai.security.guardrail.input.sensitive-words:}")
    private List<String> sensitiveWords;

    @Override
    public Set<HookPoint> hookPoints() {
        return Set.of(HookPoint.INPUT);
    }

    @Override
    public String name() {
        return "builtin-sensitive-words";
    }

    @Override
    public GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context) {
        if (content == null || content.isBlank()) {
            return GuardrailResult.passed();
        }

        if (sensitiveWords != null && !sensitiveWords.isEmpty()) {
            String lowerContent = content.toLowerCase();
            for (String word : sensitiveWords) {
                if (word != null && !word.isBlank() && lowerContent.contains(word.toLowerCase())) {
                    log.warn("敏感词护栏拦截: 检测到敏感词, word={}", word);
                    return GuardrailResult.blocked("输入包含敏感内容", name(), hookPoint);
                }
            }
        }

        return GuardrailResult.passed();
    }

    @Override
    public int order() {
        return 20;
    }
}
