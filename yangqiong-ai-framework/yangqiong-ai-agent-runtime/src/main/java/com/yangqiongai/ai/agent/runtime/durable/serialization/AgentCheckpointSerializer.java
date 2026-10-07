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

import com.yangqiongai.ai.agent.runtime.durable.AgentCheckpoint;

/**
 * 检查点序列化器
 * <p>
 * AgentCheckpoint与JSON互转，供共享检查点存储落库复用。
 * </p>
 * @author yangqiong
 */
public final class AgentCheckpointSerializer {

    private AgentCheckpointSerializer() {
    }

    /**
     * 序列化检查点为JSON
     * @param checkpoint
     * @return
     */
    public static String toJson(AgentCheckpoint checkpoint) {
        try {
            return DurableObjectMapper.get().writeValueAsString(checkpoint);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentCheckpoint失败", e);
        }
    }

    /**
     * 反序列化JSON为检查点
     * @param json
     * @return
     */
    public static AgentCheckpoint fromJson(String json) {
        try {
            return DurableObjectMapper.get().readValue(json, AgentCheckpoint.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentCheckpoint失败", e);
        }
    }
}
