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

import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;

/**
 * 内容块序列化器
 * <p>
 * AgentContentBlock与JSON互转，多态类型信息由接口注解声明，
 * 供工具执行记录等共享存储落库复用。
 * </p>
 * @author yangqiong
 */
public final class AgentContentBlockSerializer {

    private AgentContentBlockSerializer() {
    }

    /**
     * 序列化内容块为JSON
     * @param block
     * @return
     */
    public static String toJson(AgentContentBlock block) {
        try {
            return DurableObjectMapper.get().writeValueAsString(block);
        } catch (Exception e) {
            throw new IllegalStateException("序列化AgentContentBlock失败", e);
        }
    }

    /**
     * 反序列化JSON为内容块
     * @param json
     * @return
     */
    public static AgentContentBlock fromJson(String json) {
        try {
            return DurableObjectMapper.get().readValue(json, AgentContentBlock.class);
        } catch (Exception e) {
            throw new IllegalStateException("反序列化AgentContentBlock失败", e);
        }
    }
}
