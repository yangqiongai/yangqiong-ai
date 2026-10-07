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
package com.yangqiongai.ai.agent.runtime.model.protocol;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import com.yangqiongai.ai.agent.runtime.model.AgentChatResponse;
import com.yangqiongai.ai.agent.runtime.model.AgentGenerateOptions;

/**
 * 模型协议适配器
 * <p>
 * 隔离不同provider的协议差异，每个provider实现一套适配器。
 * </p>
 * @author yangqiong
 */
public interface AgentModelProtocolAdapter {

    /**
     * 构建请求体（包含model、messages、tools、options、stream等）
     * @param modelName
     * @param messages
     * @param tools
     * @param options
     * @param defaultOptions
     * @param stream
     * @return
     */
    String buildRequestBody(String modelName, List<AgentMessage> messages, List<Map<String, Object>> tools,
                              AgentGenerateOptions options, AgentGenerateOptions defaultOptions, boolean stream);

    /**
     * 解析非流式响应
     * @param root
     * @return
     */
    AgentChatResponse parseNonStreamResponse(JsonNode root);

    /**
     * 解析流式响应行（SSE格式或每行JSON）
     * @param line
     * @return
     */
    AgentChatResponse parseStreamLine(String line);

    /**
     * 获取请求端点路径（如"/chat/completions"、"/api/chat"、"/v1/messages"）
     * @return
     */
    String getEndpointPath();

    /**
     * 获取请求头（provider特有，如Anthropic的anthropic-version头）
     * @param apiKey
     * @return
     */
    Map<String, String> getHeaders(String apiKey);

    /**
     * 判断是否需要Authorization Bearer头
     * @return
     */
    boolean useBearerAuth();
}
