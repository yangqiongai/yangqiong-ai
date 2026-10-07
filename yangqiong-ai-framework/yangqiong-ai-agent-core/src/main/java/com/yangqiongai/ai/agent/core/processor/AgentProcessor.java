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

import com.yangqiongai.ai.agent.core.context.AgentContext;
import com.yangqiongai.ai.agent.core.model.request.AgentRequest;
import com.yangqiongai.ai.agent.core.model.result.AgentResult;
import com.yangqiongai.ai.common.sse.StreamEvent;
import reactor.core.publisher.Flux;

/**
 * 任务处理器
 * @author yangqiong
 */
public interface AgentProcessor {

    /**
     * 默认
     */
    String DEFAULT_AGENT = "default";

    /**
     * 直接大模型调用（不走ReAct循环）
     */
    String DIRECT_LLM = "directLlm";

    /**
     * 本地模式（启用Harness全部本地能力）
     */
    String LOCAL_AGENT = "localAgent";

    /**
     * 获取Agent编码
     * @return
     */
    String getAgentCode();

    /**
     * 构建Agent上下文
     * @param request
     * @return
     */
    AgentContext createAgentContext(AgentRequest request);

    /**
     * 同步执行任务
     * @param context
     * @return
     */
    AgentResult process(AgentContext context);

    /**
     * 流式执行任务，返回结构化事件流
     * @param context
     * @return
     */
    Flux<StreamEvent> stream(AgentContext context);
}
