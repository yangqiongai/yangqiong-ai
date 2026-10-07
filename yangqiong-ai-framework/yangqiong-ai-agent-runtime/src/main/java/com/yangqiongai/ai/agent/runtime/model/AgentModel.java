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
package com.yangqiongai.ai.agent.runtime.model;

import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * Agent模型
 * @author yangqiong
 */
public interface AgentModel {

    /**
     * 同步生成模型响应，内部使用默认超时阻塞流式结果
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    AgentChatResponse generate(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options);

    /**
     * 流式生成模型响应
     * @param messages
     * @param tools
     * @param options
     * @return
     */
    Flux<AgentChatResponse> stream(List<AgentMessage> messages, List<Map<String, Object>> tools, AgentGenerateOptions options);
}
