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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * PII泄露检测护栏
 *
 * @author yangqiong
 */
@Component
public class PiiGuardrail implements Guardrail {

    private static final Logger log = LoggerFactory.getLogger(PiiGuardrail.class);

    /**
     * PII泄露检测正则列表
     */
    private static final List<PiiPattern> PII_PATTERNS = List.of(
            new PiiPattern("手机号", Pattern.compile("1[3-9]\\d{9}")),
            new PiiPattern("身份证号", Pattern.compile("\\d{17}[\\dXx]")),
            new PiiPattern("银行卡号", Pattern.compile("\\d{16,19}")),
            new PiiPattern("邮箱", Pattern.compile("[\\w.-]+@[\\w.-]+\\.\\w+"))
    );

    @Override
    public Set<HookPoint> hookPoints() {
        return Set.of(HookPoint.OUTPUT);
    }

    @Override
    public String name() {
        return "builtin-pii-detection";
    }

    @Override
    public GuardrailResult check(HookPoint hookPoint, String content, GuardrailContext context) {
        if (content == null || content.isBlank()) {
            return GuardrailResult.passed();
        }

        // PII泄露检测
        Map<String, Integer> detectedPii = new LinkedHashMap<>();
        for (PiiPattern piiPattern : PII_PATTERNS) {
            if (piiPattern.pattern.matcher(content).find()) {
                detectedPii.merge(piiPattern.name, 1, Integer::sum);
            }
        }

        if (!detectedPii.isEmpty()) {
            String piiTypes = String.join("、", detectedPii.keySet());
            log.warn("PII护栏拦截: 检测到PII泄露, types={}", piiTypes);
            return GuardrailResult.blocked(
                    "输出包含疑似个人隐私信息（" + piiTypes + "），请脱敏后重试",
                    name(), hookPoint);
        }

        return GuardrailResult.passed();
    }

    @Override
    public int order() {
        return 10;
    }

    /**
     * PII检测模式
     */
    private static class PiiPattern {

        /**
         * 模式名称
         */
        final String name;

        /**
         * 正则模式
         */
        final Pattern pattern;

        PiiPattern(String name, Pattern pattern) {
            this.name = name;
            this.pattern = pattern;
        }
    }
}
