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
package com.yangqiongai.ai.agent.harness;

import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;

import java.util.Collections;
import java.util.List;

/**
 * 消息转换与构建工具
 * @author yangqiong
 */
public final class MessageConverter {

    private MessageConverter() {
    }

    /**
     * 构建系统消息
     * @param systemPrompt
     * @return
     */
    public static AgentMessage toSystemMessage(String systemPrompt) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(systemPrompt).build();
        return AgentMessage.builder()
                .role(AgentMessageRole.SYSTEM)
                .content(Collections.singletonList(textBlock))
                .build();
    }

    /**
     * 构建工具结果消息
     * @param result
     * @return
     */
    public static AgentMessage toToolResultMessage(AgentToolResultBlock result) {
        return AgentMessage.builder()
                .role(AgentMessageRole.TOOL)
                .content(Collections.singletonList(result))
                .build();
    }

    /**
     * 构建助手消息
     * @param content
     * @param usage
     * @return
     */
    public static AgentMessage toAssistantMessage(List<AgentContentBlock> content, AgentChatUsage usage) {
        return AgentMessage.builder()
                .role(AgentMessageRole.ASSISTANT)
                .content(content)
                .chatUsage(usage)
                .build();
    }
}
