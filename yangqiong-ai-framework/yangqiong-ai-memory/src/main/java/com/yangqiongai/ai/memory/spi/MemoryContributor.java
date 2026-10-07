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
package com.yangqiongai.ai.memory.spi;

import com.yangqiongai.ai.memory.agentmemory.model.AgentMemoryInjection;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunOutcome;
import com.yangqiongai.ai.memory.agentmemory.model.MemoryRunQuery;

/**
 * Agent运行记忆进出贡献者
 * <p>
 * 仅补齐Agent执行链路的记忆进出（无头运行注入/任务结束产出），不参与用户对话记忆链路。
 * 平台侧经AgentMiddleware桥接进引擎调用链，T19无头触发器运行也经此接入。
 * </p>
 * @author yangqiong
 */
public interface MemoryContributor {

    /**
     * 运行前收集注入记忆，返回按Token预算截断后的注入块
     * @param query
     * @return
     */
    AgentMemoryInjection beforeRun(MemoryRunQuery query);

    /**
     * 运行后产出记忆候选（任务级抽取，写入侧含安全网关与去重）
     * @param outcome
     */
    void afterRun(MemoryRunOutcome outcome);
}
