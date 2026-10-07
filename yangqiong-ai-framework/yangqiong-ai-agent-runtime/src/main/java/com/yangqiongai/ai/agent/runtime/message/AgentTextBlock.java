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
package com.yangqiongai.ai.agent.runtime.message;

import java.util.Objects;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;

/**
 * Agent文本块
 * @author yangqiong
 */
@JsonDeserialize(builder = AgentTextBlock.Builder.class)
public final class AgentTextBlock implements AgentContentBlock {

    /**
     * 文本内容
     */
    private final String text;

    private AgentTextBlock(String text) {
        this.text = text;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取文本内容
     * @return
     */
    public String getText() {
        return text;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentTextBlock that = (AgentTextBlock) o;
        return Objects.equals(text, that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(text);
    }

    @Override
    public String toString() {
        return "AgentTextBlock{text='" + text + "'}";
    }

    /**
     * 文本块构建器
     * @author yangqiong
     */
    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {

        /**
         * 文本内容
         */
        private String text;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public AgentTextBlock build() {
            return new AgentTextBlock(text);
        }
    }
}
