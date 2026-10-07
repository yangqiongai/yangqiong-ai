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
package com.yangqiongai.ai.memory.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 配置命名空间兼容测试
 * @author yangqiong
 */
@DisplayName("配置命名空间兼容测试")
class ConfigNamespaceCompatibilityTest {

    @Test
    @DisplayName("ai.memory.* 存在时优先于 ai.conversation.* 旧别名")
    void memoryNamespaceTakesPrecedenceOverConversationAlias() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "ai.memory.long-term.tool-enabled", "true",
                "ai.conversation.long-term.tool-enabled", "false"
        )));

        String resolved = env.resolvePlaceholders(
                "${ai.memory.long-term.tool-enabled:${ai.conversation.long-term.tool-enabled:true}}");

        assertThat(resolved).isEqualTo("true");
    }

    @Test
    @DisplayName("ai.memory.* 不存在时降级到 ai.conversation.* 旧别名")
    void fallbackToConversationAliasWhenMemoryAbsent() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "ai.conversation.long-term.tool-enabled", "false"
        )));

        String resolved = env.resolvePlaceholders(
                "${ai.memory.long-term.tool-enabled:${ai.conversation.long-term.tool-enabled:true}}");

        assertThat(resolved).isEqualTo("false");
    }

    @Test
    @DisplayName("两者都不存在时使用默认值")
    void useDefaultWhenBothAbsent() {
        StandardEnvironment env = new StandardEnvironment();

        String resolved = env.resolvePlaceholders(
                "${ai.memory.long-term.tool-enabled:${ai.conversation.long-term.tool-enabled:true}}");

        assertThat(resolved).isEqualTo("true");
    }

    @Test
    @DisplayName("retrieve-max-tokens 命名空间兼容")
    void retrieveMaxTokensNamespaceCompatibility() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "ai.memory.long-term.retrieve-max-tokens", "800",
                "ai.conversation.long-term.retrieve-max-tokens", "300"
        )));

        String resolved = env.resolvePlaceholders(
                "${ai.memory.long-term.retrieve-max-tokens:${ai.conversation.long-term.retrieve-max-tokens:500}}");

        assertThat(resolved).isEqualTo("800");
    }

    @Test
    @DisplayName("cross-session 命名空间兼容")
    void crossSessionNamespaceCompatibility() {
        StandardEnvironment env = new StandardEnvironment();
        Map<String, Object> props = new HashMap<>();
        props.put("ai.conversation.cross-session.model-code", "gpt-4");
        env.getPropertySources().addFirst(new MapPropertySource("test", props));

        String resolved = env.resolvePlaceholders(
                "${ai.memory.cross-session.model-code:${ai.conversation.cross-session.model-code:defaultAgent}}");

        assertThat(resolved).isEqualTo("gpt-4");
    }

    @Test
    @DisplayName("summary 命名空间兼容")
    void summaryNamespaceCompatibility() {
        StandardEnvironment env = new StandardEnvironment();
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "ai.memory.summary.trigger-count", "20"
        )));

        String resolved = env.resolvePlaceholders(
                "${ai.memory.summary.trigger-count:${ai.conversation.summary.trigger-count:12}}");

        assertThat(resolved).isEqualTo("20");
    }
}
