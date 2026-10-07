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
package com.yangqiongai.ai.memory.preference;

import com.yangqiongai.ai.agent.core.provider.DeclaredPreference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 用户偏好口令解析器
 * <p>
 * 从用户输入文本中识别显式偏好声明口令，支持两种语法：
 * 1. 自然语言口令：以"记住/以后用/请默认/帮我记住"等开头的句子，content为口令后的文本
 * 2. 结构化口令："设置偏好 key=xxx value=yyy" 或 "偏好: key -> value"
 * </p>
 * @author yangqiong
 */
@Component
public class PreferenceCommandParser {

    private static final Logger log = LoggerFactory.getLogger(PreferenceCommandParser.class);

    private static final String CHANNEL_COMMAND = "COMMAND";

    /**
     * 自然语言口令前缀（按优先级排序，长前缀优先匹配避免误识别）
     */
    private static final Pattern[] COMMAND_PREFIXES = {
            Pattern.compile("^请记住[：:]?\\s*(.+)"),
            Pattern.compile("^帮我记住[：:]?\\s*(.+)"),
            Pattern.compile("^记住[：:]?\\s*(.+)"),
            Pattern.compile("^以后[请要用默认]+[：:]?\\s*(.+)"),
            Pattern.compile("^请默认[：:]?\\s*(.+)"),
            Pattern.compile("^默认[：:]?\\s*(.+)"),
            Pattern.compile("^请以后[：:]?\\s*(.+)")
    };

    /**
     * 结构化口令：设置偏好 key=xxx value=yyy
     */
    private static final Pattern STRUCTURED_KV_PATTERN =
            Pattern.compile("(?:设置偏好|偏好设置|preference)[：:]?\\s*key=([^,\\s]+)\\s*[，,]?\\s*value=(.+)");

    /**
     * 结构化口令：偏好: key -> value
     */
    private static final Pattern STRUCTURED_ARROW_PATTERN =
            Pattern.compile("(?:偏好|preference)[：:]?\\s*([^->]+?)\\s*->\\s*(.+)");

    private static final int MIN_CONTENT_LENGTH = 2;

    private static final int MAX_CONTENT_LENGTH = 500;

    /**
     * 解析文本中的偏好口令
     * @param text
     * @return
     */
    public List<DeclaredPreference> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<DeclaredPreference> result = new ArrayList<>();
        String trimmed = text.trim();

        // 优先匹配结构化口令（带key）
        DeclaredPreference structured = parseStructured(trimmed);
        if (structured != null) {
            result.add(structured);
            return result;
        }

        // 匹配自然语言口令
        DeclaredPreference natural = parseNaturalLanguage(trimmed);
        if (natural != null) {
            result.add(natural);
        }
        return result;
    }

    /**
     * 解析结构化口令
     * @param text
     * @return
     */
    private DeclaredPreference parseStructured(String text) {
        Matcher kvMatcher = STRUCTURED_KV_PATTERN.matcher(text);
        if (kvMatcher.find()) {
            String key = kvMatcher.group(1).trim();
            String content = kvMatcher.group(2).trim();
            if (isValidContent(content)) {
                return new DeclaredPreference(key, content, CHANNEL_COMMAND);
            }
        }
        Matcher arrowMatcher = STRUCTURED_ARROW_PATTERN.matcher(text);
        if (arrowMatcher.find()) {
            String key = arrowMatcher.group(1).trim();
            String content = arrowMatcher.group(2).trim();
            if (isValidContent(content)) {
                return new DeclaredPreference(key, content, CHANNEL_COMMAND);
            }
        }
        return null;
    }

    /**
     * 解析自然语言口令
     * @param text
     * @return
     */
    private DeclaredPreference parseNaturalLanguage(String text) {
        for (Pattern pattern : COMMAND_PREFIXES) {
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                String content = matcher.group(1).trim();
                if (isValidContent(content)) {
                    log.debug("识别到偏好口令: pattern={}, content={}", pattern.pattern(),
                            content.length() > 50 ? content.substring(0, 50) + "..." : content);
                    return new DeclaredPreference(null, content, CHANNEL_COMMAND);
                }
            }
        }
        return null;
    }

    /**
     * 校验内容长度
     * @param content
     * @return
     */
    private boolean isValidContent(String content) {
        return content != null
                && content.length() >= MIN_CONTENT_LENGTH
                && content.length() <= MAX_CONTENT_LENGTH;
    }
}
