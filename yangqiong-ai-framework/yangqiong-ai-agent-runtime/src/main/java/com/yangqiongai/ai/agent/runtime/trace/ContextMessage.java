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
package com.yangqiongai.ai.agent.runtime.trace;

/**
 * 上下文注入消息
 * @author yangqiong
 */
public final class ContextMessage {

    /**
     * 消息角色
     */
    private final String role;

    /**
     * 消息文本内容
     */
    private final String content;

    /**
     * 消息来源（system_prompt/history/tool_result/memory_inject）
     */
    private final String source;

    /**
     * 内容是否被截断
     */
    private final boolean truncated;

    /**
     * 全参构造
     * @param role
     * @param content
     * @param source
     * @param truncated
     */
    public ContextMessage(String role, String content, String source, boolean truncated) {
        this.role = role;
        this.content = content;
        this.source = source;
        this.truncated = truncated;
    }

    public String getRole() {
        return role;
    }

    public String getContent() {
        return content;
    }

    public String getSource() {
        return source;
    }

    public boolean isTruncated() {
        return truncated;
    }
}
