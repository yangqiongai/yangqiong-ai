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
package com.yangqiongai.ai.agent.runtime.model;

import java.util.List;
import java.util.Objects;

import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;

/**
 * Agent对话响应
 * @author yangqiong
 */
public final class AgentChatResponse {

    /**
     * 内容块列表
     */
    private final List<AgentContentBlock> content;

    /**
     * Token用量统计
     */
    private final AgentChatUsage chatUsage;

    public AgentChatResponse(List<AgentContentBlock> content, AgentChatUsage chatUsage) {
        this.content = content != null ? List.copyOf(content) : List.of();
        this.chatUsage = chatUsage;
    }

    /**
     * 获取内容块列表
     * @return
     */
    public List<AgentContentBlock> getContent() {
        return content;
    }

    /**
     * 获取Token用量统计
     * @return
     */
    public AgentChatUsage getChatUsage() {
        return chatUsage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentChatResponse that = (AgentChatResponse) o;
        return Objects.equals(content, that.content)
                && Objects.equals(chatUsage, that.chatUsage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, chatUsage);
    }

    @Override
    public String toString() {
        return "AgentChatResponse{contentSize=" + (content != null ? content.size() : 0)
                + ", chatUsage=" + chatUsage + "}";
    }
}
