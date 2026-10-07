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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * 持久化统一对象映射器
 * <p>
 * 集中配置Jackson规则，供消息/检查点/内容块等共享存储场景复用，
 * 多态内容块与Builder反序列化行为经注解声明，全局一致。
 * </p>
 * @author yangqiong
 */
public final class DurableObjectMapper {

    /**
     * 全局共享映射器（线程安全，可并发读写）
     */
    private static final ObjectMapper MAPPER = build();

    private DurableObjectMapper() {
    }

    private static ObjectMapper build() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        return mapper;
    }

    /**
     * 获取全局映射器
     * @return
     */
    public static ObjectMapper get() {
        return MAPPER;
    }
}
