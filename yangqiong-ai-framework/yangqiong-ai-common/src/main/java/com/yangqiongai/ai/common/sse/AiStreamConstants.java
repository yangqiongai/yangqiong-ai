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
package com.yangqiongai.ai.common.sse;

/**
 * AI平台流式常量
 * @author yangqiong
 */
public final class AiStreamConstants {

    private AiStreamConstants() {
    }

    /**
     * SSE事件名：完成
     */
    public static final String EVENT_DONE = "done";

    /**
     * SSE事件名：错误
     */
    public static final String EVENT_ERROR = "error";

    /**
     * SSE事件名：思考
     */
    public static final String EVENT_THINKING = "thinking";

    /**
     * SSE事件名：工具调用
     */
    public static final String EVENT_TOOL_CALL = "tool_call";

    /**
     * SSE事件名：工具结果
     */
    public static final String EVENT_TOOL_RESULT = "tool_result";

    /**
     * SSE事件名：审批请求
     */
    public static final String EVENT_APPROVAL_REQUIRED = "approval_required";

    /**
     * SSE事件名：用户澄清请求
     */
    public static final String EVENT_CLARIFICATION_REQUIRED = "clarification_required";

    /**
     * SSE事件名：引擎工具确认请求
     */
    public static final String EVENT_CONFIRM_REQUIRED = "confirm_required";

    /**
     * SSE事件名：预算/配额告警
     */
    public static final String EVENT_BUDGET_WARNING = "budget_warning";

    /**
     * SSE完成标记
     */
    public static final String DONE_MARKER = "[DONE]";

    /**
     * thinking标签开始
     */
    public static final String THINK_START = "<think>";

    /**
     * thinking标签结束
     */
    public static final String THINK_END = "</think>";
}
