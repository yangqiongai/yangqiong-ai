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
package com.yangqiongai.ai.agent.runtime.config;

/**
 * 编排策略
 * <p>
 * 子代理编排的默认策略，LLM未显式指定时生效，并自动转译为编排提示词指令。
 * </p>
 * @author yangqiong
 */
public enum OrchestrationStrategy {

    /**
     * 顺序执行：子代理按声明顺序逐个执行，前序结果作为后序上下文
     */
    SEQUENTIAL,

    /**
     * 并行执行：子代理并发执行，结果汇聚后统一归纳
     */
    PARALLEL,

    /**
     * 自适应：由编排器依据任务特征在顺序与并行间自动选择
     */
    ADAPTIVE,

    /**
     * 辩论式：多个子代理独立给出结论后交叉质证，由主代理裁决
     */
    DEBATE,

    /**
     * 反思式：子代理执行后自我批判并修正，迭代至收敛
     */
    REFLECTION,

    /**
     * 群聊式：子代理以群聊形式协同讨论，由主持人代理汇总
     */
    GROUP_CHAT
}
