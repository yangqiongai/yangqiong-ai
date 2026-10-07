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
package com.yangqiongai.ai.agent.runtime.tool;

import java.util.Map;

/**
 * Agent工具规格定义
 * @author yangqiong
 */
public final class AgentToolSpec {

    /**
     * 工具名称
     */
    private final String name;

    /**
     * 工具描述
     */
    private final String description;

    /**
     * 工具参数定义
     */
    private final Map<String, Object> parameters;

    private AgentToolSpec(String name, String description, Map<String, Object> parameters) {
        this.name = name;
        this.description = description;
        this.parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取工具名称
     * @return
     */
    public String getName() {
        return name;
    }

    /**
     * 获取工具描述
     * @return
     */
    public String getDescription() {
        return description;
    }

    /**
     * 获取工具参数定义
     * @return
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    /**
     * 工具规格构建器
     * @author yangqiong
     */
    public static class Builder {

        /**
         * 工具名称
         */
        private String name;

        /**
         * 工具描述
         */
        private String description;

        /**
         * 工具参数定义
         */
        private Map<String, Object> parameters;

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = parameters;
            return this;
        }

        public AgentToolSpec build() {
            return new AgentToolSpec(name, description, parameters);
        }
    }
}
