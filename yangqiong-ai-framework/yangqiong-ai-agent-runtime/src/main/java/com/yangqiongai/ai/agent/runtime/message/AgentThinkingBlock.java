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
 * Agent思考块
 * @author yangqiong
 */
@JsonDeserialize(builder = AgentThinkingBlock.Builder.class)
public final class AgentThinkingBlock implements AgentContentBlock {

    /**
     * 思考内容
     */
    private final String thinking;

    private AgentThinkingBlock(String thinking) {
        this.thinking = thinking;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 获取思考内容
     * @return
     */
    public String getThinking() {
        return thinking;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentThinkingBlock that = (AgentThinkingBlock) o;
        return Objects.equals(thinking, that.thinking);
    }

    @Override
    public int hashCode() {
        return Objects.hash(thinking);
    }

    @Override
    public String toString() {
        return "AgentThinkingBlock{thinking='" + thinking + "'}";
    }

    /**
     * 思考块构建器
 * @author yangqiong
 */
@JsonPOJOBuilder(withPrefix = "")
public static class Builder {

        /**
         * 思考内容
         */
        private String thinking;

        public Builder thinking(String thinking) {
            this.thinking = thinking;
            return this;
        }

        public AgentThinkingBlock build() {
            return new AgentThinkingBlock(thinking);
        }
    }
}
