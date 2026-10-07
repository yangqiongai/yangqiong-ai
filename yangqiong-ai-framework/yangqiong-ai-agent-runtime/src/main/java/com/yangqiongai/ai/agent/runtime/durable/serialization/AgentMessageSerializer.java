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
package com.yangqiongai.ai.agent.runtime.durable.serialization;

import java.util.List;

import com.fasterxml.jackson.core.type.TypeReference;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

/**
 * 消息序列化器
 * <p>
 * AgentMessage与JSON互转，供检查点/会话存储等共享存储落库复用。
 * </p>
 * @author yangqiong
 */
public final class AgentMessageSerializer {

    private AgentMessageSerializer() {
    }

    /**
     * 序列化消息为JSON
     * @param message
     * @return
     */
    public static String toJson(AgentMessage message) {
        try {
            return DurableObjectMapper.get().writeValueAsString(message);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentMessage失败", e);
        }
    }

    /**
     * 反序列化JSON为消息
     * @param json
     * @return
     */
    public static AgentMessage fromJson(String json) {
        try {
            return DurableObjectMapper.get().readValue(json, AgentMessage.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentMessage失败", e);
        }
    }

    /**
     * 序列化消息列表为JSON
     * @param messages
     * @return
     */
    public static String listToJson(List<AgentMessage> messages) {
        try {
            return DurableObjectMapper.get().writeValueAsString(messages);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentMessage列表失败", e);
        }
    }

    /**
     * 反序列化JSON为消息列表
     * @param json
     * @return
     */
    public static List<AgentMessage> listFromJson(String json) {
        try {
            return DurableObjectMapper.get().readValue(json, new TypeReference<List<AgentMessage>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentMessage列表失败", e);
        }
    }
}
