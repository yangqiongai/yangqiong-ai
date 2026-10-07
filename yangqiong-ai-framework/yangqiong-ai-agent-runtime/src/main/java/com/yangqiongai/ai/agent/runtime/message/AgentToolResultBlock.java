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

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent工具结果块
 * @author yangqiong
 */
public final class AgentToolResultBlock implements AgentContentBlock {

    /**
     * 工具调用ID
     */
    private final String toolUseId;

    /**
     * 结果内容块列表
     */
    private final List<AgentContentBlock> content;

    /**
     * 是否为错误结果
     */
    private final boolean error;

    @JsonCreator
    private AgentToolResultBlock(@JsonProperty("toolUseId") String toolUseId,
                                 @JsonProperty("content") List<AgentContentBlock> content,
                                 @JsonProperty("error") boolean error) {
        this.toolUseId = toolUseId;
        this.content = content != null ? List.copyOf(content) : List.of();
        this.error = error;
    }

    /**
     * 创建正常工具结果
     * @param content
     * @return
     */
    public static AgentToolResultBlock of(List<AgentContentBlock> content) {
        return new AgentToolResultBlock(null, content, false);
    }

    /**
     * 创建带工具调用ID的正常结果
     * @param toolUseId
     * @param content
     * @return
     */
    public static AgentToolResultBlock of(String toolUseId, List<AgentContentBlock> content) {
        return new AgentToolResultBlock(toolUseId, content, false);
    }

    /**
     * 创建错误工具结果
     * @param errorMessage
     * @return
     */
    public static AgentToolResultBlock error(String errorMessage) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(errorMessage).build();
        return new AgentToolResultBlock(null, List.of(textBlock), true);
    }

    /**
     * 创建带工具调用ID的错误结果
     * @param toolUseId
     * @param errorMessage
     * @return
     */
    public static AgentToolResultBlock error(String toolUseId, String errorMessage) {
        AgentTextBlock textBlock = AgentTextBlock.builder().text(errorMessage).build();
        return new AgentToolResultBlock(toolUseId, List.of(textBlock), true);
    }

    /**
     * 返回带指定工具调用ID的副本，用于补全缺失的toolUseId
     * @param toolUseId
     * @return
     */
    public AgentToolResultBlock withToolUseId(String toolUseId) {
        if (Objects.equals(this.toolUseId, toolUseId)) {
            return this;
        }
        return new AgentToolResultBlock(toolUseId, this.content, this.error);
    }

    /**
     * 获取工具调用ID
     * @return
     */
    public String getToolUseId() {
        return toolUseId;
    }

    /**
     * 获取结果内容
     * @return
     */
    public List<AgentContentBlock> getContent() {
        return content;
    }

    /**
     * 是否为错误结果
     * @return
     */
    public boolean isError() {
        return error;
    }

    /**
     * 获取结果文本内容
     * @return
     */
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
        AgentToolResultBlock that = (AgentToolResultBlock) o;
        return error == that.error
                && Objects.equals(toolUseId, that.toolUseId)
                && Objects.equals(content, that.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(toolUseId, content, error);
    }

    @Override
    public String toString() {
        return "AgentToolResultBlock{toolUseId='" + toolUseId + "', error=" + error + "}";
    }
}
