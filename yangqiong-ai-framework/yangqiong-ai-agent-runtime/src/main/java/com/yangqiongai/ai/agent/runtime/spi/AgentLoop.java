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
package com.yangqiongai.ai.agent.runtime.spi;

import com.yangqiongai.ai.agent.runtime.AgentRuntimeContext;
import com.yangqiongai.ai.agent.runtime.event.AgentEvent;
import com.yangqiongai.ai.agent.runtime.message.AgentMessage;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 自定义执行循环
 * <p>
 * 替换默认ReAct执行循环以支持plan-and-execute、reflexion等执行范式。
 * </p>
 * @author yangqiong
 */
public interface AgentLoop {

    /**
     * 执行自定义循环
     * @param inputs 输入消息
     * @param context 运行时上下文
     * @return 事件流
     */
    Flux<AgentEvent> run(List<AgentMessage> inputs, AgentRuntimeContext context);
}
