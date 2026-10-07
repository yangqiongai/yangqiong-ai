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
package com.yangqiongai.ai.agent.runtime.durable;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.yangqiongai.ai.agent.runtime.message.AgentToolResultBlock;

/**
 * 内存工具执行记录存储
 * <p>
 * 单进程兜底实现：幂等键到已完成结果的索引，超限淘汰既有条目防内存无界增长。
 * </p>
 * @author yangqiong
 */
public class InMemoryToolExecutionStore implements ToolExecutionStore {

    /**
     * 最大幂等条目数，超限淘汰既有条目防内存无界增长（默认场景零增长，仅业务工具opt-in后生效）
     */
    private static final int MAX_ENTRIES = 10000;

    /**
     * 幂等键到已完成结果的索引
     */
    private final Map<String, AgentToolResultBlock> completedByKey = new ConcurrentHashMap<>();

    @Override
    public void record(String idempotencyKey, AgentToolResultBlock result) {
        if (idempotencyKey != null && result != null) {
            if (completedByKey.size() >= MAX_ENTRIES && !completedByKey.containsKey(idempotencyKey)) {
                Iterator<String> it = completedByKey.keySet().iterator();
                if (it.hasNext()) {
                    it.next();
                    it.remove();
                }
            }
            completedByKey.putIfAbsent(idempotencyKey, result);
        }
    }

    @Override
    public boolean isCompleted(String idempotencyKey) {
        return idempotencyKey != null && completedByKey.containsKey(idempotencyKey);
    }

    @Override
    public AgentToolResultBlock getResult(String idempotencyKey) {
        return idempotencyKey != null ? completedByKey.get(idempotencyKey) : null;
    }
}
