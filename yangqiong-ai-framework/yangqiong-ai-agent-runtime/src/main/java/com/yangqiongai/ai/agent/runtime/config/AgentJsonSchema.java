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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Agent JSON Schema定义
 * @author yangqiong
 */
public final class AgentJsonSchema {

    /**
     * Schema名称
     */
    private final String name;

    /**
     * Schema定义
     */
    private final Map<String, Object> schema;

    /**
     * 是否严格模式
     */
    private final Boolean strict;

    private AgentJsonSchema(String name, Map<String, Object> schema, Boolean strict) {
        this.name = name;
        this.schema = schema != null ? Map.copyOf(schema) : Map.of();
        this.strict = strict;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取Schema名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取Schema定义
     * @return
     */
    public Map<String, Object> getSchema() {
        return schema;
    }

    /**
     * 是否严格模式
     * @return
     */
    public boolean isStrict() {
        return Boolean.TRUE.equals(strict);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentJsonSchema that = (AgentJsonSchema) o;
        return Objects.equals(name, that.name)
                && Objects.equals(schema, that.schema)
                && Objects.equals(strict, that.strict);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, schema, strict);
    }

    @Override
    public String toString() {
        return "AgentJsonSchema{name='" + name + "', strict=" + strict + "}";
    }

    /**
     * JSON Schema构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * Schema名称
         */
        private String name;

        /**
         * Schema定义
         */
        private Map<String, Object> schema = new HashMap<>();

        /**
         * 是否严格模式
         */
        private Boolean strict;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder schema(Map<String, Object> schema) {
            this.schema = schema != null ? new HashMap<>(schema) : new HashMap<>();
            return this;
        }

        public Builder strict(Boolean strict) {
            this.strict = strict;
            return this;
        }

        public AgentJsonSchema build() {
            return new AgentJsonSchema(name, schema, strict);
        }
    }
}
