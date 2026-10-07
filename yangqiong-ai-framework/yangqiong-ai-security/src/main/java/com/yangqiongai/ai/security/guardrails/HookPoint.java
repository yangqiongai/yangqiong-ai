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
package com.yangqiongai.ai.security.guardrails;

/**
 * 护栏挂载点枚举
 * <p>
 * 定义护栏在Agent执行链路中的检查时机：
 * <ul>
 *   <li>INPUT — 用户输入进入LLM前</li>
 *   <li>SYSTEM_PROMPT — System Prompt组装完成后</li>
 *   <li>THINKING — LLM推理阶段（reasoning token流）</li>
 *   <li>TOOL_CALL — 工具调用参数提交前</li>
 *   <li>OUTPUT — LLM最终回复输出前</li>
 * </ul>
 * </p>
 *
 * @author yangqiong
 */
public enum HookPoint {

    /**
     * 用户输入进入LLM前
     */
    INPUT,

    /**
     * System Prompt组装完成后
     */
    SYSTEM_PROMPT,

    /**
     * LLM推理阶段（reasoning token流）
     */
    THINKING,

    /**
     * 工具调用参数提交前
     */
    TOOL_CALL,

    /**
     * LLM最终回复输出前
     */
    OUTPUT,

    /**
     * 模型调用消息审查（每次进入LLM的消息，含工具结果与历史上下文）
     */
    MODERATION
}
