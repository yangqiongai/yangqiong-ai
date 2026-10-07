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

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Agent内容块标记接口
 * <p>
 * 声明多态类型信息，支持内容块序列化到JSON后按类型还原。
 * </p>
 * @author yangqiong
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AgentTextBlock.class, name = "textBlock"),
        @JsonSubTypes.Type(value = AgentThinkingBlock.class, name = "thinkingBlock"),
        @JsonSubTypes.Type(value = AgentImageBlock.class, name = "imageBlock"),
        @JsonSubTypes.Type(value = AgentToolUseBlock.class, name = "toolUseBlock"),
        @JsonSubTypes.Type(value = AgentToolResultBlock.class, name = "toolResultBlock")
})
public interface AgentContentBlock {
}
