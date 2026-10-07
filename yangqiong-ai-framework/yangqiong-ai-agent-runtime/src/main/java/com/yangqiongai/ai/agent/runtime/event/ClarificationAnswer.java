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
 * 用户澄清应答
 * <p>
 * resumeWithClarification恢复执行的入参，与引擎侧ClarificationAnswer字段对齐。
 * </p>
 * @author yangqiong
 */
public class ClarificationAnswer {

    /**
     * 关联的工具调用ID
     */
    private final String toolCallId;

    /**
     * 澄清应答内容
     */
    private final String answer;

    public ClarificationAnswer(String toolCallId, String answer) {
        this.toolCallId = toolCallId;
        this.answer = answer;
    }

    /**
     * 获取关联的工具调用ID
     * @return
     */
    public String getToolCallId() {
        return toolCallId;
    }

    /**
     * 获取澄清应答内容
     * @return
     */
    public String getAnswer() {
        return answer;
    }
}
