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
package com.yangqiongai.ai.agent.runtime.config;

import java.util.Objects;

/**
 * Agent响应格式
 * @author yangqiong
 */
public final class AgentResponseFormat {

    /**
     * 格式类型
     */
    private final String type;

    /**
     * JSON Schema定义
     */
    private final AgentJsonSchema jsonSchema;

    public AgentResponseFormat(String type, AgentJsonSchema jsonSchema) {
        this.type = type;
        this.jsonSchema = jsonSchema;
    }

    /**
     * 创建JSON Schema响应格式
     * @param schema
     * @return
     */
    public static AgentResponseFormat jsonSchema(AgentJsonSchema schema) {
        return new AgentResponseFormat("json_schema", schema);
    }

    /**
     * 获取格式类型
     * @return
     */
    public String getType() {
        return type;
    }

    /**
     * 获取JSON Schema定义
     * @return
     */
    public AgentJsonSchema getJsonSchema() {
        return jsonSchema;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentResponseFormat that = (AgentResponseFormat) o;
        return Objects.equals(type, that.type) && Objects.equals(jsonSchema, that.jsonSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, jsonSchema);
    }

    @Override
    public String toString() {
        return "AgentResponseFormat{type='" + type + "', jsonSchema=" + jsonSchema + "}";
    }
}
