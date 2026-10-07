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
 * 连接器凭证/配置字段定义
 * <p>
 * 描述提供商所需的凭证字段与配置字段Schema，管理面据此动态渲染配置表单。
 * </p>
 * @author yangqiong
 */
public class ConnectorField {

    /**
     * 字段名
     */
    private final String name;

    /**
     * 字段展示名
     */
    private final String label;

    /**
     * 是否必填
     */
    private final boolean required;

    /**
     * 是否敏感字段（接口返回时脱敏）
     */
    private final boolean secret;

    /**
     * 占位提示
     */
    private final String placeholder;

    private ConnectorField(Builder builder) {
        this.name = builder.name;
        this.label = builder.label;
        this.required = builder.required;
        this.secret = builder.secret;
        this.placeholder = builder.placeholder;
    }

    public static Builder of(String name, String label) {
        return new Builder(name, label);
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public boolean isRequired() {
        return required;
    }

    public boolean isSecret() {
        return secret;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    /**
     * 连接器字段定义构建器
     * @author yangqiong
     */
    public static class Builder {

        private final String name;

        private final String label;

        private boolean required;

        private boolean secret;

        private String placeholder;

        private Builder(String name, String label) {
            this.name = name;
            this.label = label;
        }

        public Builder required() {
            this.required = true;
            return this;
        }

        public Builder secret() {
            this.secret = true;
            return this;
        }

        public Builder placeholder(String placeholder) {
            this.placeholder = placeholder;
            return this;
        }

        public ConnectorField build() {
            return new ConnectorField(this);
        }
    }
}
