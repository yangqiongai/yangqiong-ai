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
package com.yangqiongai.ai.agent.runtime.event;

/**
 * 需要用户澄清事件
 * @author yangqiong
 */
public class RequireUserClarificationEvent extends AgentEvent {

    /**
     * 澄清问题
     */
    private final String question;

    /**
     * 关联的工具调用ID
     */
    private final String toolCallId;

    /**
     * 候选选项列表（可空，前端渲染为可点击选择按钮）
     */
    private final java.util.List<String> options;

    public RequireUserClarificationEvent(String question, String toolCallId) {
        this(question, toolCallId, null);
    }

    public RequireUserClarificationEvent(String question, String toolCallId, java.util.List<String> options) {
        super(AgentEventType.REQUIRE_USER_CLARIFICATION, question);
        this.question = question;
        this.toolCallId = toolCallId;
        this.options = options;
    }

    /**
     * 获取澄清问题
     * @return
     */
    public String getQuestion() {
        return question;
    }

    /**
     * 获取关联的工具调用ID
     * @return
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 获取候选选项列表
     * @return
     */
    public java.util.List<String> getOptions() {
        return options;
    }
}
