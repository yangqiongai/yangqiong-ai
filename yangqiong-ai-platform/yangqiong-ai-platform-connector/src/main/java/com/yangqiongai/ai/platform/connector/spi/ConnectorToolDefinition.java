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
package com.yangqiongai.ai.platform.connector.spi;

/**
 * 连接器工具清单声明
 * <p>
 * 描述提供商暴露的工具（名称/描述/参数Schema），供管理面展示与前端挂载选择，
 * 工具清单由Provider代码声明，不落库。
 * </p>
 * @author yangqiong
 */
public class ConnectorToolDefinition {

    /**
     * 逻辑工具名（不含实例前缀）
     */
    private final String name;

    /**
     * 工具描述
     */
    private final String description;

    /**
     * 参数Schema（JSON Schema格式字符串）
     */
    private final String parametersSchema;

    private ConnectorToolDefinition(Builder builder) {
        this.name = builder.name;
        this.description = builder.description;
        this.parametersSchema = builder.parametersSchema;
    }

    public static Builder of(String name, String description) {
        return new Builder(name, description);
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getParametersSchema() {
        return parametersSchema;
    }

    /**
     * 连接器工具定义构建器
     * @author yangqiong
     */
    public static class Builder {

        private final String name;

        private final String description;

        private String parametersSchema;

        private Builder(String name, String description) {
            this.name = name;
            this.description = description;
        }

        public Builder parametersSchema(String parametersSchema) {
            this.parametersSchema = parametersSchema;
            return this;
        }

        public ConnectorToolDefinition build() {
            return new ConnectorToolDefinition(this);
        }
    }
}
