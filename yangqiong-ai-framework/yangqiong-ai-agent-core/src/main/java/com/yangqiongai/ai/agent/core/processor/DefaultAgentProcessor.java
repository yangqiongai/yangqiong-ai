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
package com.yangqiongai.ai.agent.core.processor;

import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.content.ContentBlockConverter;
import com.yangqiongai.ai.agent.core.model.content.InputBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentContentBlock;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.message.AgentMessageRole;
import com.yangqiongai.ai.agent.runtime.message.AgentTextBlock;

import java.util.ArrayList;
import java.util.List;

/**
 * AgentProcessor的默认通用实现
 * <p>
 * 提供最基础的对话Agent能力，适用于简单的问答场景。
 * 复杂场景请继承 {@link AbstractAgentProcessor} 并覆盖相应钩子方法。
 * </p>
 *
 * @author yangqiong
 */
public class DefaultAgentProcessor extends AbstractAgentProcessor {

    @Override
    public String getAgentCode() {
        return DEFAULT_AGENT;
    }

    @Override
    protected String getAgentName() {
        return "默认对话";
    }

    @Override
    protected int getMaxIterations() {
        return 10;
    }

    @Override
    protected List<AgentMessage> buildInputMessages(AgentRequest request) {
        List<InputBlock> inputBlocks = request == null ? List.of() : request.getInput();
        if ((inputBlocks == null || inputBlocks.isEmpty())
                && request != null && !request.getSeedMessages().isEmpty()) {
            // 种子模式下输入可空：上下文由种子消息承载，避免追加空user消息干扰模型
            return List.of();
        }
        List<AgentContentBlock> userInput;
        if (inputBlocks == null || inputBlocks.isEmpty()) {
            userInput = List.of(AgentTextBlock.builder().text("").build());
        } else {
            userInput = ContentBlockConverter.fromInputBlocks(inputBlocks);
        }
        List<AgentMessage> inputs = new ArrayList<>();
        AgentMessage userMessage = AgentMessage.builder()
                .name("user")
                .role(AgentMessageRole.USER)
                .content(userInput)
                .build();
        inputs.add(userMessage);
        return inputs;
    }
}
