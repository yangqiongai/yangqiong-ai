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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Agent Token用量统计
 * @author yangqiong
 */
public final class AgentChatUsage {

    /**
     * 提示Token数
     */
    private final int promptTokens;

    /**
     * 完成Token数
     */
    private final int completionTokens;

    /**
     * 总Token数
     */
    private final int totalTokens;

    @JsonCreator
    public AgentChatUsage(@JsonProperty("promptTokens") int promptTokens,
                          @JsonProperty("completionTokens") int completionTokens,
                          @JsonProperty("totalTokens") int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    /**
     * 累加两份用量统计，任一为null时返回另一份
     * @param a
     * @param b
     * @return
     */
    public static AgentChatUsage merge(AgentChatUsage a, AgentChatUsage b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return new AgentChatUsage(
                a.promptTokens + b.promptTokens,
                a.completionTokens + b.completionTokens,
                a.totalTokens + b.totalTokens);
    }

    /**
     * 获取提示Token数
     * @return
     */
    public int getPromptTokens() {
        return promptTokens;
    }

    /**
     * 获取完成Token数
     * @return
     */
    public int getCompletionTokens() {
        return completionTokens;
    }

    /**
     * 获取总Token数
     * @return
     */
    public int getTotalTokens() {
        return totalTokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AgentChatUsage that = (AgentChatUsage) o;
        return promptTokens == that.promptTokens
                && completionTokens == that.completionTokens
                && totalTokens == that.totalTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "AgentChatUsage{promptTokens=" + promptTokens
                + ", completionTokens=" + completionTokens
                + ", totalTokens=" + totalTokens + "}";
    }
}
