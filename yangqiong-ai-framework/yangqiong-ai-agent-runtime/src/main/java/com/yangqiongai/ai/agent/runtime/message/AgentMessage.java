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
package com.yangqiongai.ai.agent.runtime.message;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Agent消息
 * @author yangqiong
 */
@JsonDeserialize(builder = AgentMessage.Builder.class)
public final class AgentMessage {

    /**
     * 消息名称
     */
    private final String name;

    /**
     * 消息角色
     */
    private final AgentMessageRole role;

    /**
     * 内容块列表
     */
    private final List<AgentContentBlock> content;

    /**
     * Token用量统计
     */
    private final AgentChatUsage chatUsage;

    /**
     * 耗时（毫秒），用于LLM调用延迟记录
     */
    private final long latency;

    private AgentMessage(String name, AgentMessageRole role, List<AgentContentBlock> content, AgentChatUsage chatUsage, long latency) {
        this.name = name;
        this.role = role;
        this.content = content != null ? List.copyOf(content) : List.of();
        this.chatUsage = chatUsage;
        this.latency = latency;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取消息名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取消息角色
     * @return
     */
    public AgentMessageRole getRole() {
        return role;
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

    /**
     * 获取耗时（毫秒）
     * @return
     */
    public long getLatency() {
        return latency;
    }

    /**
     * 获取消息文本内容
     * @return
     */
    @JsonIgnore
    public String getTextContent() {
        if (content == null || content.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AgentContentBlock block : content) {
            if (block instanceof AgentTextBlock textBlock) {
                sb.append(textBlock.getText());
            }
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentMessage that = (AgentMessage) o;
        return Objects.equals(name, that.name)
                && role == that.role
                && Objects.equals(content, that.content)
                && Objects.equals(chatUsage, that.chatUsage)
                && latency == that.latency;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, role, content, chatUsage, latency);
    }

    @Override
    public String toString() {
        return "AgentMessage{name='" + name + "', role=" + role + ", contentSize=" + (content != null ? content.size() : 0) + "}";
    }

    /**
     * 消息构建器
     * @author yangqiong
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        /**
         * 消息名称
         */
        private String name;

        /**
         * 消息角色
         */
        private AgentMessageRole role;

        /**
         * 内容块列表
         */
        private List<AgentContentBlock> content = new ArrayList<>();

        /**
         * Token用量统计
         */
        private AgentChatUsage chatUsage;

        /**
         * 耗时（毫秒）
         */
        private long latency;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder role(AgentMessageRole role) {
            this.role = role;
            return this;
        }

        public Builder content(List<AgentContentBlock> content) {
            this.content = content != null ? new ArrayList<>(content) : new ArrayList<>();
            return this;
        }

        public Builder chatUsage(AgentChatUsage chatUsage) {
            this.chatUsage = chatUsage;
            return this;
        }

        public Builder latency(long latency) {
            this.latency = latency;
            return this;
        }

        public AgentMessage build() {
            return new AgentMessage(name, role, content, chatUsage, latency);
        }
    }
}
