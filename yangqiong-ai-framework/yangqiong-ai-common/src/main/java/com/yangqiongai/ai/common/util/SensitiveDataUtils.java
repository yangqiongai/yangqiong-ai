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
package com.yangqiongai.ai.common.util;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具类
 * <p>
 * 提供手机号、身份证号、邮箱、密钥等常见敏感信息的脱敏处理。
 * 用于步骤记录、日志输出等场景，防止敏感信息泄露到审计表或日志中。
 * </p>
 * @author yangqiong
 */
public class SensitiveDataUtils {

    /**
     * 手机号脱敏正则
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");

    /**
     * 身份证号脱敏正则
     */
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{6})\\d{8}(\\d{4}[Xx\\d])");

    /**
     * 邮箱脱敏正则
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(\\w{1,3})\\w+(@\\w+\\.\\w+)");

    /**
     * 密钥脱敏正则（匹配 password/secret/token/apikey 后的值）
     */
    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(password\\s*[=:]\\s*)\\S+|(secret\\s*[=:]\\s*)\\S+|(token\\s*[=:]\\s*)\\S+|(apikey\\s*[=:]\\s*)\\S+",
            Pattern.CASE_INSENSITIVE);

    private SensitiveDataUtils() {
    }

    /**
     * 脱敏处理：替换手机号、身份证、邮箱、密钥等敏感信息
     * @param text 原始文本
     * @return 脱敏后的文本，null入参返回null
     */
    public static String desensitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String result = text;
        // 手机号脱敏：13812345678 → 138****5678
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        // 身份证脱敏：110101199001011234 → 110101********1234
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1********$2");
        // 邮箱脱敏：zhangsan@gmail.com → zha***@gmail.com
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***$2");
        // 密钥脱敏：password=abc123 → password=***
        result = SECRET_PATTERN.matcher(result).replaceAll("$1$2$3$4***");
        return result;
    }
}