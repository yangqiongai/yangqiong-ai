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
package com.yangqiongai.ai.agent.core;

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.agent.runtime.message.AgentChatUsage;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;

import java.util.List;

/**
 * Agent结果处理
 * @author yangqiong
 */
@FunctionalInterface
public interface AgentResultConverter {

    /**
     * 结果转换
     * @param context
     * @param answer 多模态答案内容（已剥离 ThinkingBlock，已过 guardrails）
     * @param chatUsage
     * @param msg
     * @return
     */
    AgentResult converter(AgentContext context, List<AgentContentBlock> answer, AgentChatUsage chatUsage, AgentMessage msg);
}
