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

import java.util.List;

/**
 * 连接器提供商描述
 * <p>
 * 提供商静态能力目录（名称/分类/凭证字段/配置字段/工具清单），
 * 由Provider代码内置声明，管理面据此渲染配置表单与工具列表。
 * </p>
 * @author yangqiong
 */
public class ConnectorDescriptor {

    /**
     * 提供商展示名
     */
    private final String displayName;

    /**
     * 分类（im/database/doc等）
     */
    private final String category;

    /**
     * 图标标识
     */
    private final String icon;

    /**
     * 功能描述
     */
    private final String description;

    /**
     * 凭证字段Schema
     */
    private final List<ConnectorField> credentialFields;

    /**
     * 配置字段Schema
     */
    private final List<ConnectorField> configFields;

    /**
     * 工具清单声明
     */
    private final List<ConnectorToolDefinition> tools;

    /**
     * 是否支持入站消息
     */
    private final boolean inboundSupported;

    private ConnectorDescriptor(Builder builder) {
        this.displayName = builder.displayName;
        this.category = builder.category;
        this.icon = builder.icon;
        this.description = builder.description;
        this.credentialFields = List.copyOf(builder.credentialFields);
        this.configFields = List.copyOf(builder.configFields);
        this.tools = List.copyOf(builder.tools);
        this.inboundSupported = builder.inboundSupported;
    }

    public static Builder of(String displayName, String category) {
        return new Builder(displayName, category);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getCategory() {
        return category;
    }

    public String getIcon() {
        return icon;
    }

    public String getDescription() {
        return description;
    }

    public List<ConnectorField> getCredentialFields() {
        return credentialFields;
    }

    public List<ConnectorField> getConfigFields() {
        return configFields;
    }

    public List<ConnectorToolDefinition> getTools() {
        return tools;
    }

    public boolean isInboundSupported() {
        return inboundSupported;
    }

    /**
     * 连接器提供商描述构建器
     * @author yangqiong
     */
    public static class Builder {

        private final String displayName;

        private final String category;

        private String icon;

        private String description;

        private final java.util.List<ConnectorField> credentialFields = new java.util.ArrayList<>();

        private final java.util.List<ConnectorField> configFields = new java.util.ArrayList<>();

        private final java.util.List<ConnectorToolDefinition> tools = new java.util.ArrayList<>();

        private boolean inboundSupported;

        private Builder(String displayName, String category) {
            this.displayName = displayName;
            this.category = category;
        }

        public Builder icon(String icon) {
            this.icon = icon;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder credentialField(ConnectorField field) {
            this.credentialFields.add(field);
            return this;
        }

        public Builder configField(ConnectorField field) {
            this.configFields.add(field);
            return this;
        }

        public Builder tool(ConnectorToolDefinition tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder inboundSupported() {
            this.inboundSupported = true;
            return this;
        }

        public ConnectorDescriptor build() {
            return new ConnectorDescriptor(this);
        }
    }
}
